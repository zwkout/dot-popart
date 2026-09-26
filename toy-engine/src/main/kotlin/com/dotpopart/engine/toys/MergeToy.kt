package com.dotpopart.engine.toys

import com.dotpopart.engine.Geo
import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * MERGE：2048 のルールを借りた、傾けると光が流れて混ざり合うおもちゃ。
 * すべての LED を1マスとして使い、数字は明るさ4段階（2・4・8・16）で表す。
 *
 * 操作：一定角度以上傾けたら、その向きに1回だけ寄せる。次に動かすには水平近くに戻す（ヒステリシス）。
 * 水平のまましばらく置いておく（伏せて置いたままなど）と「自動プレイ」に切り替わる（置いたままでも眺められるように）。
 * プレビューはいつも自動プレイ（手に持って画面を見ていると、端末が立っていて傾きで遊べないため）。
 */
class MergeToy(ctx: ToyContext) : Toy(ctx) {
    private class Trail(val from: Int, val dist: Int, val e: Int)
    private class Result(val b: IntArray, val moved: Boolean, val merges: Int, val trails: List<Trail>, val mergedTo: List<Int>)
    private class Anim(val res: Result, val dir: Int, val frames: Int, val spd: Int) { var f = 0 }

    private val board: Board get() = Board.of(geo)

    /** 各マスの数字（0：空き、1：2、2：4、3：8、4：16）。 */
    private var b = IntArray(0)
    private var fresh = ArrayList<Int>()
    private var freshT = 0
    private var end = 0
    private var wait = 0
    private var anim: Anim? = null
    private var pop: List<Int> = emptyList()
    private var popT = 0

    /** 傾けて1回寄せたあと、水平近くに戻るまで次を受け付けない。 */
    private var armed = true

    /** 水平のまま置かれている時間（秒）。[IDLE_SEC] を超えたら自動プレイ。始めは自動プレイにしておく。 */
    private var idleSec = IDLE_SEC

    override val stepsPerSecond: Double get() = 12.0

    override fun init() {
        b = IntArray(board.slots.size)
        fresh = ArrayList()
        freshT = 0
        end = 0
        wait = 6
        anim = null
        pop = emptyList()
        popT = 0
        repeat(max(2, spawnCount() * 2)) { spawn() }
    }

    override fun step() {
        val tilt = readTilt()
        if (end > 0) {
            if (--end == 0) {
                resets++
                init()
            }
            return
        }
        val a = anim
        if (a != null) {
            // 流れている途中の傾きは受け付けない
            if (tilt >= 0) armed = false
            if (++a.f >= a.frames) {
                // 流れ終わったら盤面を確定し、合体したタイルを弾ませ、新しいタイルを出す
                b = a.res.b
                pop = a.res.mergedTo
                popT = 3
                anim = null
                repeat(spawnCount()) { spawn() }
                wait = 6
                if (b.none { it == 0 } && (0..3).none { move(b, it).moved }) end = END_STEPS
            }
            return
        }
        if (popT > 0) popT--
        if (freshT > 0) freshT--
        if (wait > 0) wait--
        if (demo || idleSec >= IDLE_SEC) {
            if (wait > 0) return
            autoplay()
        } else if (tilt >= 0 && armed && wait == 0) {
            armed = false
            val res = move(b, tilt)
            if (res.moved) start(res, tilt)
        }
    }

    /**
     * 傾いている向き（0 上、1 右、2 下、3 左）。しきい値に届いていなければ -1。
     * 水平近くに戻ったら、次の1回を受け付ける。
     */
    private fun readTilt(): Int {
        if (demo) return -1
        val gx = inputs.gravityX.toDouble()
        val gy = inputs.gravityY.toDouble()
        val deg = SENS_DEG[params.int("sens").coerceIn(0, 2)]
        val on = G * sin(Math.toRadians(deg))
        val rest = G * sin(Math.toRadians(deg * REST_RATIO))
        val m = max(abs(gx), abs(gy))
        if (m < rest) armed = true
        idleSec = if (m < rest) min(IDLE_SEC, idleSec + 1.0 / stepsPerSecond) else if (m >= on) 0.0 else idleSec
        if (m < on) return -1
        return if (abs(gx) > abs(gy)) (if (gx > 0) 1 else 3) else (if (gy > 0) 2 else 0)
    }

    /** 自動プレイ：空きマスと合体の数が多い向きを選ぶ。 */
    private fun autoplay() {
        var best = -1
        var bestScore = -1e9
        for (d in 0..3) {
            val r = move(b, d)
            if (!r.moved) continue
            val empty = r.b.count { it == 0 }
            val sc = empty + r.merges * 1.5 + random.nextDouble() * 1.2 - (if (d == 0) 1.5 else 0.0)
            if (sc > bestScore) {
                bestScore = sc
                best = d
            }
        }
        if (best < 0) {
            end = END_STEPS
            return
        }
        start(move(b, best), best)
    }

    private fun start(res: Result, dir: Int) {
        val spd = if (geo.big) 2 else 1
        val far = res.trails.maxOfOrNull { it.dist } ?: 0
        fresh = ArrayList()
        anim = Anim(res, dir, max(1, ceil(far.toDouble() / spd).toInt()), spd)
    }

    /** 1手ごとに出す新しいタイルの数。マスが多い盤面ほど多く出して、1ゲームの長さをそろえる。 */
    private fun spawnCount(): Int = max(1, Math.round(board.slots.size / 36.0).toInt())

    private fun spawn() {
        val empty = b.indices.filter { b[it] == 0 }
        if (empty.isEmpty()) return
        val i = empty[rnd(empty.size)]
        b[i] = if (random.nextDouble() < 0.9) 1 else 2
        if (freshT <= 0) fresh = ArrayList()
        fresh.add(i)
        freshT = 5
    }

    /** 向き d に寄せた結果。16（e = 4）が上限で、16 どうしは重ならない。 */
    private fun move(src: IntArray, d: Int): Result {
        val nb = src.copyOf()
        var moved = false
        var merges = 0
        val trails = ArrayList<Trail>()
        val mergedTo = ArrayList<Int>()
        for (idx in board.lines[d]) {
            val items = ArrayList<IntArray>() // [マス, 列の中の位置, 数字]
            idx.forEachIndexed { q, i -> if (src[i] != 0) items.add(intArrayOf(i, q, src[i])) }
            val out = ArrayList<Int>()
            var m = 0
            while (m < items.size) {
                val to = out.size
                val it = items[m]
                if (m + 1 < items.size && it[2] == items[m + 1][2] && it[2] < MAX_E) {
                    out.add(it[2] + 1)
                    merges++
                    mergedTo.add(idx[to])
                    trails.add(Trail(it[0], it[1] - to, it[2]))
                    trails.add(Trail(items[m + 1][0], items[m + 1][1] - to, items[m + 1][2]))
                    m += 2
                } else {
                    out.add(it[2])
                    trails.add(Trail(it[0], it[1] - to, it[2]))
                    m++
                }
            }
            while (out.size < idx.size) out.add(0)
            idx.forEachIndexed { q, i ->
                if (nb[i] != out[q]) moved = true
                nb[i] = out[q]
            }
        }
        return Result(nb, moved, merges, trails, mergedTo)
    }

    override fun draw(out: FloatArray) {
        val slots = board.slots
        // 空きマスの目印（いちばん暗い点灯）
        for (q in slots) put(out, q % n, q / n, EMPTY)
        val a = anim
        if (a != null) {
            // ドットが1マスずつ流れて寄る。後ろに薄い残像を1マス付ける
            val dx = DX[a.dir]
            val dy = DY[a.dir]
            val mv = a.f * a.spd
            for (tr in a.res.trails) {
                val s = slots[tr.from]
                val m = min(mv, tr.dist)
                put(out, s % n + dx * m, s / n + dy * m, LEVEL[tr.e])
                if (m > 0 && m < tr.dist) put(out, s % n + dx * (m - 1), s / n + dy * (m - 1), LEVEL[tr.e] * 0.3)
            }
            return
        }
        val dim = if (end > 0) end / END_STEPS.toDouble() else 1.0 // 終わったら、ゆっくり消えていく
        for (k in b.indices) {
            val e = b[k]
            if (e == 0) continue
            val x = slots[k] % n
            val y = slots[k] / n
            var v = LEVEL[e] * dim
            if (freshT > 0 && k in fresh) v *= 1 - freshT / 6.0 // 新しいタイルはふわっと現れる
            put(out, x, y, v)
            if (popT > 0 && k in pop) {
                // 合体した瞬間に、最大の明るさで光り、まわりへ弾ける
                for (d in 0..3) put(out, x + DX[d], y + DY[d], 0.12 * popT)
                set(out, x, y, 1.0)
            }
        }
    }

    /** 盤面の形。slots：各マスの位置（y×N＋x）。lines[d]：向き d に寄せるときの列（寄せる側の端から順のマス番号）。 */
    private class Board(val slots: IntArray, val lines: List<List<IntArray>>) {
        companion object {
            private val cache = HashMap<Int, Board>()

            fun of(geo: Geo): Board = synchronized(cache) {
                cache.getOrPut(geo.n) {
                    val n = geo.n
                    val slots = (0 until geo.size).filter { geo.inside(it) }.toIntArray()
                    val byCol = slots.indices.groupBy { slots[it] % n }.toSortedMap()
                    val byRow = slots.indices.groupBy { slots[it] / n }.toSortedMap()
                    val up = byCol.values.map { col -> col.sortedBy { slots[it] / n }.toIntArray() }
                    val left = byRow.values.map { row -> row.sortedBy { slots[it] % n }.toIntArray() }
                    Board(slots, listOf(up, left.map { it.reversedArray() }, up.map { it.reversedArray() }, left))
                }
            }
        }
    }

    private companion object {
        const val G = 9.80665
        const val MAX_E = 4
        const val END_STEPS = 18

        /**
         * 数字ごとの明るさ（2・4・8・16）。
         * 実機では 0.35 以下が下限近くにまとまって空きマスと見分けられないので、4段階とも主な帯に置く（LedMapper）。
         */
        val LEVEL = doubleArrayOf(0.0, 0.4, 0.6, 0.8, 1.0)

        /** 空きマスの目印。PONG WARS の暗い陣地と同じ、いちばん暗い点灯。 */
        const val EMPTY = 0.07

        /** 寄せる傾き（度）。傾きの感度［鈍い・普通・敏感］。 */
        val SENS_DEG = doubleArrayOf(30.0, 20.0, 12.0)

        /** 水平近くに戻ったとみなす傾き（しきい値に対する割合）。 */
        const val REST_RATIO = 0.4

        /** これだけ水平のまま置かれていたら自動プレイにする（秒）。 */
        const val IDLE_SEC = 8.0

        val DX = intArrayOf(0, 1, 0, -1)
        val DY = intArrayOf(-1, 0, 1, 0)
    }
}
