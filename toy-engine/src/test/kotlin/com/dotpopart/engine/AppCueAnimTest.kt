package com.dotpopart.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** アプリ連動のアニメーション（docs/app-cue.md の 5 章）。vwfndr（B2 ロック）と、ほかの 12 種。 */
class AppCueAnimTest {
    private val geos = listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)
    private val ps = (0..100).map { it / 100.0 }

    @Test
    fun framesAreWellFormed() {
        for (g in geos) {
            val anim = VwfndrLockAnim(g)
            val frames = ps.flatMap { p -> listOf("start p=$p" to anim.start(p), "end p=$p" to anim.end(p, 0.0)) } + ("use" to anim.use(0.0))
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
            val f = VwfndrLockAnim(g).use(0.0)
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
            assertArrayEquals("n=${g.n} start(1) = use", anim.use(0.0), anim.start(1.0), 0f)
            assertArrayEquals("n=${g.n} end(0) = use", anim.use(0.0), anim.end(0.0, 0.0), 0f)
            assertTrue("n=${g.n} end(1) is dark", anim.end(1.0, 0.0).all { it == 0f })
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
                "end" -> anim.end(p, 0.0) to anim.toyMulEnd(p)
                else -> anim.use(0.0) to 0f
            }
            assertArrayEquals("n=$n $phase p=$p", expected, out, 1e-5f)
            assertEquals("n=$n $phase p=$p toy", toyText.toFloat(), toy, 1e-5f)
        }
    }

    /** 横持ち：±90° 回すと、縦持ちの十字を斜めの軸で折り返した形（横 5・縦 9）になる。途中の角度でも形がくずれない。 */
    @Test
    fun vwfndrTurnsSideways() {
        for (g in geos) {
            val anim = VwfndrLockAnim(g)
            val up = anim.use(0.0)
            for (turn in listOf(90.0, -90.0)) {
                anim.turn = turn
                val side = anim.use(0.0)
                for (y in 0 until g.n) for (x in 0 until g.n) {
                    assertEquals("n=${g.n} turn=$turn ($x, $y)", up[g.idx(y, x)], side[g.idx(x, y)], 0f)
                }
                assertArrayEquals("n=${g.n} turn=$turn start(1) = use", side, anim.start(1.0), 0f)
                assertTrue("n=${g.n} turn=$turn end(1) is dark", anim.end(1.0, 0.0).all { it == 0f })
            }
            // 逆さま：十字は 180° 回しても同じ形
            anim.turn = 180.0
            assertArrayEquals("n=${g.n} turn=180", up, anim.use(0.0), 0f)
            for (turn in listOf(15.0, 45.0, -60.0, 135.0)) {
                anim.turn = turn
                val f = anim.use(0.0)
                val lit = f.count { it > 0f }
                // 斜めの腕は Bresenham で短く（マスが少なく）なる
                assertTrue("n=${g.n} turn=$turn lit=$lit", lit in (g.n * 2 / 3)..g.n)
                for (i in f.indices) if (!g.inside(i)) assertEquals(0f, f[i])
            }
        }
    }

    @Test
    fun allAnimsAreWellFormed() {
        for (g in geos) for (id in AppCueAnims.ids) for (h in listOf(0.0, 137.0)) {
            val anim = AppCueAnims.create(id, g).also { it.heading = h }
            val frames = ps.map { p -> "start p=$p" to anim.start(p) } +
                listOf(0.0, 0.45, 1.3, 2.9, 6.1, 17.7).map { u -> "use u=$u" to anim.use(u) } +
                ps.flatMap { p -> listOf(0.0, 5.3).map { u0 -> "end p=$p u0=$u0" to anim.end(p, u0) } }
            for ((name, f) in frames) {
                val where = "$id n=${g.n} h=$h $name"
                assertEquals(where, g.size, f.size)
                for (i in f.indices) {
                    assertFalse("$where NaN at $i", f[i].isNaN())
                    assertTrue("$where out of range at $i: ${f[i]}", f[i] in 0f..1f)
                    if (!g.inside(i)) assertEquals("$where lit outside at $i", 0f, f[i])
                }
            }
            assertArrayEquals("$id n=${g.n} h=$h start(1) = use(0)", anim.use(0.0), anim.start(1.0), 1e-6f)
            for (u0 in listOf(0.0, 5.3)) assertTrue("$id n=${g.n} h=$h end(1) is dark", anim.end(1.0, u0).all { it == 0f })
            if (anim.darkUse) assertTrue("$id n=${g.n} dark use", anim.use(2.0).all { it == 0f })
            if (!anim.animatedUse) assertArrayEquals("$id n=${g.n} still use", anim.use(0.0), anim.use(3.7), 0f)
        }
    }

    /** マップ（13×13）：8 方向それぞれで、矢印のマスは上向き 27・斜め 24。 */
    @Test
    fun mapsArrowHasEightDirections() {
        val anim = AppCueAnims.create("nav_d", Geo.PHONE_4A_PRO)
        for (dir in 0 until 8) {
            anim.heading = dir * 45.0
            val lit = anim.use(0.0).count { it > 0f }
            assertEquals("dir=$dir", if (dir % 2 == 0) 27 else 24, lit)
        }
    }

    /** 期待値は tools/reference/dump_app_cue_more.js が docs/prototype/app-cue-more.js から書き出したもの。 */
    @Test
    fun allAnimsMatchPrototype() {
        val lines = javaClass.getResource("/app-cue-more-reference.txt")!!.readText().lines().filter { it.isNotBlank() }
        assertEquals(1216, lines.size)
        for (line in lines) {
            val (head, body) = line.split('|')
            val (id, n, phase, t, u0, heading) = head.split(' ')
            val g = Geo.of(n.toInt())
            val expected = FloatArray(g.size)
            if (body.isNotEmpty()) for (cell in body.split(',')) {
                val (i, v) = cell.split(':')
                expected[i.toInt()] = v.toFloat()
            }
            val anim = AppCueAnims.create(id, g).also { it.heading = heading.toDouble() }
            val out = when (phase) {
                "start" -> anim.start(t.toDouble())
                "use" -> anim.use(t.toDouble())
                else -> anim.end(t.toDouble(), u0.toDouble())
            }
            assertArrayEquals(head, expected, out, 1e-5f)
        }
    }

    private operator fun <T> List<T>.component6() = this[5]
}
