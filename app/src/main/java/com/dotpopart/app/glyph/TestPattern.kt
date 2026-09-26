package com.dotpopart.app.glyph

import com.dotpopart.app.device.DeviceInfo

/**
 * 明るさの下限を測るための縦線（デバッグ版の受け口から出す）。
 * 実効値（システムの倍率 1023/4095 をかけた後の値）のリストを受け取り、左から1列おきに縦線にする。
 * 出している間は、おもちゃの代わりにこれをおもちゃの層に送る。
 */
object TestPattern {
    /** おもちゃの層に送る値（null なら出していない）。 */
    @Volatile var frame: IntArray? = null
        private set

    fun show(effective: List<Int>) {
        val n = DeviceInfo.matrixLength
        val out = IntArray(n * n)
        val geo = DeviceInfo.geo
        effective.forEachIndexed { k, eff ->
            val x = 1 + k * 2
            if (x >= n) return@forEachIndexed
            val raw = (eff * 4095.0 / 1023.0).toInt().coerceIn(0, 4095)
            for (y in 0 until n) if (geo.inside(x, y)) out[y * n + x] = raw
        }
        frame = out
    }

    fun clear() {
        frame = null
    }
}
