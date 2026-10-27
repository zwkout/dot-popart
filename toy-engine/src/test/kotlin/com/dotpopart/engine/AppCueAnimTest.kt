package com.dotpopart.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** アプリ連動のアニメーション（docs/app-cue.md の 5 章）。 */
class AppCueAnimTest {
    private val geos = listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)
    private val ps = (0..100).map { it / 100.0 }

    @Test
    fun framesAreWellFormed() {
        for (g in geos) {
            val anim = VwfndrLockAnim(g)
            val frames = ps.flatMap { p -> listOf("start p=$p" to anim.start(p), "end p=$p" to anim.end(p)) } + ("use" to anim.use())
            for ((name, f) in frames) {
                assertEquals("n=${g.n} $name", g.size, f.size)
                for (i in f.indices) {
                    assertFalse("n=${g.n} $name NaN at $i", f[i].isNaN())
                    assertTrue("n=${g.n} $name out of range at $i", f[i] in 0f..1f)
                    if (!g.inside(i)) assertEquals("n=${g.n} $name lit outside at $i", 0f, f[i])
                }
            }
        }
    }

    @Test
    fun useIsCrossOnly() {
        for (g in geos) {
            val f = VwfndrLockAnim(g).use()
            val c = (g.n - 1) / 2
            val lit = f.indices.filter { f[it] > 0f }
            assertEquals("n=${g.n}", g.n, lit.size)
            for (i in lit) assertTrue("n=${g.n} off cross at $i", i % g.n == c || i / g.n == c)
            assertEquals(0.9f, f[g.idx(c, c)], 1e-6f)
        }
    }

    @Test
    fun phasesJoin() {
        for (g in geos) {
            val anim = VwfndrLockAnim(g)
            assertArrayEquals("n=${g.n} start(1) = use", anim.use(), anim.start(1.0), 0f)
            assertArrayEquals("n=${g.n} end(0) = use", anim.use(), anim.end(0.0), 0f)
            assertTrue("n=${g.n} end(1) is dark", anim.end(1.0).all { it == 0f })
            assertEquals(1f, anim.toyMulStart(0.0))
            assertEquals(0f, anim.toyMulStart(1.0))
            assertEquals(0f, anim.toyMulEnd(0.0))
            assertEquals(1f, anim.toyMulEnd(1.0))
        }
    }

    /** 期待値は tools/reference/dump_app_cue.js が docs/prototype/app-cue.js から書き出したもの。 */
    @Test
    fun matchesPrototype() {
        val json = javaClass.getResource("/app-cue-reference.json")!!.readText()
        val caseRe = Regex("\\{\"n\":(\\d+),\"phase\":\"(\\w+)\",\"p\":([\\d.]+),\"toy\":([\\d.]+),\"frame\":\\[([^\\]]*)]}")
        val cases = caseRe.findAll(json).toList()
        assertEquals(2 * (13 * 2 + 1), cases.size)
        for (m in cases) {
            val (n, phase, pText, toyText, frameText) = m.destructured
            val p = pText.toDouble()
            val anim = VwfndrLockAnim(Geo.of(n.toInt()))
            val expected = frameText.split(',').map { it.toFloat() }.toFloatArray()
            val (out, toy) = when (phase) {
                "start" -> anim.start(p) to anim.toyMulStart(p)
                "end" -> anim.end(p) to anim.toyMulEnd(p)
                else -> anim.use() to 0f
            }
            assertArrayEquals("n=$n $phase p=$p", expected, out, 1e-5f)
            assertEquals("n=$n $phase p=$p toy", toyText.toFloat(), toy, 1e-5f)
        }
    }
}
