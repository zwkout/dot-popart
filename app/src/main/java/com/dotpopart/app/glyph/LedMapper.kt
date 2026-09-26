package com.dotpopart.app.glyph

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * エンジンの明るさ（0〜1）を、LED に送る値（0〜4095）に変える。
 *
 * 見た目の明るさは「実効値」（送った値 × システムがおもちゃの層にかける倍率 1023/4095）で考える。
 * LED がムラなく光る下限は、(4a) Pro のおもちゃの層で実効 64（48 はムラあり、32 以下は見えない。
 * 室内で縦線を並べて測った）。これより暗くはできないので、エンジンの値を2つの帯に分ける。
 * - 0.02 以下：消灯
 * - 0.35 以下（薄い帯：飾り・背景・暗く点灯する部分）：0.15 以下は下限（実効 64）、0.35 に向けて 128 まで。
 *   「暗く点灯」はどのおもちゃも PONG WARS の暗い陣地（0.14 → 64）にそろえる
 * - それより上（主な帯：主役）：その段階の最大値の 1/3（下限の2倍以上）から最大値までを、対数で割り振る
 * 暗く点灯する部分（下限 64）と主役の差は、明るさ1で 2〜4倍、明るさ4で 5〜16倍になる。
 */
object LedMapper {
    const val FLOOR = 64.0
    const val OFF_MAX = 0.02f
    const val DIM_MAX = 0.35f

    /** これ以下の薄い帯は、ちょうど下限（PONG WARS の暗い陣地と同じ明るさ）。 */
    const val DIM_FLAT = 0.15f

    /** 段階ごとの最大の実効値。4段階目がおもちゃの層の上限（送る値 4095）。 */
    private val PEAK = doubleArrayOf(256.0, 420.0, 680.0, 1023.0)

    /** システムがおもちゃの層にかける倍率の逆数。 */
    private const val TOY_LAYER_GAIN = 4095.0 / 1023.0

    /** 実効値。 */
    fun effective(v: Float, level: Int): Double {
        val lv = level.coerceIn(0, 3)
        return when {
            v <= OFF_MAX -> 0.0
            v <= DIM_MAX -> FLOOR * 2.0.pow((maxOf(0f, v - DIM_FLAT) / (DIM_MAX - DIM_FLAT)).toDouble())
            else -> {
                val peak = PEAK[lv]
                val low = maxOf(FLOOR * 2, peak / 3)
                val t = ((v.coerceAtMost(1f) - DIM_MAX) / (1f - DIM_MAX)).toDouble()
                low * (peak / low).pow(t)
            }
        }
    }

    /** おもちゃの層に送る値。 */
    fun map(frame: FloatArray, level: Int, out: IntArray) {
        for (i in frame.indices) {
            out[i] = (effective(frame[i], level) * TOY_LAYER_GAIN).roundToInt().coerceIn(0, 4095)
        }
    }
}
