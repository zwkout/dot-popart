package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** PONG WARS：明と暗が陣地を塗り合うブロック崩し。 */
class PongToy(ctx: ToyContext) : Toy(ctx) {
    private class Ball(val team: Int, var x: Double, var y: Double, var vx: Double, var vy: Double)

    /** マスの陣営（1：明、2：暗、0：円の外）。 */
    private var g = IntArray(0)
    private val balls = ArrayList<Ball>()

    override val stepsPerSecond: Double
        get() = bySpeed(12.0, 20.0, 30.0) * (if (geo.big) 1.6 else 1.0)

    override fun init() {
        val c = geo.c
        g = IntArray(geo.size)
        for (y in 0 until n) for (x in 0 until n) {
            if (geo.inside(x, y)) g[geo.idx(x, y)] = if (x < c || (x.toDouble() == c && y < c)) LIGHT else DARK
        }
        balls.clear()
        for (team in intArrayOf(LIGHT, DARK)) {
            val own = ArrayList<Int>()
            for (y in 0 until n) for (x in 0 until n) {
                if (!geo.inside(x, y)) continue
                val ok = if (team == LIGHT) x >= 1 && x <= c - 2 else x >= c + 2 && x <= n - 2
                if (ok) own.add(geo.idx(x, y))
            }
            repeat(params.int("balls")) {
                val cell = own[rnd(own.size)]
                val a = random.nextDouble() * PI * 2
                balls.add(Ball(team, (cell % n).toDouble(), (cell / n).toDouble(), cos(a) * SPEED, sin(a) * SPEED))
            }
        }
    }

    override fun step() {
        for (b in balls) {
            var cx = jr(b.x + b.vx)
            var cy = jr(b.y)
            if (!geo.inside(cx, cy)) {
                b.vx = -b.vx
            } else if (g[geo.idx(cx, cy)] != b.team) {
                g[geo.idx(cx, cy)] = b.team
                b.vx = -b.vx
            }
            cx = jr(b.x)
            cy = jr(b.y + b.vy)
            if (!geo.inside(cx, cy)) {
                b.vy = -b.vy
            } else if (g[geo.idx(cx, cy)] != b.team) {
                g[geo.idx(cx, cy)] = b.team
                b.vy = -b.vy
            }
            val nx = b.x + b.vx
            val ny = b.y + b.vy
            if (!geo.inside(jr(nx), jr(ny))) {
                b.vx = -b.vx
                b.vy = -b.vy
            } else {
                b.x = nx
                b.y = ny
                val i = geo.idx(jr(nx), jr(ny))
                if (g[i] != b.team) g[i] = b.team
            }
            // 同じ軌道を回り続けないよう、向きを ±0.03rad 揺らす
            val a = atan2(b.vy, b.vx) + (random.nextDouble() - 0.5) * 0.06
            b.vx = cos(a) * SPEED
            b.vy = sin(a) * SPEED
        }
    }

    override fun draw(out: FloatArray) {
        for (i in 0 until geo.size) {
            if (geo.inside(i)) out[i] = if (g[i] == LIGHT) 0.85f else 0.14f
        }
        for (b in balls) {
            // 明のボールは明るい地に空いた穴に、暗のボールは暗い地に光る点に見える
            set(out, jr(b.x), jr(b.y), if (b.team == LIGHT) 0.0 else 1.0)
        }
    }

    private companion object {
        const val LIGHT = 1
        const val DARK = 2
        const val SPEED = 0.42
    }
}
