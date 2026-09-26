package com.dotpopart.engine

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * マトリクスの形状。中心 C = (N−1)/2 から (x−C)² + (y−C)² ≤ R² のマスだけが実在する。
 * R² は Phone (4a) Pro（13）が 42.25、Phone (3)（25）が 153.25。LED 数は 137／489 になる。
 */
class Geo private constructor(val n: Int, val r2: Double) {
    val c: Double = (n - 1) / 2.0
    val r: Double = sqrt(r2)
    val size: Int = n * n

    /** Phone (3) のような広い盤面か。ステップ頻度やスプライトの大きさを切り替えるのに使う。 */
    val big: Boolean = n >= 25

    private val mask = BooleanArray(size) { i ->
        val dx = i % n - c
        val dy = i / n - c
        dx * dx + dy * dy <= r2
    }

    val ledCount: Int = mask.count { it }

    /** 外周のマス（上下左右のどれかが円の外）を、12時の位置から時計回りに並べたもの。 */
    val ring: IntArray = run {
        val cells = (0 until size).filter { i ->
            if (!mask[i]) return@filter false
            val x = i % n
            val y = i / n
            !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
        }
        cells.sortedBy { clockAngle(it % n.toDouble(), (it / n).toDouble()) }.toIntArray()
    }

    fun inside(x: Int, y: Int): Boolean = x in 0 until n && y in 0 until n && mask[y * n + x]

    fun inside(i: Int): Boolean = i in 0 until size && mask[i]

    fun idx(x: Int, y: Int): Int = y * n + x

    /** 12時の方向を 0、時計回りを正とした角度（0〜2π）。 */
    fun clockAngle(x: Double, y: Double): Double {
        val a = atan2(x - c, -(y - c))
        return if (a < 0) a + 2 * PI else a
    }

    companion object {
        val PHONE_4A_PRO = Geo(13, 42.25)
        val PHONE_3 = Geo(25, 153.25)

        fun of(n: Int): Geo = when (n) {
            13 -> PHONE_4A_PRO
            25 -> PHONE_3
            else -> Geo(n, (n / 2.0) * (n / 2.0))
        }
    }
}
