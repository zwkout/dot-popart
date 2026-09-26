package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** AQUARIUM：通知が来るとエサが落ちてくる水槽。 */
class AquariumToy(ctx: ToyContext) : Toy(ctx) {
    private class Fish(var x: Double, val y: Int, var dir: Int, val sp: Double, val v: Double, var happyUntil: Double = -1.0, var turnedAt: Double = -99.0)
    private class Food(val x: Double, var y: Double, var rest: Double = 0.0)
    private class Bubble(val x: Int, var y: Double)

    private val fish = ArrayList<Fish>()
    private val food = ArrayList<Food>()
    private val bubbles = ArrayList<Bubble>()
    private var next = 0
    private var seenNotifications = 0

    /** 振り返った回数（テスト用）。 */
    internal var turns = 0
        private set


    private val sprite: FishSprite get() = if (geo.big) FishSprite.LARGE else FishSprite.SMALL

    override val stepsPerSecond: Double
        get() = 10.0 * (if (geo.big) 1.4 else 1.0)

    override fun init() {
        val count = params.int("fish")
        val lanes = if (geo.big) BIG_LANES[count - 1] else SMALL_LANES[count - 1]
        val xs = if (geo.big) intArrayOf(17, 8, 15) else intArrayOf(8, 4, 7)
        fish.clear()
        lanes.forEachIndexed { k, y ->
            val dir = if (k % 2 == 1) -1 else 1
            val sp = (0.1 + random.nextDouble() * 0.1) * (if (geo.big) 1.5 else 1.0)
            fish.add(Fish(xs[k].toDouble(), y, dir, sp, FISH_BRIGHTNESS[k]))
        }
        food.clear()
        bubbles.clear()
        next = 40 + rnd(40)
        seenNotifications = inputs.notifications
    }

    /** エサを落とす（通知1件ぶん）。 */
    override fun act() {
        val cnt = if (params.int("food") == 1) 3 else 1
        for (k in 0 until cnt) food.add(Food((3 + rnd(n - 6)).toDouble(), 0.5 - k * 1.8))
    }

    /** 決まった位置にエサを1粒落とす（テスト用）。 */
    internal fun dropFood(x: Double) {
        food.add(Food(x, 0.5))
    }

    override fun step() {
        val sp = sprite
        val len = sp.len
        if (demo) {
            if (--next <= 0) {
                act()
                next = 90 + rnd(90)
            }
        } else {
            // 通知が増えた分だけエサを落とす（一度に落としすぎない）
            val fresh = (inputs.notifications - seenNotifications).coerceIn(0, 5)
            seenNotifications = inputs.notifications
            repeat(fresh) { act() }
        }
        val dt = 1.0 / stepsPerSecond
        for (f in food) {
            if (f.y < 1 || geo.inside(jr(f.x), jr(f.y + 1))) f.y += 0.13 else f.rest += dt
        }
        food.removeAll { it.rest >= FOOD_REST_SEC }

        for (f in fish) {
            val rx = jr(f.x)
            var target: Food? = null
            var best = 99.0
            for (fd in food) {
                if (fd.y > f.y + sp.half + 0.5 || fd.y < 0) continue
                val d = abs(fd.x - f.x)
                if (d < best) {
                    best = d
                    target = fd
                }
            }
            if (target != null) {
                // 向きを変えると、鼻先が尾の位置へ移る。体の真上のエサで振り返ると、またエサが後ろになって
                // 左右を向き続けてしまう。そこで、エサが尾の位置より後ろになってから振り返る。
                // 体の真上なら、そのまま前へ泳いで追い越す。鼻先のすぐそば（1.2 マス以内）なら、止まって待つ。
                // 念のため、振り返ったあと1秒は振り返らない
                if ((target.x - rx) * f.dir <= -len + 0.01 && time - f.turnedAt >= TURN_COOLDOWN_SEC && fits(rx - len * f.dir, f.y, -f.dir)) {
                    turn(f, rx - len * f.dir)
                }
                val ahead = (target.x - f.x) * f.dir
                val nx = f.x + f.dir * 0.28
                if (ahead > 0.3 || ahead < -1.2) {
                    if (fits(jr(nx), f.y, f.dir)) {
                        f.x = nx
                    } else if (ahead < -1.2 && time - f.turnedAt >= TURN_COOLDOWN_SEC && fits(rx - len * f.dir, f.y, -f.dir)) {
                        // 壁で前へ進めず追い越せないときは、反対へ向いてから追い越す
                        turn(f, rx - len * f.dir)
                    }
                }
                if (abs(target.x - f.x) <= 1.2 && abs(target.y - f.y) <= sp.half + 0.2) {
                    food.remove(target)
                    f.happyUntil = time + HAPPY_SEC
                }
            } else {
                val nx2 = f.x + f.dir * f.sp
                if (!fits(jr(nx2 + f.dir), f.y, f.dir) || random.nextDouble() < 0.004) {
                    if (fits(rx - len * f.dir, f.y, -f.dir)) turn(f, rx - len * f.dir)
                } else {
                    f.x = nx2
                }
            }
            if (random.nextDouble() < 0.01) bubbles.add(Bubble(jr(f.x) + f.dir, (f.y - sp.half).toDouble()))
        }
        for (b in bubbles) b.y -= 0.25
        bubbles.removeAll { !geo.inside(it.x, jr(it.y)) }
    }

    /** 向きを変える。鼻先は尾があった位置 [nose] に移る。 */
    private fun turn(f: Fish, nose: Int) {
        f.x = nose.toDouble()
        f.dir = -f.dir
        f.turnedAt = time
        turns++
    }

    /** 鼻先を (x, y) に置いたとき、魚全体が円の中に収まるか。 */
    private fun fits(x: Int, y: Int, dir: Int): Boolean =
        sprite.cells.all { c -> geo.inside(x + c.dx * dir, y + c.dy) }

    override fun draw(out: FloatArray) {
        val sp = sprite
        // 水草（ゆらゆら揺れる）
        val weeds = if (geo.big) arrayOf(intArrayOf(8, 4), intArrayOf(11, 3), intArrayOf(17, 5)) else arrayOf(intArrayOf(4, 2), intArrayOf(9, 2))
        for (w in weeds) {
            for (h in 0 until w[1]) {
                val y = n - 1 - h
                val x = w[0] + if (h > 0 && sin(time * 1.5 + w[0] + h) > 0.3) 1 else 0
                put(out, x, y, 0.16)
                if (!geo.inside(w[0], n - 1) && h == 0) put(out, x, y - 1, 0.16)
            }
        }
        for (b in bubbles) put(out, b.x, jr(b.y), 0.3)
        for (f in food) put(out, jr(f.x), jr(f.y), 0.7)
        val wig = odd(time, 4.0)
        for (f in fish) {
            val x = jr(f.x)
            // エサを食べたら少しの間点滅する
            val k = if (time < f.happyUntil && odd(time, 10.0)) 0.45 else f.v
            for (c in sp.cells) {
                var dy = c.dy
                if (c.tail && wig) dy = Integer.signum(dy) * floor(abs(dy) / 2.0).toInt()
                put(out, x + c.dx * f.dir, f.y + dy, c.v * k)
            }
        }
    }

    private companion object {
        val SMALL_LANES = arrayOf(intArrayOf(6), intArrayOf(3, 8), intArrayOf(2, 6, 10))
        val BIG_LANES = arrayOf(intArrayOf(12), intArrayOf(7, 17), intArrayOf(5, 12, 19))
        val FISH_BRIGHTNESS = doubleArrayOf(1.0, 0.8, 0.62)
        const val HAPPY_SEC = 0.6
        const val FOOD_REST_SEC = 4.0
        const val TURN_COOLDOWN_SEC = 1.0
    }
}

/** 魚のドット絵。鼻先 (0, 0) を基準にした右向きの形。目は「点灯しない1マス」として空けてある。 */
internal class FishSprite(val len: Int, val half: Int, val cells: List<Cell>) {
    class Cell(val dx: Int, val dy: Int, val v: Double, val tail: Boolean = false)

    companion object {
        /**
         * 尾びれの明るさ（体に対する割合）。0.55 だと、いちばん暗い3匹目（0.62）の尾びれが
         * 薄い帯に落ちて、暗くしたときに消えるので 0.75 にしている（LedMapper）。
         */
        private const val TAIL = 0.75

        val SMALL = FishSprite(
            4, 1,
            listOf(
                Cell(0, 0, 1.0), Cell(-1, -1, 1.0), Cell(-1, 1, 1.0), Cell(-2, -1, 1.0), Cell(-2, 0, 1.0), Cell(-2, 1, 1.0),
                Cell(-3, 0, TAIL, true), Cell(-4, -1, TAIL, true), Cell(-4, 1, TAIL, true),
            ),
        )
        val LARGE = FishSprite(
            9, 2,
            buildList {
                for (x in intArrayOf(-5, -4, -3, -1)) add(Cell(x, -1, 1.0))
                for (x in -6..0) add(Cell(x, 0, 1.0))
                for (x in -5..-1) add(Cell(x, 1, 1.0))
                add(Cell(-4, -2, 0.7)); add(Cell(-3, -2, 0.7)); add(Cell(-4, 2, 0.5))
                add(Cell(-7, 0, TAIL, true)); add(Cell(-8, -1, TAIL, true)); add(Cell(-8, 1, TAIL, true))
                add(Cell(-9, -2, TAIL, true)); add(Cell(-9, 2, TAIL, true))
            },
        )
    }
}
