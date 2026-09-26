package com.dotpopart.engine.toys

import com.dotpopart.engine.DotFont
import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * CLOCK：時と分を上下2段に出し、外周のリングが毎分0秒から時計回りに埋まっていく。
 * リングは、真ん中の点と同じく秒の変わり目でだけ進める（マスの角度で点けると、ばらばらの間隔で進み、毎秒の点滅とずれて見える）。
 * マスは角度の順に並べて1分に均等に割り振る（(4a) Pro は28マスで、ほぼ2秒に1マス）。
 */
class ClockToy(ctx: ToyContext) : Toy(ctx) {
    private companion object {
        /** 秒リングの先端の明るさ（主な帯の下のほう）。 */
        const val TIP = 0.45
    }

    /** 秒リングのマス。12時の位置から時計回りの順。 */
    private val ring: IntArray by lazy {
        val c = geo.c
        val ringR = if (geo.big) geo.r - 1.2 else 6.0
        (0 until geo.size)
            .filter { i ->
                val dx = i % n - c
                val dy = i / n - c
                geo.inside(i) && sqrt(dx * dx + dy * dy) >= ringR
            }
            .sortedBy { i ->
                val a = atan2(i % n - c, -(i / n - c))
                if (a < 0) a + 2 * PI else a
            }
            .toIntArray()
    }

    override val stepsPerSecond: Double get() = 10.0

    override fun init() {}

    override fun step() {}

    override fun draw(out: FloatArray) {
        val t = inputs.local()
        var h = t.hour
        if (params.int("h12") == 1) h = if (h % 12 == 0) 12 else h % 12
        val sec = t.second
        val hh = h.toString().padStart(2, '0')
        val mm = t.minute.toString().padStart(2, '0')
        val big = geo.big
        val ox = DotFont.twoDigitsX(big)
        val y1 = if (big) 4 else 1
        val y2 = if (big) 14 else 7
        // 秒リングは暗く点灯する部分。設定（0〜100%）を薄い帯（0.14〜0.35）に割り振る。0% は消す（LedMapper）
        val setting = params["ring"] / 100.0
        val level = if (setting <= 0.0) 0.0 else 0.14 + 0.21 * setting
        if (level > 0) {
            // 0秒で先頭の1マス、59秒で全部。いちばん新しいマス（先端）は主な帯で光らせる
            val lit = minOf(ring.size, sec * ring.size / 60 + 1)
            for (k in 0 until lit) out[ring[k]] = (if (k == lit - 1) TIP else level).toFloat()
        }
        for (y in 0 until n) for (x in 0 until n) {
            if (!geo.inside(x, y)) continue
            if (DotFont.on(hh, ox, y1, x, y, big) || DotFont.on(mm, ox, y2, x, y, big)) out[geo.idx(x, y)] = 1f
        }
        // 中央の1点を1秒ごとに点滅させる
        out[geo.idx(geo.c.toInt(), geo.c.toInt())] = if (sec % 2 == 1) 0.8f else 0.15f
    }
}
