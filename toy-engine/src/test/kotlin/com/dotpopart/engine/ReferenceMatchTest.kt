package com.dotpopart.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * デザイン段階の参照実装（JavaScript。リポジトリには含めない）と、乱数を使わないおもちゃの描画を1マスずつ比べる。
 * CLOCK は秒リングを除き、点灯する場所だけを比べる。
 * 期待値は tools/reference/dump_reference.js が Node.js で書き出したもの。
 */
class ReferenceMatchTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun clockAndBatteryMatchPrototype() {
        val json = javaClass.getResource("/reference.json")!!.readText()
        val local = LocalDateTime.parse(Regex("\"fixedLocal\":\"([^\"]+)\"").find(json)!!.groupValues[1])
        val epoch = local.atZone(zone).toInstant().toEpochMilli()
        val caseRe = Regex(
            "\\{\"id\":\"(\\w+)\",\"n\":(\\d+),\"params\":\\{([^}]*)},\"time\":([\\d.]+),\"level\":(\\d+),\"frame\":\\[([^\\]]*)]}",
        )
        val cases = caseRe.findAll(json).toList()
        assertEquals(12, cases.size)
        for (m in cases) {
            val (id, n, paramsText, time, level, frameText) = m.destructured
            val params = paramsText.split(',').associate { kv ->
                val (k, v) = kv.split(':')
                k.trim('"') to v.toFloat()
            }
            val expected = frameText.split(',').map { it.toFloat().coerceIn(0f, 1f) }
            val geo = Geo.of(n.toInt())
            val def = ToyCatalog[id]!!
            val inputs = Inputs(epochMillis = epoch, zone = zone, batteryLevel = level.toInt())
            val runner = ToyRunner(def, geo, def.params(params), inputs, Random(1))
            runner.toy.time = time.toDouble()
            val out = FloatArray(geo.size)
            runner.draw(out)
            for (i in out.indices) {
                if (!geo.inside(i)) continue
                val where = "$id n=$n $params t=$time at (${i % geo.n}, ${i / geo.n})"
                if (id == "clock") {
                    // 秒リングは進み方を変えたので比べない。数字と中央の点は点灯する場所だけ比べる
                    // （中央の点の明るさは実機で見やすいよう変えた）
                    val dx = i % geo.n - geo.c
                    val dy = i / geo.n - geo.c
                    if (kotlin.math.sqrt(dx * dx + dy * dy) >= (if (geo.big) geo.r - 1.2 else 6.0)) continue
                    assertEquals(where, expected[i] > 0f, out[i] > 0f)
                } else {
                    assertEquals(where, expected[i], out[i], 1e-5f)
                }
            }
        }
    }

    @Test
    fun everyToyLightsSomething() {
        for (def in ToyCatalog.all) for (geo in listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)) {
            val runner = ToyRunner(def, geo, def.params(), Inputs(charging = true, batteryLevel = 50), Random(5))
            val out = FloatArray(geo.size)
            var lit = 0
            repeat(100) {
                runner.advance(0.1)
                runner.draw(out)
                lit = maxOf(lit, out.count { it > 0.05f })
            }
            assertTrue("${def.id} n=${geo.n} lit=$lit", lit >= 3)
        }
    }
}
