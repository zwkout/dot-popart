package com.dotpopart.engine

import kotlin.math.floor

/**
 * 正距円筒図法の、マスごとの陸の割合の地図（[WORLD_MAP_ROWS]）。
 * 行ごとに陸の割合を左から足した表を持ち、経度の区間に陸がどれだけあるかを、マスの途中まで含めて正確に出す。
 */
object WorldMap {
    val width = WORLD_MAP_WIDTH
    val height = WORLD_MAP_HEIGHT

    /** sums[r][c] は、行 r の左端から c マスぶんの陸の割合の合計。 */
    private val sums: Array<DoubleArray> = Array(height) { r ->
        val row = WORLD_MAP_ROWS[r]
        val s = DoubleArray(width + 1)
        for (c in 0 until width) s[c + 1] = s[c] + level(row[c])
        s
    }

    private fun level(ch: Char): Double = when (ch) {
        '.' -> 0.0
        '#' -> 1.0
        else -> (ch - '0').toDouble() / WORLD_MAP_LEVELS
    }

    /** 緯度 lat が入る行。 */
    fun row(lat: Double): Int = floor((90 - lat) / 180 * height).toInt().coerceIn(0, height - 1)

    /** 行 r の、経度 lon0〜lon1（lon0 < lon1、度）で陸が占める割合（0〜1）。 */
    fun landBetween(r: Int, lon0: Double, lon1: Double): Double =
        (landUpTo(r, lon1) - landUpTo(r, lon0)) / ((lon1 - lon0) / 360 * width)

    /** 行 r の、経度 −180° から lon までの陸の長さ（マス単位）。1周を超えたら、そのぶん1周ぶんを足す。 */
    private fun landUpTo(r: Int, lon: Double): Double {
        val u = (lon + 180) / 360 * width
        val laps = floor(u / width)
        val v = u - laps * width
        val c = v.toInt().coerceIn(0, width - 1)
        val s = sums[r]
        return laps * s[width] + s[c] + (v - c) * (s[c + 1] - s[c])
    }
}
