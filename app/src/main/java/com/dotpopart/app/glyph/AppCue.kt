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

/**
 * アプリ連動（docs/app-cue.md）。対象アプリを開いている間だけ、おもちゃの代わりにそのアプリのアニメーションを出す。
 *
 * - 前面のアプリは UsageStatsManager の画面遷移のイベントで調べる（「使用状況へのアクセス」が要る）。
 *   対象アプリの RESUMED で開いた、対象アプリの PAUSED/STOPPED かほかのアプリの RESUMED で閉じたとみなす。
 *   見るのはどのアプリが前面に来たかだけで、使用時間などは読まないし、保存もしない。
 * - IDLE →（開いた）→ START →（伸びきった）→ USE →（閉じた）→ END → IDLE。
 *   START の途中で閉じたら END を、END の途中で開いたら START を、それぞれ頭から再生する。
 *   アプリのスイッチをオフにしたときも、閉じたときと同じく END を再生する。
 * - 重ね方：おもちゃのコマに倍率を掛けたあと、アニメーションの値と大きいほうを取る。
 * ループのスレッドだけから呼ぶ。
 */
class AppCue(private val context: Context) {
    private enum class Phase { IDLE, START, USE, END }

    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val event = UsageEvents.Event()

    private var foregroundPackage: String? = null
    private var settledPackage: String? = null
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
        val mul: Float
        when (phase) {
            Phase.IDLE -> return
            Phase.START -> {
                val p = (elapsed / a.startSeconds).coerceIn(0.0, 1.0)
                a.start(p, animFrame)
                mul = a.toyMulStart(p)
            }
            Phase.USE -> {
                a.use(animFrame)
                mul = 0f
            }
            Phase.END -> {
                val p = (elapsed / a.endSeconds).coerceIn(0.0, 1.0)
                a.end(p, animFrame)
                mul = a.toyMulEnd(p)
            }
        }
        for (i in frame.indices) frame[i] = maxOf(frame[i] * mul, animFrame[i])
    }

    /** 今出したいアプリ（なければ null）。 */
    private fun desired(s: AppSettings, now: Long): AppCueTarget? {
        forced?.let { return if (it) AppCueTargets.all.first() else null }
        val anyOn = AppCueTargets.all.any { s.appCueOn(it.id) }
        poll(anyOn, now)
        return AppCueTargets.ofPackage(foregroundPackage)?.takeIf { s.appCueOn(it.id) }
    }

    /** [open] は始めるアプリ（開いてすぐ閉じたものも含む）、[want] は今も開いているアプリ。 */
    private fun advance(open: AppCueTarget?, want: AppCueTarget?, geo: Geo, dt: Double) {
        elapsed += dt
        when (phase) {
            Phase.IDLE -> if (open != null) begin(open, geo)
            Phase.START -> when {
                want != target -> enter(Phase.END)
                elapsed >= anim!!.startSeconds -> enter(Phase.USE)
            }
            Phase.USE -> if (want != target) enter(Phase.END)
            Phase.END -> when {
                open != null -> begin(open, geo)
                elapsed >= anim!!.endSeconds -> enter(Phase.IDLE)
            }
        }
    }

    private fun begin(t: AppCueTarget, geo: Geo) {
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
        var fg = settledPackage
        var nextSettled = settledPackage
        while (events.getNextEvent(event)) {
            val pkg = event.packageName
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    fg = pkg
                    val t = AppCueTargets.ofPackage(pkg)
                    if (t != null && !first && seenOpens.add(event.timeStamp)) opened = t
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> if (pkg == fg) fg = null
                else -> continue
            }
            if (event.timeStamp < nextSettledAt) nextSettled = fg
        }
        foregroundPackage = fg
        settledPackage = nextSettled
        settledAt = nextSettledAt
        seenOpens.removeAll { it < nextSettledAt }
    }

    private fun resetPoll() {
        foregroundPackage = null
        settledPackage = null
        settledAt = 0L
        seenOpens.clear()
        opened = null
    }

    companion object {
        /** デバッグ用：null でなければ、前面のアプリに関係なく最初の対象アプリを出す（true）／消す（false）。 */
        @Volatile var forced: Boolean? = null

        private const val POLL_MS = 500L
        private const val PERM_CHECK_MS = 5_000L
        private const val LOOKBACK_MS = 60 * 60_000L
        private const val SETTLE_MS = 2_000L

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
