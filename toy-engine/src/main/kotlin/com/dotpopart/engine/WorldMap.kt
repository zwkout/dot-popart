package com.dotpopart.engine

import kotlin.math.floor

/** 正距円筒図法の陸／海のビットマップ（[WORLD_MAP_ROWS]）。 */
object WorldMap {
    private val w = WORLD_MAP_WIDTH
    private val h = WORLD_MAP_HEIGHT

    fun landAt(lat: Double, lon: Double): Boolean {
        val r = floor((90 - lat) / 180 * h).toInt().coerceIn(0, h - 1)
        var u = ((lon + 180) / 360) % 1.0
        if (u < 0) u += 1.0
        val c = floor(u * w).toInt().coerceIn(0, w - 1)
        return WORLD_MAP_ROWS[r][c] == '#'
    }
}
