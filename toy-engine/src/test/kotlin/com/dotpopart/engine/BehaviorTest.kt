package com.dotpopart.engine

import com.dotpopart.engine.toys.AquariumToy
import com.dotpopart.engine.toys.MeteorToy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/** 実機で見て決めた動きを確かめる。 */
class BehaviorTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val g13 = Geo.PHONE_4A_PRO

    private fun at(h: Int, m: Int = 0, day: Int = 24) =
        ZonedDateTime.of(2026, 9, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun runner(id: String, inputs: Inputs, values: Map<String, Float> = emptyMap(), geo: Geo = g13, demo: Boolean = false) =
        ToyRunner(ToyCatalog[id]!!, geo, ToyCatalog[id]!!.params(values), inputs, Random(11), EngineOptions(demo = demo))

    private fun frame(r: ToyRunner) = FloatArray(r.geo.size).also { r.draw(it) }

    /** #8：正午〜翌正午を1日とし、約8割を 20時〜翌6時に流す。 */
    @Test
    fun meteorScheduleFavorsNight() {
        var night = 0
        var total = 0
        for (seed in 0 until 50) {
            val inputs = Inputs(epochMillis = at(13), zone = zone)
            val r = ToyRunner(ToyCatalog["meteor"]!!, g13, ToyCatalog["meteor"]!!.params(mapOf("per" to 10f)), inputs, Random(seed))
            r.advance(0.2)
            val times = (r.toy as MeteorToy).scheduled
            assertEquals(10, times.size)
            for (t in times) {
                assertTrue(t >= at(12) && t < at(12, day = 25))
                val h = Instant.ofEpochMilli(t).atZone(zone).hour
                if (h >= 20 || h < 6) night++
                total++
            }
        }
        assertEquals(0.8, night.toDouble() / total, 0.001)
    }

    @Test
    fun meteorFlowsAtScheduledTime() {
        val inputs = Inputs(epochMillis = at(12, 1), zone = zone)
        val r = runner("meteor", inputs, mapOf("per" to 10f, "stars" to 0f))
        r.advance(0.2)
        val first = (r.toy as MeteorToy).scheduled.first()
        inputs.epochMillis = first - 1_000
        r.advance(0.2)
        assertTrue(frame(r).none { it >= 0.99f })
        // 予定の時刻を過ぎると、流れ星（明るさ 1）が現れる
        var seen = false
        for (k in 0 until 20) {
            inputs.epochMillis = first + k * 100L
            r.advance(0.1)
            if (frame(r).any { it >= 0.99f }) seen = true
        }
        assertTrue(seen)
    }

    /** #10：「今日の残り時間」は毎分減り、0時に注ぎ直して満杯に戻る。 */
    @Test
    fun sandRemainingTimeDrainsAndRefills() {
        val inputs = Inputs(epochMillis = at(18), zone = zone, gravityY = 9.8f)
        val r = runner("sand", inputs, mapOf("mode" to 1f))
        fun grains() = frame(r).count { it > 0f }
        val atSix = grains()
        assertEquals(Math.round(g13.ledCount * 0.25).toInt(), atSix)
        // 23:50 まで進めると、残りは約 0.7%
        for (m in 0 until 350) {
            inputs.epochMillis = at(18) + m * 60_000L
            r.advance(0.1)
        }
        assertTrue(grains() <= 2)
        // 0時を過ぎると、上から降り注いで満杯に近づく
        inputs.epochMillis = at(0, 0, day = 25) + 500
        repeat(200) { r.advance(0.1) }
        assertTrue("grains=${grains()}", grains() >= g13.ledCount - 2)
    }

    /** #7：挿すと種から今の充電量の高さまで伸び、抜くと約1.4秒でしおれて種に戻る。 */
    @Test
    fun sproutFollowsChargeLevel() {
        val inputs = Inputs(batteryLevel = 80, charging = false)
        val r = runner("sprout", inputs)
        r.advance(0.1)
        fun stem() = (0 until g13.n).count { y -> frame(r)[g13.idx(6, y)] >= 0.7f }
        assertEquals(0, stem()) // 種だけ（0.3）
        inputs.charging = true
        repeat(3) { r.advance(0.1) }
        val early = stem()
        repeat(80) { r.advance(0.1) }
        val grown = stem()
        assertTrue("early=$early grown=$grown", grown > early)
        // 80% の高さ：floor(0.8 × 6.99) = 5 → 茎は6マス（光の粒を含む）
        assertEquals(6, grown)
        inputs.charging = false
        repeat(16) { r.advance(0.1) }
        assertEquals(0, stem())
    }

    @Test
    fun aquariumDropsFoodOnNotification() {
        val inputs = Inputs(notifications = 5)
        val r = runner("aquarium", inputs, mapOf("fish" to 1f, "food" to 1f))
        repeat(5) { r.advance(0.1) }
        fun foodCells() = frame(r).count { it in 0.69f..0.71f }
        assertEquals(0, foodCells())
        inputs.notifications = 6
        repeat(12) { r.advance(0.1) } // 上から落ちてきて見えるようになる
        assertTrue(foodCells() >= 1)
    }

    @Test
    fun marbleVibratesOnHardHit() {
        val inputs = Inputs(gravityX = 9.8f)
        val strong = runner("marble", inputs, mapOf("vib" to 2f))
        var max = 0f
        repeat(60) {
            strong.advance(0.1)
            max = maxOf(max, strong.toy.consumeHaptic())
        }
        assertTrue("haptic=$max", max > 0.5f)
        val off = runner("marble", inputs, mapOf("vib" to 0f))
        repeat(60) {
            off.advance(0.1)
            assertEquals(0f, off.toy.consumeHaptic())
        }
    }

    @Test
    fun batteryBlinksBelowWarning() {
        val inputs = Inputs(batteryLevel = 10)
        val r = runner("battery", inputs, mapOf("warn" to 20f))
        val top = g13.idx(6, 0)
        val values = (0 until 10).map { r.advance(0.1); frame(r)[top] }.toSet()
        assertEquals(setOf(0.2f, 1f), values)
        inputs.batteryLevel = 20 // 下回っていなければ点滅しない
        val steady = (0 until 10).map { r.advance(0.1); frame(r)[top] }.toSet()
        assertEquals(setOf(1f), steady)
    }

    /** #9：100% のときは「FL」と出す。リングは一周埋まり、文字はリングの内側に収まる。 */
    @Test
    fun batteryShowsFlWhenFull() {
        for (geo in listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)) {
            val def = ToyCatalog["battery"]!!
            val r = ToyRunner(def, geo, def.params(), Inputs(batteryLevel = 100), Random(1))
            val out = frame(r)
            val big = geo.big
            val ox = DotFont.twoDigitsX(big)
            val oy = if (big) 9 else 4
            for (y in 0 until geo.n) for (x in 0 until geo.n) {
                val i = geo.idx(x, y)
                if (!geo.inside(i) || i in geo.ring) continue
                val expected = DotFont.on("FL", ox, oy, x, y, big)
                if (expected) assertEquals("n=${geo.n} ($x, $y)", 1f, out[i])
            }
            assertTrue(geo.ring.all { out[it] == 1f })
        }
    }

    /**
     * DODGE：左右の傾きでレーンが決まる（プレイヤーの先頭は下から3段目、1ステップに1マス横へ）。
     * 最初の車がプレイヤーの段に届くのは13ステップ目なので、それまでに確かめる（9ステップ/秒）。
     */
    @Test
    fun dodgeFollowsTilt() {
        val inputs = Inputs(gravityX = 5f)
        val r = runner("dodge", inputs)
        repeat(4) { r.advance(0.1) } // 3ステップ：中央（x=6）から右（x=9）へ
        assertEquals(1f, frame(r)[g13.idx(9, 10)])
        inputs.gravityX = -5f
        repeat(7) { r.advance(0.1) } // 計9ステップ：左（x=3）へ
        assertEquals(1f, frame(r)[g13.idx(3, 10)])
        inputs.gravityX = 0f
        repeat(3) { r.advance(0.1) } // 計12ステップ：中央へ
        assertEquals(1f, frame(r)[g13.idx(6, 10)])
    }

    /** MERGE：傾けたら1回だけ寄せる。傾けたままでは次に進まず、水平に戻してから傾けると次に寄せる。 */
    @Test
    fun mergeMovesOncePerTilt() {
        val inputs = Inputs(gravityX = 5f)
        val r = runner("merge", inputs)
        val first = frame(r).toList()
        repeat(20) { r.advance(0.1) }
        val moved = frame(r).toList()
        assertTrue(moved != first)
        repeat(30) { r.advance(0.1) }
        assertEquals(moved, frame(r).toList())
        inputs.gravityX = 0f
        repeat(5) { r.advance(0.1) }
        assertEquals(moved, frame(r).toList())
        inputs.gravityY = -5f
        repeat(20) { r.advance(0.1) }
        assertTrue(frame(r).toList() != moved)
    }

    /** #19：秒リングは秒の変わり目でだけ進み、1分でちょうど一周する（(4a) Pro は28マス、ほぼ2秒に1マス）。 */
    @Test
    fun clockRingStepsOnSecondBoundaries() {
        val inputs = Inputs(epochMillis = at(10), zone = zone)
        val r = runner("clock", inputs)
        val center = g13.idx(6, 6)
        fun ringLit() = frame(r).let { f -> f.indices.count { it != center && f[it] > 0f && f[it] < 0.99f } }
        val counts = (0 until 60).map { s ->
            inputs.epochMillis = at(10) + s * 1000L + 100
            val a = ringLit()
            inputs.epochMillis = at(10) + s * 1000L + 900
            assertEquals("${s}s", a, ringLit()) // 1秒の途中では変わらない
            a
        }
        assertEquals(1, counts.first())
        assertTrue(counts.zipWithNext().all { (a, b) -> b - a in 0..1 })
        // 28マスを60秒に均等に：進む間隔は2秒か3秒
        val steps = counts.indices.filter { it > 0 && counts[it] != counts[it - 1] }
        assertTrue(steps.zipWithNext().all { (a, b) -> b - a in 2..3 })
    }

    /**
     * AQUARIUM：魚の体の真上にエサが落ちてきても、左右を向き続けない。
     * 1匹のとき、魚は右を向いて始まる（(4a) Pro は鼻先 x=8・体は x=4〜8、Phone (3) は鼻先 x=17・体は x=8〜17）。
     */
    @Test
    fun fishDoesNotThrashUnderFood() {
        for (geo in listOf(g13, Geo.PHONE_3)) for (x in (if (geo.big) listOf(10.0, 12.0, 14.0, 16.0) else listOf(5.0, 6.0, 7.0))) {
            val r = runner("aquarium", Inputs(), mapOf("fish" to 1f), geo)
            val toy = r.toy as AquariumToy
            toy.dropFood(x)
            fun foodCells() = frame(r).count { it in 0.69f..0.71f }
            var sec = 0.0
            while (foodCells() > 0 && sec < 10) {
                r.advance(0.1)
                sec += 0.1
            }
            assertEquals("n=${geo.n} x=$x", 0, foodCells()) // 食べた
            assertTrue("n=${geo.n} x=$x turns=${toy.turns}", toy.turns <= 1)
        }
    }

    /**
     * GLOBE：ゆっくり回して1周（約9.6fps）見ても、LED が2コマ以内に点いて戻る（または消えて戻る）ことがほとんどない。
     * 以前の点で拾う描き方では、Phone (3) で1周に約330回、(4a) Pro で約90回あった（島がもぞもぞ動いて見えた）。
     */
    @Test
    fun globeSpinsWithoutFlicker() {
        for ((geo, limit) in listOf(g13 to 0, Geo.PHONE_3 to 5)) {
            val r = runner("globe", Inputs(), mapOf("spin" to 1f), geo)
            val lastFlip = IntArray(geo.size) { -100 }
            var prev = frame(r)
            var bounces = 0
            for (t in 1..576) {
                r.advance(1 / 9.6)
                val cur = frame(r)
                for (i in cur.indices) {
                    if ((cur[i] > 0.5f) == (prev[i] > 0.5f)) continue
                    if (t - lastFlip[i] <= 2) bounces++
                    lastFlip[i] = t
                }
                prev = cur
            }
            assertTrue("n=${geo.n} bounces=$bounces", bounces <= limit)
        }
    }
}
