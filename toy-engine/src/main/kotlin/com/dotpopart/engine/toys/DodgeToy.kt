package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * DODGE：傾けて車をよける縦スクロールのレースゲーム。
 * 左右の傾きでレーンを直接決める。しばらく傾けずにいる（伏せて置いたままなど）と、
 * 前方を見て空いているレーンへ移る「自動運転」に切り替わる（置いたままでも眺められるように）。
 */
class DodgeToy(ctx: ToyContext) : Toy(ctx) {
    private class Car(val lane: Int, var y: Int)

    private lateinit var lanes: IntArray
    private var cw = 0
    private var ch = 0
    private var py = 0
    private var ph = 0
    private lateinit var sprite: Array<IntArray>

    private val cars = ArrayList<Car>()
    private var lane = 1
    private var px = 0
    private var gap = 2
    private var free = 1
    private var crash = 0
    private var t = 0

    /** 傾きで選んでいるレーン（ヒステリシス付き）。 */
    private var tiltLane = 1

    /** 傾けずにいる時間（秒）。[IDLE_SEC] を超えたら自動運転。始めは自動運転にしておく。 */
    private var idleSec = IDLE_SEC

    private val crashSteps: Int get() = if (geo.big) 14 else 10

    override val stepsPerSecond: Double
        get() = bySpeed(6.0, 9.0, 13.0) * (if (geo.big) 1.7 else 1.0)

    override fun init() {
        val c = geo.c.toInt()
        if (geo.big) {
            lanes = intArrayOf(c - 7, c, c + 7)
            cw = 2; ch = 4; py = n - 8; ph = 4
            sprite = arrayOf(
                intArrayOf(0, 0), intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1),
                intArrayOf(-2, 2), intArrayOf(-1, 2), intArrayOf(0, 2), intArrayOf(1, 2), intArrayOf(2, 2),
                intArrayOf(-1, 3), intArrayOf(1, 3),
            )
        } else {
            lanes = intArrayOf(3, 6, 9)
            cw = 1; ch = 2; py = n - 3; ph = 2
            sprite = arrayOf(intArrayOf(0, 0), intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1))
        }
        cars.clear()
        lane = 1
        px = lanes[1]
        gap = 2
        free = 1
        crash = 0
        t = 0
    }

    override fun step() {
        readTilt()
        if (crash > 0) {
            if (--crash == 0) {
                resets++
                init()
            }
            return
        }
        t++
        for (car in cars) car.y++
        cars.removeAll { it.y >= n + 1 }
        // 新しい車の列。必ず1レーンは空け、空くレーンは前の列から隣までしか動かさない
        if (--gap <= 0) {
            free = (free + rnd(3) - 1).coerceIn(0, 2)
            val open = (0..2).filter { it != free }.shuffled(random)
            val two = random.nextDouble() < TWO_CARS
            for (l in open.take(if (two) 2 else 1)) cars.add(Car(l, -ch))
            gap = (if (geo.big) 13 else 7) + rnd(if (geo.big) 8 else 4) + (if (geo.big) 4 else 2)
        }
        if (idleSec >= IDLE_SEC) autopilot() else lane = tiltLane
        val tx = lanes[lane]
        val mv = if (geo.big) 2 else 1
        px = if (px < tx) min(tx, px + mv) else if (px > tx) max(tx, px - mv) else px
        val hit = cars.any { car ->
            val lx = lanes[car.lane]
            sprite.any { q ->
                val x = px + q[0]
                val y = py + q[1]
                x >= lx - cw && x <= lx + cw && y >= car.y && y < car.y + ch
            }
        }
        if (hit) crash = crashSteps
    }

    /** 左右の傾きでレーンを決める。レーンの境目ではヒステリシスを持たせて、ふらつかないようにする。 */
    private fun readTilt() {
        val gx = inputs.gravityX.toDouble()
        tiltLane = when (tiltLane) {
            0 -> if (gx > -(TILT - HYST)) (if (gx > TILT) 2 else 1) else 0
            2 -> if (gx < TILT - HYST) (if (gx < -TILT) 0 else 1) else 2
            else -> if (gx < -TILT) 0 else if (gx > TILT) 2 else 1
        }
        idleSec = if (abs(gx) >= TILT) 0.0 else min(IDLE_SEC, idleSec + 1.0 / stepsPerSecond)
    }

    /** 自動運転。ときどき判断を誤ってぶつかる。 */
    private fun autopilot() {
        val look = if (geo.big) 14 else 8
        fun danger(l: Int) = cars.any { it.lane == l && it.y + ch > py - look && it.y < py + ph }
        if (random.nextDouble() > 0.025) {
            if (danger(lane)) {
                val opts = (0..2).filter { !danger(it) }.sortedBy { abs(it - lane) }
                if (opts.isNotEmpty()) lane = opts[0]
            } else if (random.nextDouble() < 0.02) {
                val safe = (0..2).filter { !danger(it) }
                if (safe.isNotEmpty()) lane = safe[rnd(safe.size)]
            }
        }
    }

    override fun draw(out: FloatArray) {
        val fade = if (crash > 0) 0.35 else 1.0
        // 道路の端（流れる破線でスピード感を出す）
        for (ex in intArrayOf(1, n - 2)) for (y in 0 until n) if ((y + t) % 3 == 0) put(out, ex, y, 0.18 * fade)
        for (car in cars) {
            val lx = lanes[car.lane]
            for (y in car.y until car.y + ch) for (x in lx - cw..lx + cw) {
                put(out, x, y, (if (y == car.y + ch - 1) 0.6 else 0.45) * fade)
            }
        }
        val blink = crash > 0 && odd(time, 8.0)
        for (q in sprite) put(out, px + q[0], py + q[1], if (blink) 0.2 else 1.0)
        if (crash > 0) {
            // ぶつかった位置から輪が広がる
            val r = (crashSteps - crash) * (if (geo.big) 1.3 else 0.8)
            for (y in 0 until n) for (x in 0 until n) {
                if (!geo.inside(x, y)) continue
                val d = sqrt(((x - px) * (x - px) + (y - py) * (y - py)).toDouble())
                if (abs(d - r) < 0.7) put(out, x, y, 0.9)
            }
        }
    }

    private companion object {
        /** レーンを切り替える傾き（m/s²）。約15°。 */
        const val TILT = 2.5
        const val HYST = 0.8

        /** これだけ傾けずにいたら自動運転にする（秒）。 */
        const val IDLE_SEC = 5.0

        /** 車の多さ「普通」で、2台並ぶ確率。 */
        const val TWO_CARS = 0.25
    }
}
