package com.dotpopart.app.glyph

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.dotpopart.app.data.AppCueTarget
import com.dotpopart.app.data.AppCueTargets
import com.dotpopart.app.data.AppSettings
import com.dotpopart.engine.AppCueAnim
import com.dotpopart.engine.Geo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * アプリ連動（docs/app-cue.md）。対象アプリを開いている間だけ、おもちゃの代わりにそのアプリのアニメーションを出す。
 *
 * - 前面のアプリは UsageStatsManager の画面遷移のイベントで調べる（「使用状況へのアクセス」が要る）。
 *   対象アプリの RESUMED で開いた、前面にある対象アプリの画面の PAUSED/STOPPED（0.7秒待つ）かほかのアプリの RESUMED で閉じたとみなす。
 *   見るのはどのアプリが前面に来たかだけで、使用時間などは読まないし、保存もしない。
 * - IDLE →（開いた）→ START →（伸びきった）→ USE →（閉じた）→ END → IDLE。
 *   START の途中で閉じたら END を、END の途中で同じアプリを開いたら START を、それぞれ頭から再生する。
 *   対象アプリ A から B へ直接切り替わったときは、A の END を最後まで再生してから B の START を始める。
 *   アプリのスイッチをオフにしたときも、閉じたときと同じく END を再生する。
 * - END には USE を何秒続けたか（u0）を渡す（USE の間も動くものは、止まった位置から END を始める）。
 * - マップのように方位を使うアニメーションの間だけ、方位センサー（[CueCompass]）を動かす。
 * - 重ね方：おもちゃのコマに倍率を掛けたあと、アニメーションの値と大きいほうを取る。
 * ループのスレッドだけから呼ぶ。
 */
class AppCue(private val context: Context) {
    private enum class Phase { IDLE, START, USE, END }

    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val event = UsageEvents.Event()

    /** 前面のアクティビティ（「パッケージ/クラス」）。 */
    private var foreground: String? = null
    private var settled: String? = null

    /** 前面のアクティビティが PAUSED/STOPPED になったアプリと、その時刻（少しのあいだ開いたままとみなす）。 */
    private var lingerPackage: String? = null
    private var lingerAt = 0L
    private var settledAt = 0L
    private val seenOpens = HashSet<Long>()
    private var opened: AppCueTarget? = null
    private var pollAt = 0L
    private var permitted = false
    private var permCheckedAt = 0L

    private var phase = Phase.IDLE
    private var elapsed = 0.0
    private var target: AppCueTarget? = null
    private var anim: AppCueAnim? = null
    private var animFrame = FloatArray(0)

    /** END に渡す、USE を続けた秒数。 */
    private var u0 = 0.0

    /** A の END の途中で B が開いたとき、END のあとに始めるアプリ。 */
    private var next: AppCueTarget? = null

    private val compass = CueCompass(context)

    /** 13×13 のマップの矢印の向き（0〜7）。境目で行ったり来たりしないよう、手元で覚えておく。 */
    private var navDir = -1
    private var smoothX = 0.0
    private var smoothY = -1.0

    /** アニメーションを出しているか（IDLE 以外）。出している間はおもちゃの振動を止める。 */
    val visible: Boolean get() = phase != Phase.IDLE

    val state: String get() = if (phase == Phase.IDLE) "idle" else "${target?.id}:${phase.name.lowercase()}"

    /** おもちゃのコマ [frame] にアニメーションを重ねる。[now] は elapsedRealtime、[dt] は前の刻みからの秒。 */
    fun apply(frame: FloatArray, geo: Geo, s: AppSettings, now: Long, dt: Double) {
        val want = desired(s, now)
        // 開いてすぐ閉じたときは、START を出してから、次の刻みで END に移る
        val blip = opened?.takeIf { forced == null && s.appCueOn(it.id) }
        opened = null
        advance(want ?: blip, want, geo, dt)
        val a = anim ?: return
        if (phase != Phase.IDLE && a.usesHeading) {
            compass.start()
            a.heading = headingFor(geo)
        } else {
            compass.stop()
        }
        val mul: Float
        when (phase) {
            Phase.IDLE -> return
            Phase.START -> {
                val p = (elapsed / a.startSeconds).coerceIn(0.0, 1.0)
                a.start(p, animFrame)
                mul = a.toyMulStart(p)
            }
            Phase.USE -> {
                a.use(elapsed, animFrame)
                mul = 0f
            }
            Phase.END -> {
                val p = (elapsed / a.endSeconds).coerceIn(0.0, 1.0)
                a.end(p, u0, animFrame)
                mul = a.toyMulEnd(p)
            }
        }
        for (i in frame.indices) frame[i] = maxOf(frame[i] * mul, animFrame[i])
    }

    /** 画面を上にして置いている間など、描かない間はセンサーを止める。 */
    fun pause() = compass.stop()

    /**
     * アニメーションに渡す方位。13×13 は 8 方向に縛るので、今の向きから ±(22.5 + 5)° を超えたら切り替える。
     * 25×25 はなめらかに回すので、向きを少しずつ追わせる（センサーのふらつきを抑える）。
     */
    private fun headingFor(geo: Geo): Double {
        val h = compass.heading
        if (!geo.big) {
            val diff = if (navDir < 0) null else ((h - navDir * 45 + 540) % 360) - 180
            if (diff == null || abs(diff) > NAV_SWITCH_DEG) navDir = Math.floorMod(Math.round(h / 45).toInt(), 8)
            return navDir * 45.0
        }
        val t = Math.toRadians(h)
        smoothX += (sin(t) - smoothX) * NAV_SMOOTH
        smoothY += (-cos(t) - smoothY) * NAV_SMOOTH
        return (Math.toDegrees(atan2(smoothX, -smoothY)) + 360) % 360
    }

    /** 今出したいアプリ（なければ null）。 */
    private fun desired(s: AppSettings, now: Long): AppCueTarget? {
        forced?.let { return if (it) AppCueTargets.byId(forcedApp) ?: AppCueTargets.all.first() else null }
        val anyOn = AppCueTargets.all.any { s.appCueOn(it.id) }
        poll(anyOn, now)
        val pkg = foreground?.substringBefore('/')
            ?: lingerPackage?.takeIf { System.currentTimeMillis() - lingerAt < PAUSE_GRACE_MS }
        return AppCueTargets.ofPackage(pkg)?.takeIf { s.appCueOn(it.id) }
    }

    /** [open] は始めるアプリ（開いてすぐ閉じたものも含む）、[want] は今も開いているアプリ。 */
    private fun advance(open: AppCueTarget?, want: AppCueTarget?, geo: Geo, dt: Double) {
        elapsed += dt
        when (phase) {
            Phase.IDLE -> if (open != null) begin(open, geo)
            Phase.START -> when {
                want != target -> {
                    u0 = 0.0
                    enter(Phase.END)
                }
                elapsed >= anim!!.startSeconds -> enter(Phase.USE)
            }
            Phase.USE -> if (want != target) {
                u0 = elapsed
                enter(Phase.END)
            }
            Phase.END -> when {
                open != null && open == target -> begin(open, geo)
                elapsed >= anim!!.endSeconds -> {
                    // 別の対象アプリに切り替わっていたら、END を最後まで出してから始める
                    val after = want ?: next
                    if (after != null) begin(after, geo) else enter(Phase.IDLE)
                }
                open != null -> next = open
            }
        }
    }

    private fun begin(t: AppCueTarget, geo: Geo) {
        next = null
        if (t != target || anim?.geo != geo) {
            target = t
            anim = t.anim(geo)
            animFrame = FloatArray(geo.size)
        }
        enter(Phase.START)
    }

    private fun enter(p: Phase) {
        phase = p
        elapsed = 0.0
        Log.i("DPA", "cue $state")
    }

    /**
     * 前面のアプリを調べる。
     * イベントは、時刻をつけられてから少し遅れて読めるようになることがある。前回の続きからだけ読むと、
     * 遅れて届いたものを取りこぼすので、確定した時点（[settledAt]、今の [SETTLE_MS] 前）の状態から、
     * それより後のイベントを毎回読み直す。
     * 開いてすぐ閉じた（2回の読み出しの間に開いて閉じた）ときも START を出せるよう、
     * まだ見ていない対象アプリの RESUMED があれば [opened] に入れる。
     *
     * 閉じたとみなすのは、前面にあるアクティビティそのものの PAUSED/STOPPED だけにする。アプリの中で画面が
     * 移るとき（Shazam の Splash → Configuration など）は、前の画面の STOPPED が次の画面の RESUMED のあとに届く。
     * また PAUSED と次の画面の RESUMED が別々の読み出しに分かれることがあるので、PAUSED/STOPPED のあと
     * [PAUSE_GRACE_MS] のあいだは開いたままとみなす（ほかのアプリの RESUMED が来れば、すぐに閉じたとみなす）。
     */
    private fun poll(enabled: Boolean, now: Long) {
        if (!enabled) {
            resetPoll()
            return
        }
        if (now - pollAt < POLL_MS) return
        pollAt = now
        if (permCheckedAt == 0L || now - permCheckedAt >= PERM_CHECK_MS) {
            permitted = hasAccess(context)
            permCheckedAt = now
        }
        if (!permitted) {
            resetPoll()
            return
        }
        val end = System.currentTimeMillis()
        val first = settledAt == 0L
        val begin = if (first) end - LOOKBACK_MS else settledAt
        val nextSettledAt = end - SETTLE_MS
        val events = runCatching { usage.queryEvents(begin, end) }.getOrNull() ?: return
        var fg = settled
        var nextSettled = settled
        var linger: String? = null
        var lingerTime = 0L
        while (events.getNextEvent(event)) {
            val pkg = event.packageName
            val activity = "$pkg/${event.className}"
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    fg = activity
                    linger = null
                    val t = AppCueTargets.ofPackage(pkg)
                    if (t != null && !first && seenOpens.add(event.timeStamp)) opened = t
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> if (activity == fg) {
                    fg = null
                    linger = pkg
                    lingerTime = event.timeStamp
                }
                else -> continue
            }
            if (event.timeStamp < nextSettledAt) nextSettled = fg
        }
        foreground = fg
        lingerPackage = linger
        lingerAt = lingerTime
        settled = nextSettled
        settledAt = nextSettledAt
        seenOpens.removeAll { it < nextSettledAt }
    }

    private fun resetPoll() {
        foreground = null
        settled = null
        lingerPackage = null
        settledAt = 0L
        seenOpens.clear()
        opened = null
    }

    companion object {
        /** デバッグ用：null でなければ、前面のアプリに関係なく [forcedApp] を出す（true）／消す（false）。 */
        @Volatile var forced: Boolean? = null

        /** デバッグ用：[forced] で出すアプリの id（なければ vwfndr）。 */
        @Volatile var forcedApp: String? = null

        private const val NAV_SWITCH_DEG = 22.5 + 5
        private const val NAV_SMOOTH = 0.35

        private const val POLL_MS = 500L
        private const val PERM_CHECK_MS = 5_000L
        private const val LOOKBACK_MS = 60 * 60_000L
        private const val SETTLE_MS = 2_000L
        private const val PAUSE_GRACE_MS = 700L

        /** 「使用状況へのアクセス」が許可されているか。 */
        fun hasAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
