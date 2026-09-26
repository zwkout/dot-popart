package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext

/** LIFE：コンウェイのライフゲーム（B3/S23）。円の外は常に「死」。 */
class LifeToy(ctx: ToyContext) : Toy(ctx) {
    private var g = BooleanArray(0)
    private val hist = ArrayDeque<BooleanArray>()
    private var generation = 0

    /** 全滅・繰り返しを見つけた時刻（まだなら負）。[END_SEC] 秒暗く見せてから作り直す。 */
    private var endedAt = -1.0

    override val stepsPerSecond: Double
        get() = bySpeed(2.0, 5.0, 10.0)

    override fun init() {
        val density = params["density"] / 100.0
        g = BooleanArray(geo.size) { i -> geo.inside(i) && random.nextDouble() < density }
        hist.clear()
        generation = 0
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
        val next = BooleanArray(geo.size)
        var alive = 0
        for (i in 0 until geo.size) {
            if (!geo.inside(i)) continue
            val x = i % n
            val y = i / n
            var k = 0
            for (d in D8) {
                val nx = x + d[0]
                val ny = y + d[1]
                if (geo.inside(nx, ny) && g[geo.idx(nx, ny)]) k++
            }
            val v = if (g[i]) k == 2 || k == 3 else k == 3
            next[i] = v
            if (v) alive++
        }
        generation++
        // 直近16世代のどれかと同じなら、止まったか繰り返しに入っている。
        // 念のため、とても長く続いたときも作り直す（安全策）。
        if (alive == 0 || hist.any { it.contentEquals(next) } || generation >= MAX_GENERATIONS) endedAt = time
        hist.addLast(next)
        if (hist.size > 16) hist.removeFirst()
        g = next
    }

    override fun draw(out: FloatArray) {
        val v = if (endedAt >= 0) 0.35f else 1f
        for (i in 0 until geo.size) if (g[i]) out[i] = v
    }

    private companion object {
        const val END_SEC = 0.6
        const val MAX_GENERATIONS = 3000
    }
}
