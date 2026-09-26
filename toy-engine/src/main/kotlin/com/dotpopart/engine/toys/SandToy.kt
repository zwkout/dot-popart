package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * SAND：流れ落ちる砂。重力の向きは端末面に沿った成分から求め、8方向に丸める。
 * 「今日の残り時間」では、毎分その割合まで砂を減らし、0時には上から降り注いで満杯に戻る。
 *
 * サラサラ流れて見えるよう、次のようにしている（実機で見て調整）。
 * - 粒ごとに、動くかどうかを確率 [MOVE_CHANCE] で決める。全部の粒がそろって1マス動くと、塊のまま落ちて見える。
 * - ステップを細かくする（10 → 20回/秒）。1粒あたりの落ちる速さはほぼ同じ。
 * - 真下も斜め下もふさがっているときは、ときどき [SPILL_CHANCE] で真横へこぼれる。山が崩れて平らに近づく。
 */
class SandToy(ctx: ToyContext) : Toy(ctx) {
    private var g = BooleanArray(0)

    /** 最後に使った重力の向き（単位ベクトル）。水平に伏せていて傾きが小さいときはこれを使い続ける。 */
    private var dirX = 0.0
    private var dirY = 1.0
    private var lastMinute = Long.MIN_VALUE
    private var target = 0

    override val stepsPerSecond: Double
        get() = 20.0 * (if (geo.big) 1.5 else 1.0)

    override fun init() {
        val frac = if (remainingMode) remainingFraction() else params["amount"] / 100.0
        val count = (geo.ledCount * frac).roundToInt()
        target = count
        g = BooleanArray(geo.size)
        // 下の行から詰める（同じ行の中はランダム）
        val order = (0 until geo.size).filter { geo.inside(it) }
            .map { it to random.nextDouble() }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.first / n }.thenBy { it.second })
        for (k in 0 until count) g[order[k].first] = true
        lastMinute = inputs.epochMillis / 60_000
    }

    private val remainingMode: Boolean get() = params.int("mode") == 1

    private fun remainingFraction(): Double {
        val t = inputs.local().toLocalTime()
        return 1 - t.toSecondOfDay() / 86400.0
    }

    override fun step() {
        // プレビューでも実機の傾きを使う
        val mx = inputs.gravityX.toDouble()
        val my = inputs.gravityY.toDouble()
        val mag = hypot(mx, my)
        if (mag >= MIN_TILT) {
            dirX = mx / mag
            dirY = my / mag
        }
        val gx = dirX
        val gy = dirY
        if (remainingMode) updateAmount(gx, gy)

        val k = jr(atan2(gy, gx) / (PI / 4))
        val main = dirOf(k)
        val left = dirOf(k - 1)
        val right = dirOf(k + 1)
        val sideA = dirOf(k - 2)
        val sideB = dirOf(k + 2)
        // 重力の向きで最も先にある粒から順に動かす（同じ高さの中の順番は毎回ばらばらに）
        val grains = (0 until geo.size).filter { g[it] }
            .map { it to (it % n) * gx + (it / n) * gy + random.nextDouble() * 0.5 }
            .sortedByDescending { it.second }
        for ((i, _) in grains) {
            if (random.nextDouble() >= MOVE_CHANCE) continue
            val x = i % n
            val y = i / n
            val diag = if (random.nextBoolean()) arrayOf(left, right) else arrayOf(right, left)
            val moved = tryMove(i, x, y, main) || tryMove(i, x, y, diag[0]) || tryMove(i, x, y, diag[1])
            if (!moved && random.nextDouble() < SPILL_CHANCE) {
                val side = if (random.nextBoolean()) sideA else sideB
                tryMove(i, x, y, side)
            }
        }
    }

    /** 毎分、残り時間の割合に合わせる。減らすときは表面の粒を消し、増やすときは1ステップ1粒ずつ上から注ぐ。 */
    private fun updateAmount(gx: Double, gy: Double) {
        val minute = inputs.epochMillis / 60_000
        if (minute != lastMinute) {
            lastMinute = minute
            target = (geo.ledCount * remainingFraction()).roundToInt()
        }
        var count = g.count { it }
        if (count > target) {
            val top = (0 until geo.size).filter { g[it] }.sortedBy { (it % n) * gx + (it / n) * gy }
            for (i in top) {
                if (count <= target) break
                g[i] = false
                count--
            }
        } else if (count < target) {
            val empty = (0 until geo.size).filter { geo.inside(it) && !g[it] }
            val minProj = empty.minOf { (it % n) * gx + (it / n) * gy }
            val candidates = empty.filter { (it % n) * gx + (it / n) * gy <= minProj + 1.0 }
            g[candidates[rnd(candidates.size)]] = true
        }
    }

    private fun tryMove(i: Int, x: Int, y: Int, d: IntArray): Boolean {
        val nx = x + d[0]
        val ny = y + d[1]
        if (!geo.inside(nx, ny) || g[geo.idx(nx, ny)]) return false
        g[i] = false
        g[geo.idx(nx, ny)] = true
        return true
    }

    private fun dirOf(k: Int): IntArray {
        val an = k * PI / 4
        return intArrayOf(jr(cos(an)), jr(sin(an)))
    }

    override fun draw(out: FloatArray) {
        for (i in 0 until geo.size) {
            if (g[i]) out[i] = (0.72 + ((i * 37) % 7) * 0.04).toFloat()
        }
    }

    private companion object {
        /** これより傾きが小さい（m/s²）ときは、前の向きを使い続ける。約9°。 */
        const val MIN_TILT = 1.5
        const val MOVE_CHANCE = 0.55
        const val SPILL_CHANCE = 0.12
    }
}
