package com.dotpopart.engine

/**
 * 数字の書体。(4a) Pro は 3×5、Phone (3) は 5×7。
 * BATTERY の満タン表示を見比べるために F と L を足している。
 */
object DotFont {
    private val FONT3 = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"), '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"), '3' to listOf("111", "001", "111", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"), '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"), '7' to listOf("111", "001", "010", "010", "010"),
        '8' to listOf("111", "101", "111", "101", "111"), '9' to listOf("111", "101", "111", "001", "111"),
        'F' to listOf("111", "100", "111", "100", "100"), 'L' to listOf("100", "100", "100", "100", "111"),
    )
    private val FONT5 = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    )

    fun width(big: Boolean) = if (big) 5 else 3
    fun height(big: Boolean) = if (big) 7 else 5

    /** 文字の送り幅（文字の幅＋字間）。 */
    fun advance(big: Boolean) = if (big) 8 else 4

    /** 文字列を描いたときの全体の幅。 */
    fun textWidth(str: String, big: Boolean) = str.length * advance(big) - (advance(big) - width(big))

    /** 2桁の数字を中央に置くときの x の原点。 */
    fun twoDigitsX(big: Boolean) = if (big) 6 else 3

    /** 文字列 [str] を (ox, oy) から描いたとき、(x, y) が点灯するか。 */
    fun on(str: String, ox: Int, oy: Int, x: Int, y: Int, big: Boolean): Boolean {
        val font = if (big) FONT5 else FONT3
        val w = width(big)
        val h = height(big)
        val step = advance(big)
        for (i in str.indices) {
            val dx = x - (ox + i * step)
            val dy = y - oy
            if (dx in 0 until w && dy in 0 until h) {
                val glyph = font[str[i]] ?: return false
                return glyph[dy][dx] == '1'
            }
        }
        return false
    }
}
