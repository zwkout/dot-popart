package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import com.dotpopart.engine.WorldMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GLOBE：正射投影の地球（地軸の傾きはなし）。
 * 実時間では、画面中央の経度を「太陽直下点の経度 − 60°」にする。「今の昼夜」では右寄り60°の方向から光が当たる。
 */
class GlobeToy(ctx: ToyContext) : Toy(ctx) {
    private companion object {
        /** 海の明るさ。PONG WARS の暗い陣地と同じ（いちばん暗い点灯。LedMapper）。 */
        const val OCEAN = 0.14
    }

    override val stepsPerSecond: Double get() = 10.0

    override fun init() {}

    override fun step() {}

    override fun draw(out: FloatArray) {
        val c = geo.c
        val ss = if (geo.big) 2 else 3
        val re = geo.r + 0.3
        val utcMillisOfDay = Math.floorMod(inputs.epochMillis, 86_400_000L)
        val utcH = utcMillisOfDay / 3_600_000.0
        val sunLon = (12 - utcH) * 15
        val center = when (params.int("spin")) {
            0 -> sunLon - 60
            1 -> 139.7 - time * 6
            else -> 139.7 - time * 20
        }
        val night = params.int("night") == 1
        for (y in 0 until n) for (x in 0 until n) {
            if (!geo.inside(x, y)) continue
            var landN = 0
            var inN = 0
            for (sy in 0 until ss) for (sx in 0 until ss) {
                val nx = (x - 0.5 + (sx + 0.5) / ss - c) / re
                val ny = (y - 0.5 + (sy + 0.5) / ss - c) / re
                val r = nx * nx + ny * ny
                if (r >= 1) continue
                inN++
                val z = sqrt(1 - r)
                val lat = -asin(ny) * 180 / PI
                val lon = center + atan2(nx, z) * 180 / PI
                if (WorldMap.landAt(lat, lon)) landN++
            }
            val i = geo.idx(x, y)
            val lf = landN.toDouble() / (ss * ss)
            if (inN == 0) continue
            val cx = (x - c) / re
            val cy = (y - c) / re
            val cz = sqrt(max(0.0, 1 - cx * cx - cy * cy))
            // 昼：海はいちばん暗い点灯（球の輪郭が見える）、陸は主な帯（LedMapper）。
            // 海岸線のマスは、陸が半分以上なら陸、そうでなければ海にする（間の明るさは、暗い帯と主な帯の境目で跳ぶため）
            val land = 0.62 + 0.38 * (0.35 + 0.65 * cz)
            var v = if (lf >= 0.5) land else OCEAN
            if (night) {
                val lit = cx * 0.866 + cz * 0.5
                if (lit < -0.08) {
                    // 夜側は海も陸も薄い帯（明るさ1・2では消える）。夜側の陸で、まれに街の灯をまたたかせる
                    val twinkle = lf > 0.5 && sin(x * 12.9 + y * 78.2 + time * 0.7) > 0.93
                    v = if (twinkle) 0.7 else if (lf >= 0.5) 0.3 else OCEAN
                } else if (lit < 0.12 && lf >= 0.5) {
                    v = 0.45 // 昼夜の境目の陸は少し暗く
                }
            }
            out[i] = v.toFloat()
        }
    }
}
