package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext

/**
 * MAZE：座標が奇数のマスを部屋とし、穴掘り法（再帰的バックトラック）で迷路を作る。
 * 解き方はランダムな順番の深さ優先探索。行き止まりから戻る動きも1歩として見せる。
 */
class MazeToy(ctx: ToyContext) : Toy(ctx) {
    private var open = BooleanArray(0)

    /** 掘った順番のマス。 */
    private var seq = IntArray(0)

    /** 探索でたどる順番のマス（戻る動きを含む）。 */
    private var path = IntArray(0)
    private var trail = BooleanArray(0)
    private var gi = 0
    private var si = 0
    private var goal = 0
    private var endedAt = -1.0

    override val stepsPerSecond: Double
        get() = bySpeed(5.0, 10.0, 20.0) * (if (geo.big) 2.0 else 1.0)

    override fun init() {
        val c = geo.c.toInt()
        val sx = 1
        val sy = c - 1
        val gx = n - 2
        val gy = c + 1
        open = BooleanArray(geo.size)
        val order = ArrayList<Int>()
        val vis = BooleanArray(geo.size)
        val stack = ArrayList<Int>()
        val s0 = geo.idx(sx, sy)
        vis[s0] = true
        open[s0] = true
        order.add(s0)
        stack.add(s0)
        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val x = cur % n
            val y = cur / n
            val dirs = D4.copyOf().also { it.shuffle(random) }
            val nb = dirs.firstOrNull { d ->
                val rx = x + d[0] * 2
                val ry = y + d[1] * 2
                geo.inside(rx, ry) && geo.inside(x + d[0], y + d[1]) && !vis[geo.idx(rx, ry)]
            }
            if (nb == null) {
                stack.removeAt(stack.size - 1)
                continue
            }
            val room = geo.idx(x + nb[0] * 2, y + nb[1] * 2)
            val wall = geo.idx(x + nb[0], y + nb[1])
            vis[room] = true
            open[wall] = true
            open[room] = true
            order.add(wall)
            order.add(room)
            stack.add(room)
        }
        seq = order.toIntArray()

        goal = geo.idx(gx, gy)
        val walk = ArrayList<Int>()
        val seen = BooleanArray(geo.size)
        visit(sx, sy, walk, seen)
        path = walk.toIntArray()

        trail = BooleanArray(geo.size)
        gi = if (params.int("gen") == 1) seq.size else 1
        si = 0
        endedAt = -1.0
    }

    private fun visit(x: Int, y: Int, walk: ArrayList<Int>, seen: BooleanArray): Boolean {
        val i = geo.idx(x, y)
        walk.add(i)
        seen[i] = true
        if (i == goal) return true
        val dirs = D4.copyOf().also { it.shuffle(random) }
        for (d in dirs) {
            val nx = x + d[0]
            val ny = y + d[1]
            if (geo.inside(nx, ny) && open[geo.idx(nx, ny)] && !seen[geo.idx(nx, ny)]) {
                if (visit(nx, ny, walk, seen)) return true
                walk.add(i)
            }
        }
        return false
    }

    override fun step() {
        if (endedAt >= 0) {
            if (time - endedAt >= END_SEC) {
                resets++
                init()
            }
            return
        }
        if (gi < seq.size) {
            gi = minOf(seq.size, gi + if (geo.big) 4 else 2)
            return
        }
        if (si < path.size - 1) {
            si++
            trail[path[si]] = true
            return
        }
        endedAt = time
    }

    override fun draw(out: FloatArray) {
        val shown = BooleanArray(geo.size)
        for (k in 0 until gi) shown[seq[k]] = true
        for (i in 0 until geo.size) {
            if (!geo.inside(i)) continue
            // 壁は PONG WARS の暗い陣地と同じ明るさ、たどった道は主な帯（LedMapper）
            out[i] = if (shown[i]) (if (trail[i]) 0.5f else 0f) else 0.14f
        }
        if (gi >= seq.size) {
            out[goal] = if (odd(time, 3.0)) 0.8f else 0.14f
            out[path[si]] = if (endedAt >= 0 && odd(time, 6.0)) 0.3f else 1f
        } else {
            out[seq[gi - 1]] = 1f
        }
    }

    private companion object {
        const val END_SEC = 1.6
    }
}
