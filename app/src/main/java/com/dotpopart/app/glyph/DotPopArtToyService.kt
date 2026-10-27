package com.dotpopart.app.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.dotpopart.app.DotPopArtApp
import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.data.ButtonAction
import com.dotpopart.app.data.Rotation
import com.dotpopart.app.data.SettingsRepository
import com.dotpopart.app.device.DeviceInfo
import com.dotpopart.engine.Inputs
import com.dotpopart.engine.ToyCatalog
import com.dotpopart.engine.ToyRunner
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Glyph Toy「Dot=PopArt」。点灯中のおもちゃとローテーションに従って、マトリクスに 10fps で描く。
 *
 * - 描画ループは専用のスレッドで固定レートに刻む（処理時間ぶんのずれを貯めない）。
 * - 同じ内容のコマは送らず、他の表示に上書きされても戻るよう1秒に1回は再送する。
 * - 常時表示：おもちゃの層（setMatrixFrame）だけに描く。(4a) Pro のシステムは、
 *   伏せているとき（向きのセンサー screen_upward = 2）も、手に持っているとき（= 0）もおもちゃの層を表示し、
 *   画面を上にして置いたとき（= 1）だけ LED を消す（段階1とその後のログで確かめた）。
 *   以前はアプリ層と切り替えていたが、明るさの差（システムの倍率が層ごとに違う）と、切り替えの遅れ・
 *   固まりが出たのでやめた。画面を上にして置いている間は、計算も送信も止める。
 * - 設定シートを開いている間は、編集中のおもちゃ（LiveDraft）を描く。✓ で保存されると、そのまま続けて描く。
 * - アプリ連動：対象アプリ（vwfndr）を開いている間は、おもちゃの代わりにそのアプリのアニメーションを出す（[AppCue]）。
 *   出ている間もおもちゃの中の時間は進めるが、振動は止める。
 * - WakeLock は、点灯している間（伏せているときも手に持っているときも）、バッテリーの最適化の対象外のときだけ持つ
 *   （画面が消えている間のカクつき・固まり対策。Doze 中は、対象外でないと WakeLock が効かない）。画面を上にして置いている間は外す。
 */
class DotPopArtToyService : Service() {
    private val events = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> buttonLongPress = true
                GlyphToy.EVENT_AOD -> Log.d(TAG, "aod event")
            }
        }
    }
    private val messenger = Messenger(events)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: SettingsRepository
    private lateinit var hub: InputHub
    private lateinit var cue: AppCue
    private lateinit var power: PowerManager
    private var vibrator: Vibrator? = null

    @Volatile private var settings: AppSettings? = null
    @Volatile private var buttonLongPress = false

    @Volatile private var bound = false
    private var thread: HandlerThread? = null
    private var loop: Handler? = null

    // ここから下はループのスレッドだけが触る
    private var nextTick = 0L
    private var lastTickAt = 0L
    private var runner: ToyRunner? = null
    private var runnerKey = ""

    /**
     * ふわっと出し入れする度合い（0：消えている、1：そのまま）。光り始め・持ち上げて再び光るとき・
     * おもちゃの切り替えで使う。画面を上にして置いたときはシステムが一瞬で消すので、消える側は出し入れできない。
     */
    private var fade = 0.0

    /** 前の刻みで、設定シートの編集中のおもちゃを描いていたか（編集中の切り替えは出し入れしない）。 */
    private var lastDraft = false
    private var wasPaused = false
    private val inputs = Inputs()
    private var frame = FloatArray(0)
    private val toyLayer = LayerOut(toy = true)
    private var faceDown = false
    private var faceSince = 0L
    private var upSince = 0L
    private var flatUp = false
    private var flatSince = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastHaptic = 0L
    private var pendingActive: String? = null
    private var rotationWriteAt = 0L
    private var statFrames = 0
    private var statAt = 0L

    override fun onCreate() {
        super.onCreate()
        repo = (application as DotPopArtApp).settings
        hub = InputHub(this)
        cue = AppCue(this)
        power = getSystemService(PowerManager::class.java)
        vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
        scope.launch { repo.settings.collect { settings = it } }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "onBind")
        bound = true
        GlyphLink.acquire(this, this) {
            // 前のプロセスが開いたままのアプリ層がシステムに残っていると、おもちゃの層より優先して表示され、
            // 古いコマのまま止まって見える（アプリの更新や強制終了のあとに起きた）。最初に必ず閉じる
            GlyphLink.manager?.let { runCatching { it.closeAppMatrix() } }
            if (bound) startLoop()
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind")
        bound = false
        stopLoop()
        GlyphLink.serviceDrawing = false
        // 最後に描いていた層を片付ける（アプリ層に turnOff を使うと、戻した表示の上に黒を重ねてしまう）
        GlyphLink.manager?.let { m ->
            runCatching { m.turnOff() }
        }
        GlyphLink.release(this)
        return false
    }

    override fun onDestroy() {
        stopLoop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startLoop() {
        if (thread != null) return
        val t = HandlerThread("dpa-loop").also { it.start() }
        thread = t
        loop = Handler(t.looper)
        nextTick = 0
        lastTickAt = 0
        fade = 0.0
        loop?.post(tick)
    }

    private fun stopLoop() {
        val t = thread ?: return
        loop?.removeCallbacksAndMessages(null)
        loop?.post {
            hub.stop()
            releaseWake()
        }
        t.quitSafely()
        thread = null
        loop = null
    }

    private val tick = Runnable { runTick() }

    private fun runTick() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTickAt == 0L) PERIOD_MS / 1000.0 else (now - lastTickAt) / 1000.0
        lastTickAt = now
        val s = settings
        val m = GlyphLink.manager
        if (s == null || m == null) {
            schedule(PERIOD_MS)
            return
        }
        GlyphLink.serviceDrawing = true
        val draft = LiveDraft.current
        val def = ToyCatalog[draft?.toyId ?: pendingActive ?: s.activeToy] ?: ToyCatalog.default
        hub.start(fastRate = !isPaused() && def.usesTilt)
        val flipped = updateFace(now)
        if (isPaused()) {
            // 画面を上にして置いている間は、システムが LED を消している。何もせず、短めに見に来る
            if (!wasPaused) {
                // 持ち上げたとき、システムは最後のコマを出し直すので、消したコマを預けておく。
                // そのあと、こちらから 0 からふわっと出す
                toyLayer.sendRaw(m, IntArray(DeviceInfo.geo.size), now)
                wasPaused = true
            }
            releaseWake()
            toyLayer.reset()
            fade = 0.0
            lastTickAt = 0
            schedule(PAUSED_PERIOD_MS)
            return
        }
        wasPaused = false
        // 伏せているときも手に持っているときも CPU を起こしておく。手に持って画面が消えている間に外すと、
        // CPU が止まって1.2秒ほどコマが届かないことが、数秒おきに起きる
        holdWakeIfAllowed()

        // 設定シートを開いている間は、編集中のおもちゃを描く（ローテーションの記録は続ける）
        val decided = decideActive(s, flipped)
        val wantId = draft?.toyId ?: decided
        val wantKey = runnerKeyOf(wantId, s, draft?.params)
        val current = runner
        // 切り替えは、今のおもちゃをふわっと消してから、次をふわっと出す。設定シートの編集中は待たせない
        val fadingOut = current != null && wantKey != runnerKey && draft == null && !lastDraft && fade > 0.0
        val r = if (fadingOut) current!! else ensureRunner(wantId, s, draft?.params)
        lastDraft = draft != null
        fade = if (fadingOut) fade - dt / FADE_OUT_SEC else fade + dt / FADE_IN_SEC
        fade = fade.coerceIn(0.0, 1.0)
        inputs.epochMillis = System.currentTimeMillis()
        // 背面から見ると、端末の x 軸（画面側から見た右）は左を向く。マトリクスの配列は背面から見た向きで
        // 左上が (0, 0)（システムの時計のコマで確かめた）なので、重力の x は ax のまま使う
        inputs.gravityX = hub.ax
        inputs.gravityY = hub.ay
        inputs.batteryLevel = hub.batteryLevel
        inputs.charging = hub.charging
        inputs.notifications = NotificationCounter.count.get()
        r.advance(dt)
        val haptic = r.toy.consumeHaptic()

        r.draw(frame)
        cue.apply(frame, DeviceInfo.geo, s, now, dt)
        if (!cue.visible) vibrate(haptic, now)
        val pattern = TestPattern.frame
        if (pattern != null) {
            toyLayer.sendRaw(m, pattern, now)
        } else {
            toyLayer.send(m, frame, draft?.brightness ?: s.brightness, now, if (lastDraft) 1.0 else LedMapper.fadeGain(fade))
        }
        if (now - statAt >= STAT_MS) {
            Log.i(TAG, "toy=${def.id} sent=$statFrames/${(now - statAt) / 1000}s face=${if (faceDown) "down" else "up"} brightness=${s.brightness} wake=${wakeLock?.isHeld == true} cue=${cue.state}")
            statFrames = 0
            statAt = now
        }
        schedule(PERIOD_MS, if (r.toy.stepsPerSecond <= MAX_STEP_SYNC_RATE) r.secondsUntilNextStep() else null)
    }

    /** おもちゃの層への送信。同じ内容のコマは送らず、他の表示に上書きされても戻るよう1秒に1回は再送する。 */
    private inner class LayerOut(val toy: Boolean) {
        private var colors = IntArray(0)
        private var last: IntArray? = null
        private var lastAt = 0L

        fun send(m: com.nothing.ketchum.GlyphMatrixManager, frame: FloatArray, level: Int, now: Long, gain: Double = 1.0) {
            if (colors.size != frame.size) colors = IntArray(frame.size)
            LedMapper.map(frame, level, colors, gain)
            sendRaw(m, colors, now)
        }

        /** 値をそのまま送る（明るさの下限の測定用の縦線など）。 */
        fun sendRaw(m: com.nothing.ketchum.GlyphMatrixManager, colors: IntArray, now: Long) {
            val prev = last
            if (prev != null && prev.contentEquals(colors) && now - lastAt < RESEND_MS) return
            try {
                m.setMatrixFrame(colors)
                last = colors.copyOf()
                lastAt = now
                statFrames++
            } catch (e: Exception) {
                Log.w(TAG, "send failed: ${e.message}")
            }
        }

        /** 次は必ず送る（送らなかった間に、ほかの表示で上書きされているかもしれないため）。 */
        fun reset() {
            last = null
        }
    }

    /**
     * 次の刻みを予約する。刻みは固定レート（[PERIOD_MS] ごと）で、1周期以上遅れたら（CPU が止まっていたなど）
     * 追いつかずに刻み直す。
     *
     * [untilStepSec] があれば、おもちゃが次のステップへ進む瞬間にも刻む。固定レートだけだと、
     * 1秒に9ステップの DODGE が10fps で送られ、9コマに1回だけ0.2秒止まって見える（ガクガクする）。
     * ステップの瞬間に描けば、動きの間隔がそろう。
     */
    private fun schedule(periodMs: Long, untilStepSec: Double? = null) {
        if (periodMs != PERIOD_MS) {
            // 止まっている間は固定レートにこだわらない。動き出したら刻み直す
            nextTick = 0L
            loop?.postDelayed(tick, periodMs)
            return
        }
        val periodNanos = periodMs * 1_000_000L
        val afterWork = SystemClock.elapsedRealtimeNanos()
        if (nextTick == 0L || afterWork - nextTick > periodNanos) nextTick = afterWork + periodNanos
        while (nextTick <= afterWork) nextTick += periodNanos
        var target = nextTick
        if (untilStepSec != null) {
            val stepAt = afterWork + (untilStepSec * 1e9).toLong() + STEP_SLACK_NANOS
            if (stepAt < target - STEP_SLACK_NANOS) target = stepAt
        }
        loop?.postDelayed(tick, ((target - afterWork) / 1_000_000L).coerceAtLeast(0L))
    }

    /** 画面を上にして置いている間（システムが LED を消す）は止める。 */
    private fun isPaused(): Boolean = DeviceInfo.aodOnly && flatUp

    /** 伏せたかどうかを更新する。「伏せるたび」のために、伏せた瞬間なら true を返す。 */
    private fun updateFace(now: Long): Boolean {
        if (!DeviceInfo.aodOnly) return false
        // システムの向きのセンサーがあれば、それに従う（LED の切り替えと完全にそろう）
        val sys = hub.systemFace
        if (sys >= 0) {
            val down = sys == 2
            flatUp = sys == 1
            if (down == faceDown) return false
            faceDown = down
            Log.i(TAG, if (down) "face down (system)" else "face up (system=$sys)")
            if (!down) {
                upSince = now
                return false
            }
            return now - upSince >= FLIP_MIN_UP_MS
        }
        // 画面を上にして置いたか（机の上など）
        if (!flatUp) {
            if (hub.screenFlatUp()) {
                if (flatSince == 0L) flatSince = now
                if (now - flatSince >= FLAT_SETTLE_MS) {
                    flatUp = true
                    flatSince = 0L
                    Log.i(TAG, "flat up")
                }
            } else {
                flatSince = 0L
            }
        } else if (!hub.screenFlatUpLoose()) {
            flatUp = false
            Log.i(TAG, "picked up")
        }
        if (!faceDown) {
            if (hub.screenDown()) {
                if (faceSince == 0L) faceSince = now
                if (now - faceSince >= FACE_SETTLE_MS) {
                    faceDown = true
                    faceSince = 0L
                    Log.i(TAG, "face down")
                    return now - upSince >= FLIP_MIN_UP_MS
                }
            } else {
                faceSince = 0L
            }
        } else if (hub.screenUp()) {
            if (faceSince == 0L) faceSince = now
            if (now - faceSince >= FACE_SETTLE_MS) {
                faceDown = false
                faceSince = 0L
                upSince = now
                Log.i(TAG, "face up")
            }
        } else {
            faceSince = 0L
        }
        return false
    }

    /** ローテーションと Glyph ボタンで、今出すおもちゃを決める。保存は非同期なので、反映されるまで手元で覚えておく。 */
    private fun decideActive(s: AppSettings, flipped: Boolean): String {
        if (pendingActive == s.activeToy) pendingActive = null
        val wall = System.currentTimeMillis()
        val writing = wall - rotationWriteAt < WRITE_GRACE_MS
        if (!writing && Rotation.active(s)) {
            if (s.rotationLastSwitch <= 0L) {
                rotationWriteAt = wall
                scope.launch { repo.markRotationStart(wall) }
            } else if (Rotation.due(s, wall, flipped)) {
                switchTo(Rotation.next(s), wall, rotation = true)
            }
        }
        if (buttonLongPress) {
            buttonLongPress = false
            val def = ToyCatalog[pendingActive ?: s.activeToy]
            if (s.buttonAction == ButtonAction.ACT && def?.action != null) {
                runner?.toy?.act()
            } else {
                switchTo(Rotation.manualNext(s), wall, rotation = Rotation.active(s))
            }
        }
        return pendingActive ?: s.activeToy
    }

    private fun switchTo(id: String, wall: Long, rotation: Boolean) {
        Log.i(TAG, "switch to $id rotation=$rotation")
        pendingActive = id
        rotationWriteAt = wall
        scope.launch { if (rotation) repo.rotateTo(id, wall) else repo.applyToy(id, emptyMap()) }
    }

    /** おもちゃか設定が変わったら作り直す（設定を変えたら最初からやり直す）。 */
    private fun runnerKeyOf(id: String, s: AppSettings, override: Map<String, Float>?): String {
        val def = ToyCatalog[id] ?: ToyCatalog.default
        val params = def.params(override ?: s.paramsOf(def.id))
        return "${def.id}|${params.asMap()}|${DeviceInfo.matrixLength}"
    }

    private fun ensureRunner(id: String, s: AppSettings, override: Map<String, Float>? = null): ToyRunner {
        val def = ToyCatalog[id] ?: ToyCatalog.default
        val params = def.params(override ?: s.paramsOf(def.id))
        val key = runnerKeyOf(id, s, override)
        val current = runner
        if (current != null && key == runnerKey) return current
        val geo = DeviceInfo.geo
        frame = FloatArray(geo.size)
        toyLayer.reset()
        runnerKey = key
        inputs.notifications = NotificationCounter.count.get()
        return ToyRunner(def, geo, params, inputs)
            .also { runner = it }
    }

    private fun vibrate(strength: Float, now: Long) {
        if (strength <= 0f || now - lastHaptic < 80) return
        lastHaptic = now
        val amp = (255 * strength).roundToInt().coerceIn(1, 255)
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(18, amp)) }
    }

    private fun holdWakeIfAllowed() {
        if (!power.isIgnoringBatteryOptimizations(packageName)) {
            releaseWake()
            return
        }
        val wl = wakeLock ?: power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DotPopArt:matrix").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        // 取りっぱなしにならないよう、期限つきで取り直し続ける
        wl.acquire(WAKE_TIMEOUT_MS)
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    companion object {
        private const val TAG = "DPA"
        const val PERIOD_MS = 100L

        /** ふわっと出す・消すのにかける秒数。 */
        private const val FADE_IN_SEC = 0.6
        private const val FADE_OUT_SEC = 0.35

        /** ステップの瞬間にも刻むのは、1秒あたりのステップがこれ以下のおもちゃだけ（送る回数を増やしすぎない）。 */
        private const val MAX_STEP_SYNC_RATE = 15.0

        /** ステップの瞬間より少しだけ後に刻む（ちょうどだと丸めでステップが次の刻みにずれるため）。 */
        private const val STEP_SLACK_NANOS = 3_000_000L
        private const val PAUSED_PERIOD_MS = 250L
        private const val RESEND_MS = 1000L
        private const val STAT_MS = 30_000L
        private const val FACE_SETTLE_MS = 600L
        private const val FLAT_SETTLE_MS = 1000L
        private const val FLIP_MIN_UP_MS = 3000L
        private const val WRITE_GRACE_MS = 3000L
        private const val WAKE_TIMEOUT_MS = 60_000L
    }
}
