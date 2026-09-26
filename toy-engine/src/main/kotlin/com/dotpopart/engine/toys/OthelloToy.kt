package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext

/** OTHELLO：丸い盤面でランダムオセロ。 */
class OthelloToy(ctx: ToyContext) : Toy(ctx) {
    private var b = IntArray(0)
    private var turn = 1
    private var pass = 0
    private var last = -1
    private var winner = 0

    /** 終局した時刻（まだなら負）。勝った側の石を [END_SEC] 秒点滅させてから最初に戻る。 */
    private var endedAt = -1.0

    override val stepsPerSecond: Double
        get() = bySpeed(1.0, 2.0, 5.0) * (if (geo.big) 3.0 else 1.0)

    override fun init() {
        val c = geo.c.toInt()
        b = IntArray(geo.size)
        b[geo.idx(c, c)] = LIGHT
        b[geo.idx(c + 1, c + 1)] = LIGHT
        b[geo.idx(c + 1, c)] = DARK
        b[geo.idx(c, c + 1)] = DARK
        turn = LIGHT
        pass = 0
        last = -1
        winner = 0
        endedAt = -1.0
    }

    override fun step() {
        if (endedAt >= 0) {
            if (time - endedAt >= END_SEC) {
                resets++
                init()
            }
            return
        }
        val moves = ArrayList<Pair<Int, IntArray>>()
        for (i in 0 until geo.size) {
            if (!geo.inside(i) || b[i] != 0) continue
            val f = flips(i % n, i / n, turn)
            if (f.isNotEmpty()) moves.add(i to f)
        }
        if (moves.isEmpty()) {
            pass++
            turn = 3 - turn
            if (pass >= 2) {
                val light = b.count { it == LIGHT }
                val dark = b.count { it == DARK }
                winner = if (light == dark) 0 else if (light > dark) LIGHT else DARK
                endedAt = time
            }
            return
        }
        val m = if (params.int("style") == 1) moves.maxBy { it.second.size } else moves[rnd(moves.size)]
        b[m.first] = turn
        for (i in m.second) b[i] = turn
        last = m.first
        pass = 0
        turn = 3 - turn
    }

    /** (x, y) に打ったときに返せる石。8方向で、相手の石が続いた先に自分の石があれば挟める。 */
    private fun flips(x: Int, y: Int, pl: Int): IntArray {
        val op = 3 - pl
        val res = ArrayList<Int>()
        for (d in D8) {
            var cx = x + d[0]
            var cy = y + d[1]
            val line = ArrayList<Int>()
            while (geo.inside(cx, cy) && b[geo.idx(cx, cy)] == op) {
                line.add(geo.idx(cx, cy))
                cx += d[0]
                cy += d[1]
            }
            if (line.isNotEmpty() && geo.inside(cx, cy) && b[geo.idx(cx, cy)] == pl) res.addAll(line)
        }
        return res.toIntArray()
    }

    override fun draw(out: FloatArray) {
        val ended = endedAt >= 0
        for (i in 0 until geo.size) {
            if (!geo.inside(i)) continue
            val v = b[i]
            // 暗い石は、PONG WARS の暗い陣地と同じ明るさ（LedMapper）
            var value = if (v == LIGHT) 0.95 else if (v == DARK) 0.14 else 0.0
            if (ended && v == winner && odd(time, 4.0)) value = 0.08
            // 直前に打った石は点滅させる
            if (i == last && !ended && odd(time, 5.0)) value = if (v == LIGHT) 0.5 else 0.6
            out[i] = value.toFloat()
        }
    }

    private companion object {
        const val LIGHT = 1
        const val DARK = 2
        const val END_SEC = 1.2
    }
}

internal val D4 = arrayOf(intArrayOf(0, -1), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0))
internal val D8 = arrayOf(
    intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(1, -1), intArrayOf(-1, 0),
    intArrayOf(1, 0), intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1),
)
