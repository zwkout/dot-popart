package com.dotpopart.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ToyEngineTest {
    private val geos = listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)
    private val zone = ZoneId.of("Asia/Tokyo")
    private val start = ZonedDateTime.of(2026, 9, 24, 23, 58, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun geoMatchesDevices() {
        assertEquals(137, Geo.PHONE_4A_PRO.ledCount)
        assertEquals(489, Geo.PHONE_3.ledCount)
        // 行ごとの LED 数（実機の LED の並び）
        assertEquals(listOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5), rowCounts(Geo.PHONE_4A_PRO))
        assertEquals(
            listOf(7, 11, 15, 17, 19, 21, 21, 23, 23) + List(7) { 25 } + listOf(23, 23, 21, 21, 19, 17, 15, 11, 7),
            rowCounts(Geo.PHONE_3),
        )
        for (g in geos) {
            // 外周は12時の位置から始まる
            val first = g.ring.first()
            assertEquals(0, first / g.n)
        }
    }

    private fun rowCounts(g: Geo) = (0 until g.n).map { y -> (0 until g.n).count { x -> g.inside(x, y) } }

    /** 全おもちゃ × 両形状 × 設定の組み合わせで、描画の条件を確かめる。 */
    @Test
    fun allToysDrawValidFrames() {
        for (def in ToyCatalog.all) for (geo in geos) for (values in variants(def)) for (demo in listOf(false, true)) {
            val label = "${def.id} n=${geo.n} $values demo=$demo"
            val inputs = Inputs(epochMillis = start, zone = zone, batteryLevel = 15, charging = false)
            val runner = ToyRunner(def, geo, def.params(values), inputs, Random(42), EngineOptions(demo = demo))
            val out = FloatArray(geo.size)
            // 3分ぶん（10fps）。途中で入力をいろいろ変える
            for (frame in 0 until 1800) {
                driveInputs(inputs, frame)
                runner.advance(0.1)
                if (frame == 900) runner.toy.act()
                if (frame == 1200) runner.toy.restart()
                if (frame % 3 == 0) checkFrame(label, geo, runner, out)
            }
        }
    }

    /** 終わりのあるおもちゃ（OTHELLO・LIFE・MAZE）は、一定時間内に自分で最初からやり直す。 */
    @Test
    fun finiteToysResetThemselves() {
        for (id in listOf("othello", "life", "maze")) for (geo in geos) for (speed in 0..2) {
            val def = ToyCatalog[id]!!
            val inputs = Inputs(epochMillis = start, zone = zone)
            // 速さは「普通」で固定だが、ほかの速さでも終わることを確かめる
            val runner = ToyRunner(def, geo, def.paramsOverridingFixed(mapOf("speed" to speed.toFloat())), inputs, Random(7))
            val limitSec = 15 * 60
            var sec = 0.0
            while (runner.toy.resets < 2 && sec < limitSec) {
                runner.advance(0.1)
                sec += 0.1
            }
            assertTrue("$id n=${geo.n} speed=$speed resets=${runner.toy.resets} in ${sec}s", runner.toy.resets >= 2)
        }
    }

    /** DODGE（ぶつかる）と MERGE（盤面が詰まる）も、傾けずに置いておくと自動で遊び、自分で最初からやり直す。 */
    @Test
    fun tiltGamesResetThemselves() {
        for (id in listOf("dodge", "merge")) for (geo in geos) {
            val def = ToyCatalog[id]!!
            val runner = ToyRunner(def, geo, def.params(), Inputs(epochMillis = start, zone = zone), Random(9))
            var sec = 0.0
            while (runner.toy.resets < 1 && sec < 15 * 60) {
                runner.advance(0.1)
                sec += 0.1
            }
            assertTrue("$id n=${geo.n} resets=${runner.toy.resets} in ${sec}s", runner.toy.resets >= 1)
        }
    }

    /** 終わりのないおもちゃも、restart() で最初から作り直せて、描画が壊れない。 */
    @Test
    fun endlessToysCanRestart() {
        for (def in ToyCatalog.all) for (geo in geos) {
            val inputs = Inputs(epochMillis = start, zone = zone)
            val runner = ToyRunner(def, geo, def.params(), inputs, Random(3))
            val out = FloatArray(geo.size)
            repeat(5) {
                repeat(50) { runner.advance(0.1) }
                runner.toy.restart()
                assertEquals(0.0, runner.toy.time, 0.0)
                checkFrame("${def.id} restart", geo, runner, out)
            }
        }
    }

    @Test
    fun paramsAreClampedAndDefaulted() {
        val def = ToyCatalog["pong"]!!
        val p = def.params(mapOf("balls" to 99f, "speed" to 2f))
        assertEquals(5, p.int("balls"))
        // 固定値は、保存された値があっても使わない
        assertEquals(1, p.int("speed"))
        assertEquals(mapOf("balls" to 5f), p.asMap())
        assertEquals(2, def.params().int("balls"))
        assertEquals(1, ToyCatalog["dodge"]!!.params(mapOf("speed" to Float.NaN)).int("speed"))
    }

    @Test
    fun runnerLimitsCatchUp() {
        val def = ToyCatalog["pong"]!!
        val runner = ToyRunner(def, Geo.PHONE_4A_PRO, def.params(), Inputs(), Random(1))
        runner.advance(60.0) // 1分止まっていても、追いつくのは 0.25 秒ぶんだけ
        assertEquals(ToyRunner.MAX_CATCH_UP_SEC, runner.toy.time, 1e-9)
    }

    private fun checkFrame(label: String, geo: Geo, runner: ToyRunner, out: FloatArray) {
        out.fill(0f)
        runner.toy.draw(out)
        assertEquals(label, geo.size, out.size)
        for (i in out.indices) {
            val v = out[i]
            if (v.isNaN()) fail("$label: NaN at $i")
            if (v < 0f || v > 1f) fail("$label: value $v out of range at $i")
            if (!geo.inside(i) && v != 0f) fail("$label: lit outside the circle at (${i % geo.n}, ${i / geo.n}) = $v")
        }
    }

    /** 既定値に加えて、区切りボタンは全選択肢、スライダーは最小と最大を1つずつ変えた組み合わせ。 */
    private fun variants(def: ToyDef): List<Map<String, Float>> {
        val list = mutableListOf<Map<String, Float>>(emptyMap())
        for (c in def.controls) {
            when (c) {
                is Control.Choice -> c.options.indices.forEach { list.add(mapOf(c.key to it.toFloat())) }
                is Control.Slider -> {
                    list.add(mapOf(c.key to c.min))
                    list.add(mapOf(c.key to c.max))
                }
            }
        }
        return list.distinct()
    }

    /** 時刻を進め（0時・正午をまたぐ）、傾きを回し、電池と充電と通知を変える。 */
    private fun driveInputs(inputs: Inputs, frame: Int) {
        // 1コマで 40 秒進める：3分のテストで約20時間ぶん
        inputs.epochMillis = start + frame * 40_000L
        val a = 2 * PI * frame / 300.0
        inputs.gravityX = (9.8 * sin(a)).toFloat()
        inputs.gravityY = (9.8 * cos(a)).toFloat()
        if (frame % 450 in 400..449) {
            inputs.gravityX = 0.2f
            inputs.gravityY = -0.1f
        }
        inputs.charging = (frame / 200) % 2 == 0
        inputs.batteryLevel = if (inputs.charging) minOf(100, 15 + frame / 10 % 90) else 60
        if (frame % 97 == 0) inputs.notifications++
    }
}
