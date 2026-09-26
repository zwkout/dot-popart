package com.dotpopart.app

import com.dotpopart.app.glyph.LedMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedMapperTest {
    @Test
    fun dimBandAtFloorOnEveryLevel() {
        // 暗く点灯する部分（PONG WARS の暗い陣地 0.14 など）はちょうど下限。薄い帯の上端は下限の2倍
        for (level in 0..3) assertEquals(LedMapper.FLOOR, LedMapper.effective(0.14f, level), 0.0)
        for (level in 0..3) assertEquals(LedMapper.FLOOR * 2, LedMapper.effective(0.35f, level), 1e-9)
        for (level in 0..3) assertEquals(0.0, LedMapper.effective(0.01f, level), 0.0)
    }

    /** 明るさ1でも、主役（1.0）と暗く点灯する部分の差が4倍、主役の一番暗いところでも2倍ある。 */
    @Test
    fun contrastAtLowestBrightness() {
        assertEquals(4.0, LedMapper.effective(1f, 0) / LedMapper.effective(0.14f, 0), 0.01)
        assertTrue(LedMapper.effective(0.36f, 0) / LedMapper.effective(0.14f, 0) >= 2.0)
    }

    @Test
    fun brighterWithLevelAndValue() {
        for (level in 0..3) {
            var prev = 0.0
            for (k in 36..100) {
                val e = LedMapper.effective(k / 100f, level)
                assertTrue("level=$level v=$k", e >= prev)
                assertTrue("visible level=$level v=$k", e >= LedMapper.FLOOR)
                prev = e
            }
        }
        for (level in 1..3) assertTrue(LedMapper.effective(1f, level) > LedMapper.effective(1f, level - 1))
    }

    /** 明るさ4では、主役（1.0）と薄い点の差が約16倍（実効 1023 対 64）。 */
    @Test
    fun contrastAtFullBrightness() {
        assertEquals(1023.0 / 64.0, LedMapper.effective(1f, 3) / LedMapper.effective(0.14f, 3), 0.01)
        val out = IntArray(1)
        LedMapper.map(floatArrayOf(1f), 3, out)
        assertEquals(4095, out[0])
    }
}
