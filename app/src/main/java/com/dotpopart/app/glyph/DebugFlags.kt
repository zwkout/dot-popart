package com.dotpopart.app.glyph

import android.util.Log

/**
 * 電池の減り方を測るための切り替え（デバッグ版の受け口から使う）。
 * - [pause]：伏せたままでも、画面を上にして置いたときと同じに止める（おもちゃを止めたときの減り方を測る）。
 * - [legacy]：省電力の工夫（伏せている間に刻みを休む・加速度センサーを止める）を使わず、前の動き方にする。
 * - [startPlan]：決めた時間ごとに、上の2つを端末の中で切り替える。夜中は adb が切れることがあるため。
 */
object DebugFlags {
    @Volatile var pause = false
    @Volatile var legacy = false

    /** 区間の並び（モードと長さ ms）。終わったら、ふだんの動きに戻す。 */
    @Volatile private var plan: List<Pair<String, Long>> = emptyList()
    @Volatile private var planStart = 0L
    @Volatile private var current = ""

    /** 「legacy:120,saver:120,pause:150」の形（モードと分）。モードは legacy・saver・pause。 */
    fun startPlan(spec: String, now: Long) {
        plan = spec.split(',').mapNotNull { part ->
            val (mode, min) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            min.trim().toLongOrNull()?.let { mode.trim() to it * 60_000L }
        }
        planStart = now
        current = ""
        Log.i("DPA", "debug plan: $plan")
    }

    /** サービスの刻みごとに呼ぶ。 */
    fun update(now: Long) {
        val p = plan
        if (p.isEmpty()) return
        var t = now - planStart
        var mode = "done"
        for ((m, ms) in p) {
            if (t < ms) {
                mode = m
                break
            }
            t -= ms
        }
        if (mode == current) return
        current = mode
        pause = mode == "pause"
        legacy = mode == "legacy"
        Log.i("DPA", "debug plan: $mode")
        if (mode == "done") plan = emptyList()
    }
}
