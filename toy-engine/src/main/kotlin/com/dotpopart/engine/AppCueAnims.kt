package com.dotpopart.engine

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * アプリ連動のアニメーションの一覧（docs/app-cue.md の 2.3〜2.5）。
 * vwfndr 以外は docs/prototype/app-cue-more.js の採用案（末尾の `APPS`）を移したもの。細かい数値は参照実装に合わせる。
 *
 * 座標の基準：C = (N−1)/2、s = (N−1)/12（13×13 で 1、25×25 で 2）。角度は 12 時を 0°、時計回り。
 * 三角関数は StrictMath（fdlibm。参照実装を動かす JavaScript と同じ計算）を使い、四捨五入は JavaScript の Math.round に合わせる。
 */
object AppCueAnims {
    /** アニメーションの id（参照実装の名前）。vwfndr は `b2_lock`。 */
    val ids = listOf(
        "b2_lock", "shz_b", "prompt", "radar", "scan_d", "nav_d", "git_b", "link_a", "gauge", "curtain_m", "tape_a", "ess_d", "film_b",
    )

    fun create(id: String, geo: Geo): AppCueAnim = when (id) {
        "b2_lock" -> VwfndrLockAnim(geo)
        "shz_b" -> ShazamSpin(geo)
        "prompt" -> TermuxPrompt(geo)
        "radar" -> Radar(geo)
        "scan_d" -> LensScan(geo)
        "nav_d" -> MapsArrow(geo)
        "git_b" -> GitDiff(geo)
        "link_a" -> ObsidianLink(geo)
        "gauge" -> SpeedGauge(geo)
        "curtain_m" -> Curtain(geo)
        "tape_a" -> TapeReels(geo)
        "ess_d" -> EssentialSpark(geo)
        "film_b" -> Film(geo)
        else -> throw IllegalArgumentException("unknown app cue anim: $id")
    }
}

/** 参照実装の描き方（put／線／輪など）をまとめた土台。点は最大値で重ね、円の外は描かない。 */
abstract class CueAnim(final override val geo: Geo) : AppCueAnim {
    protected val n = geo.n
    protected val c = geo.c
    protected val s = (n - 1) / 12.0
    private val dist = DoubleArray(geo.size) { i -> sqrt(sq(i % n - c) + sq(i / n - c)) }

    override var heading = 0.0
    override var turn = 0.0

    final override fun start(p: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        drawStart(out, p)
        return out
    }

    final override fun use(u: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        drawUse(out, u)
        return out
    }

    final override fun end(p: Double, u0: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        drawEnd(out, p, u0)
        return out
    }

    protected abstract fun drawStart(f: FloatArray, p: Double)
    protected abstract fun drawUse(f: FloatArray, u: Double)
    protected abstract fun drawEnd(f: FloatArray, p: Double, u0: Double)

    protected fun d(i: Int) = dist[i]

    protected fun set(f: FloatArray, i: Int, v: Double) {
        val w = v.toFloat()
        if (w > f[i]) f[i] = w
    }

    protected fun put(f: FloatArray, x: Double, y: Double, v: Double) {
        if (!(v > 0)) return
        val xi = rnd(x)
        val yi = rnd(y)
        if (!geo.inside(xi, yi)) return
        set(f, geo.idx(xi, yi), v)
    }

    /** 線分のマス（Bresenham）。端点は四捨五入。 */
    protected fun cells(ax: Double, ay: Double, bx: Double, by: Double): List<IntArray> {
        var x0 = rnd(ax)
        var y0 = rnd(ay)
        val x1 = rnd(bx)
        val y1 = rnd(by)
        val out = ArrayList<IntArray>()
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var e = dx + dy
        while (true) {
            out.add(intArrayOf(x0, y0))
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * e
            if (e2 >= dy) { e += dy; x0 += sx }
            if (e2 <= dx) { e += dx; y0 += sy }
        }
        return out
    }

    protected fun line(f: FloatArray, x0: Double, y0: Double, x1: Double, y1: Double, v: Double) {
        for (q in cells(x0, y0, x1, y1)) put(f, q[0].toDouble(), q[1].toDouble(), v)
    }

    /** 中心から角度 [a]（度）・半径 [r] の位置。 */
    protected fun pol(a: Double, r: Double): DoubleArray {
        val t = a * Math.PI / 180
        return doubleArrayOf(c + r * StrictMath.sin(t), c - r * StrictMath.cos(t))
    }

    protected fun rot(dx: Double, dy: Double, a: Double): DoubleArray {
        val t = a * Math.PI / 180
        val cs = StrictMath.cos(t)
        val sn = StrictMath.sin(t)
        return doubleArrayOf(dx * cs - dy * sn, dx * sn + dy * cs)
    }

    protected fun angOf(i: Int): Double {
        val x = i % n
        val y = i / n
        return mod(StrictMath.atan2(x - c, -(y - c)) * 180 / Math.PI, 360.0)
    }

    protected fun ringAt(f: FloatArray, cx: Double, cy: Double, r: Double, v: Double) {
        if (!(v > 0)) return
        for (i in f.indices) {
            if (!geo.inside(i)) continue
            val dd = sqrt(sq(i % n - cx) + sq(i / n - cy))
            if (abs(dd - r) < 0.55) set(f, i, v)
        }
    }

    protected fun disc(f: FloatArray, r: Double, v: Double) {
        if (!(v > 0)) return
        put(f, c, c, v)
        for (i in f.indices) if (geo.inside(i) && dist[i] <= r + 1e-9) set(f, i, v)
    }

    /** 横 1 行。[half] があれば中心から左右 half マスまで。 */
    protected fun hline(f: FloatArray, y: Double, v: Double, half: Double? = null) {
        val yi = rnd(y).toDouble()
        for (x in 0 until n) if (half == null || abs(x - c) <= half + 1e-9) put(f, x.toDouble(), yi, v)
    }

    /** 多角形を塗る。マスを 3×3 に分けて、3 点以上が中に入っていれば点灯。 */
    protected fun fillPoly(f: FloatArray, pts: List<DoubleArray>, v: Double) {
        if (!(v > 0)) return
        for (i in f.indices) {
            if (!geo.inside(i)) continue
            val x = (i % n).toDouble()
            val y = (i / n).toDouble()
            var hit = 0
            for (a in -1..1) for (b in -1..1) if (inPoly(pts, x + a / 3.0, y + b / 3.0)) hit++
            if (hit >= 3) set(f, i, v)
        }
    }

    private fun inPoly(pts: List<DoubleArray>, x: Double, y: Double): Boolean {
        var inside = false
        var b = pts.size - 1
        for (a in pts.indices) {
            val (xa, ya) = pts[a]
            val (xb, yb) = pts[b]
            if ((ya > y) != (yb > y) && x < (xb - xa) * (y - ya) / (yb - ya) + xa) inside = !inside
            b = a
        }
        return inside
    }

    /**
     * 13×13 の座標で描いたドット絵の点の列。[vf] が i 番目の明るさ（0 なら描かない）。
     * 25×25 や回したとき用に、となりあう点のあいだは線で埋める。
     */
    protected fun bmPath(f: FloatArray, pts: Array<IntArray>, ang: Double = 0.0, sc: Double = 1.0, vf: (Int) -> Double) {
        val pp = pts.map { q ->
            var dx = (q[0] - 6) * s * sc
            var dy = (q[1] - 6) * s * sc
            if (ang != 0.0) {
                val r = rot(dx, dy, ang)
                dx = r[0]
                dy = r[1]
            }
            doubleArrayOf(c + dx, c + dy)
        }
        for (i in pp.indices) {
            val v = vf(i)
            if (!(v > 0)) continue
            put(f, pp[i][0], pp[i][1], v)
            if (i + 1 < pp.size) {
                val w = vf(i + 1)
                if (w > 0) {
                    val mv = min(v, w)
                    for (q in cells(pp[i][0], pp[i][1], pp[i + 1][0], pp[i + 1][1])) put(f, q[0].toDouble(), q[1].toDouble(), mv)
                }
            }
        }
    }

    protected companion object {
        const val FLASH = 1.0
        const val CENTER = 0.9
        const val MAIN = 0.75
        const val REST = 0.45
        const val DIM = 0.25
        const val START_S = AppCueAnim.START_SECONDS
        const val END_S = AppCueAnim.END_SECONDS

        /** JavaScript の Math.round（.5 は大きいほうへ）。 */
        fun rnd(x: Double): Int = Math.floor(x + 0.5).toInt()
        fun sq(x: Double) = x * x
        fun cl(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v
        fun seg(p: Double, a: Double, b: Double) = cl((p - a) / (b - a))
        fun eo(t: Double) = 1 - (1 - t) * (1 - t)
        fun eio(t: Double) = if (t < 0.5) 2 * t * t else 1 - 2 * (1 - t) * (1 - t)
        fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
        fun mod(a: Double, m: Double) = ((a % m) + m) % m
        fun hash(k: Double): Double {
            val x = StrictMath.sin(k * 127.1 + 311.7) * 43758.5453
            return x - Math.floor(x)
        }

        /** START を「USE を 1.2 秒前から始めたもの」として描くときの時刻。 */
        fun tStart(p: Double) = (p - 1) * START_S

        fun pts(vararg xy: Int) = Array(xy.size / 2) { intArrayOf(xy[2 * it], xy[2 * it + 1]) }
    }
}

/** Shazam：回って現れる（`shz_b`）。ユーザーが描いた 2 本の鉤の線（2 本目は点対称）。 */
private class ShazamSpin(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true
    private val hook = pts(5, 2, 4, 3, 3, 4, 2, 5, 2, 6, 3, 7, 4, 8, 5, 8, 6, 7, 7, 6)
    private val hooks = listOf(hook, Array(hook.size) { intArrayOf(12 - hook[it][0], 12 - hook[it][1]) })

    override fun drawStart(f: FloatArray, p: Double) {
        val q = eo(seg(p, 0.0, 0.7))
        val v = if (p < 0.7) MAIN * seg(p, 0.0, 0.1) else if (p < 0.8) FLASH else lerp(FLASH, REST, seg(p, 0.8, 1.0))
        for (h in hooks) bmPath(f, h, -180 * (1 - q), lerp(0.3, 1.0, q)) { v }
    }

    override fun drawUse(f: FloatArray, u: Double) {
        // 光が 2 本同時に、外の先端から内へなぞる
        val hp = (u % 1.6) / 1.6 * (hook.size + 2) - 1
        for (h in hooks) bmPath(f, h) { i -> maxOf(REST, FLASH * (1 - abs(i - hp) / 1.5)) }
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        val q = eio(seg(p, 0.0, 0.7))
        val v = REST * (1 - seg(p, 0.5, 0.9))
        for (h in hooks) bmPath(f, h, 180 * q, 1 - 0.7 * q) { v }
    }
}

/** Termux：`>_`（`prompt`）。 */
private class TermuxPrompt(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true
    private val path = cells(c - 3 * s, c - 2 * s, c - s, c) + cells(c - s, c, c - 3 * s, c + 2 * s).drop(1)

    private fun cursor(f: FloatArray, v: Double) = line(f, c + s, c + 2 * s, c + 3 * s, c + 2 * s, v)

    private fun prompt(f: FloatArray, k: Int) {
        for (i in 0 until k) put(f, path[i][0].toDouble(), path[i][1].toDouble(), MAIN)
    }

    override fun drawStart(f: FloatArray, p: Double) {
        prompt(f, rnd(seg(p, 0.0, 0.4) * path.size))
        if (p >= 0.4) cursor(f, if (p < 0.5) FLASH else MAIN)
    }

    override fun drawUse(f: FloatArray, u: Double) {
        prompt(f, path.size)
        if (u % 1.06 < 0.53) cursor(f, MAIN)
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) = prompt(f, rnd((1 - seg(p, 0.2, 0.8)) * path.size))
}

/** Flightradar24：レーダー（`radar`）。 */
private class Radar(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true
    private val blips = arrayOf(doubleArrayOf(40.0, 4.0), doubleArrayOf(150.0, 2.5), doubleArrayOf(255.0, 5.0))

    private fun draw(f: FloatArray, th: Double, len: Double, trail: Double, blipGain: Double, passed: ((DoubleArray) -> Boolean)? = null) {
        if (trail > 0) {
            for (i in f.indices) {
                if (!geo.inside(i) || d(i) < 0.8 || d(i) > len + 0.5) continue
                val dl = mod(th - angOf(i), 360.0)
                if (dl <= 100) set(f, i, 0.35 * (1 - dl / 100) * trail)
            }
        }
        for (b in blips) {
            if (passed != null && !passed(b)) continue
            val dl = mod(th - b[0], 360.0)
            val q = pol(b[0], b[1] * s)
            put(f, q[0], q[1], 0.9 * StrictMath.pow(1 - dl / 360, 1.5) * blipGain)
        }
        if (len >= 0.5) {
            val e = pol(th, len)
            line(f, c, c, e[0], e[1], MAIN)
        }
    }

    private fun useAngle(u: Double) = mod(360 * u / 4, 360.0)

    override fun drawStart(f: FloatArray, p: Double) {
        val th = 360 * eio(seg(p, 0.35, 1.0))
        draw(f, if (th == 360.0) 0.0 else th, 6 * s * eo(seg(p, 0.1, 0.4)), seg(p, 0.35, 0.6), 1.0) { b -> th >= 360 || th > b[0] }
        put(f, c, c, CENTER * seg(p, 0.0, 0.15))
    }

    override fun drawUse(f: FloatArray, u: Double) {
        draw(f, useAngle(u), 6 * s, 1.0, 1.0)
        put(f, c, c, CENTER)
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        draw(f, useAngle(u0), 6 * s * (1 - eio(seg(p, 0.2, 0.65))), 1 - seg(p, 0.0, 0.3), 1 - seg(p, 0.0, 0.4))
        put(f, c, c, CENTER * (1 - seg(p, 0.6, 0.95)))
    }
}

/** Google レンズ：走査線（`scan_d`）。いつも上から下へ流れる。 */
private class LensScan(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true

    private fun core(f: FloatArray, t: Double, k: Double, half: Double? = null) {
        val period = 1.8
        val top = c - 6 * s - 1
        val bot = c + 6 * s + 2
        val y = rnd(lerp(top, bot, mod(t + START_S, period) / period)).toDouble()
        hline(f, y, MAIN * k, half)
        hline(f, y - 1, 0.35 * k, half)
        hline(f, y - 2, 0.15 * k, half)
    }

    override fun drawStart(f: FloatArray, p: Double) = core(f, tStart(p), seg(p, 0.0, 0.15), (6 * s + 0.5) * eo(seg(p, 0.0, 0.3)))
    override fun drawUse(f: FloatArray, u: Double) = core(f, u, 1.0)
    override fun drawEnd(f: FloatArray, p: Double, u0: Double) = core(f, u0 + p * END_S, 1 - seg(p, 0.0, 0.5))
}

/**
 * Google マップ：矢印（`nav_d`）。[heading] の向きを指す。
 * 13×13 は手で描いた 2 枚の矢印（上向き 27 マス・右上向き 24 マス）を 90° ずつ回して 8 方向にする。
 * 25×25 は 8 方向に縛らず、矢じりを回して塗る（`nav_a`）。
 */
private class MapsArrow(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true
    override val usesHeading = true

    /** 中心からのずれ。最初の点が先端。 */
    private val up = pts(
        0, -4, -1, -3, 0, -3, 1, -3, -1, -2, 0, -2, 1, -2, -2, -1, -1, -1, 0, -1, 1, -1, 2, -1,
        -2, 0, -1, 0, 0, 0, 1, 0, 2, 0, -3, 1, -2, 1, -1, 1, 1, 1, 2, 1, 3, 1, -4, 2, -3, 2, 3, 2, 4, 2,
    )
    private val ne = pts(
        3, -3, 2, -3, 0, -2, 1, -2, 2, -2, 3, -2, -2, -1, -1, -1, 0, -1, 1, -1, 2, -1,
        -4, 0, -3, 0, -2, 0, -1, 0, 0, 0, 1, 0, 2, 0, 0, 1, 1, 1, 0, 2, 1, 2, 0, 3, 0, 4,
    )
    private val head = listOf(doubleArrayOf(0.0, -4.0), doubleArrayOf(3.0, 3.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(-3.0, 3.0))

    /** 方向 [dir]（0 = 上、時計回りに 45° ずつ）の矢印のマス。中心からのずれと、先端かどうか（3 つ目が 1）。 */
    fun arrowCells(dir: Int): List<IntArray> {
        val base = if (dir % 2 == 0) up else ne
        val turns = dir / 2
        return base.mapIndexed { idx, q ->
            var x = q[0]
            var y = q[1]
            repeat(turns) {
                val t = x
                x = -y
                y = t
            }
            intArrayOf(x, y, if (idx == 0) 1 else 0)
        }
    }

    private fun snapDir(h: Double) = mod(rnd(h / 45).toDouble(), 8.0).toInt()

    private fun arrow(f: FloatArray, dir: Int, vf: (IntArray, Double) -> Double) {
        val a = dir * 45 * Math.PI / 180
        val ux = StrictMath.sin(a)
        val uy = -StrictMath.cos(a)
        for (q in arrowCells(dir)) {
            val v = vf(q, q[0] * ux + q[1] * uy)
            if (v > 0) put(f, c + q[0], c + q[1], v)
        }
    }

    private fun smooth(f: FloatArray, a: Double, k: Double) {
        if (k <= 0) return
        val p = head.map { q ->
            val r = rot(q[0] * s * k, q[1] * s * k, a)
            doubleArrayOf(c + r[0], c + r[1])
        }
        fillPoly(f, p, MAIN)
        put(f, p[0][0], p[0][1], CENTER)
        put(f, c, c, MAIN)
    }

    override fun drawStart(f: FloatArray, p: Double) {
        if (geo.big) {
            val q = seg(p, 0.1, 1.0)
            smooth(f, heading + 70 * (1 - q) * StrictMath.exp(-3 * q) * StrictMath.cos(10 * q), eo(seg(p, 0.0, 0.4)))
            return
        }
        // 向きは「目標から 3 段手前」から 1 段ずつ回って止まる
        val dir = mod((snapDir(heading) + rnd(3 * (1 - eo(seg(p, 0.0, 0.6))))).toDouble(), 8.0).toInt()
        val k = seg(p, 0.0, 0.15)
        arrow(f, dir) { q, _ -> (if (p >= 0.6 && p < 0.7) FLASH else if (q[2] == 1) CENTER else MAIN) * k }
    }

    override fun drawUse(f: FloatArray, u: Double) {
        if (geo.big) {
            smooth(f, heading, 1.0)
            return
        }
        arrow(f, snapDir(heading)) { q, _ -> if (q[2] == 1) CENTER else MAIN }
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        if (geo.big) {
            val k = 1 - eio(seg(p, 0.0, 0.6))
            if (k > 0.15) smooth(f, heading, k) else put(f, c, c, MAIN * (1 - seg(p, 0.6, 0.95)))
            return
        }
        // 矢印の進む向きの軸で、後ろから先へ向かって消える
        val cut = lerp(-5 * s, 4.5 * s, eio(seg(p, 0.0, 0.6)))
        arrow(f, snapDir(heading)) { q, along ->
            if (q[2] == 1) {
                if (p < 0.6) CENTER else CENTER * (1 - seg(p, 0.6, 0.9))
            } else if (along > cut) {
                MAIN
            } else {
                0.0
            }
        }
    }
}

/** GitHub：差分が流れる（`git_b`）。横線をコードの行に見立てる。 */
private class GitDiff(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true

    private class Line(val type: Int, val ind: Int, val len: Int)

    private fun diffLine(k: Int): Line {
        val h = hash(k * 3.7 + 11)
        val type = if (h < 0.35) 1 else if (h < 0.55) -1 else 0
        return Line(type, Math.floor(hash(k * 5.3 + 2) * 3).toInt(), 3 + Math.floor(hash(k * 9.1 + 7) * 6).toInt())
    }

    private fun view(f: FloatArray, rowOff: Int, reveal: (Int, Double) -> Double) {
        val pitch = 2 * s
        val top = c - 6 * s
        val k0 = Math.floor(rowOff / pitch).toInt()
        val last = k0 + Math.ceil(13 * s / pitch).toInt() + 1
        for (k in k0..last) {
            val y = top + k * pitch - rowOff
            if (y < 0 || y >= n) continue
            val l = diffLine(k)
            val x0 = c - 3 * s + l.ind * s
            val len = l.len * s
            val show = reveal(k, len)
            val v = if (l.type == 1) MAIN else if (l.type == -1) 0.2 else REST
            var x = 0
            while (x < min(len, show)) {
                put(f, x0 + x, y, v)
                x++
            }
            if (show > 0 && l.type != 0) {
                // 左の欄の印：追加は明るい点、削除は淡い横棒
                val gx = c - 5 * s
                if (l.type == 1) {
                    put(f, gx, y, FLASH)
                } else {
                    put(f, gx, y, 0.3)
                    put(f, gx + 1, y, 0.3)
                }
            }
        }
    }

    private fun offset(u: Double) = Math.floor(u / (0.5 / s)).toInt()

    override fun drawStart(f: FloatArray, p: Double) = view(f, 0) { k, len ->
        val t = 0.05 + 0.1 * k
        rnd(len * seg(p, t, t + 0.25)).toDouble()
    }

    override fun drawUse(f: FloatArray, u: Double) = view(f, offset(u)) { _, len -> len }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) = view(f, offset(u0)) { _, len -> rnd(len * (1 - eio(seg(p, 0.0, 0.7)))).toDouble() }
}

/** Obsidian：`[[ ]]`（`link_a`）。 */
private class ObsidianLink(geo: Geo) : CueAnim(geo) {
    private fun bracket(f: FloatArray, x0: Double, dir: Int, v: Double) {
        val x = rnd(x0).toDouble()
        var y = c - 2 * s
        while (y <= c + 2 * s) {
            put(f, x, y, v)
            y++
        }
        var k = 1
        while (k <= s) {
            put(f, x + dir * k, c - 2 * s, v)
            put(f, x + dir * k, c + 2 * s, v)
            k++
        }
    }

    private fun brackets(f: FloatArray, off: Double, v: Double) {
        bracket(f, c - 5 * s - off, 1, v)
        bracket(f, c - 2 * s - off, 1, v)
        bracket(f, c + 5 * s + off, -1, v)
        bracket(f, c + 2 * s + off, -1, v)
    }

    override fun drawStart(f: FloatArray, p: Double) {
        brackets(f, 4 * s * (1 - eo(seg(p, 0.0, 0.5))), if (p >= 0.5 && p < 0.62) FLASH else MAIN)
        if (p >= 0.5) disc(f, s - 1, if (p < 0.62) FLASH else CENTER)
    }

    override fun drawUse(f: FloatArray, u: Double) {
        brackets(f, 0.0, MAIN)
        disc(f, s - 1, CENTER)
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        disc(f, s - 1, CENTER * (1 - seg(p, 0.0, 0.3)))
        brackets(f, 4 * s * eio(seg(p, 0.2, 0.8)), MAIN * (1 - seg(p, 0.5, 0.9)))
    }
}

/** Speedtest：メーター（`gauge`）。 */
private class SpeedGauge(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true
    private val layer = FloatArray(geo.size)

    private fun draw(f: FloatArray, v: Double, base: Double, needleK: Double, litV: Double = REST) {
        val rA = 5 * s
        for (i in f.indices) {
            if (!geo.inside(i) || abs(d(i) - rA) >= 0.55) continue
            val rel = mod(angOf(i) - 225, 360.0)
            if (rel > 270) continue
            set(f, i, if (rel / 270 <= v + 1e-9) litV else DIM * base)
        }
        if (needleK > 0 && 4 * s * needleK >= 0.5) {
            val e = pol(225 + 270 * v, 4 * s * needleK)
            line(f, c, c, e[0], e[1], MAIN)
        }
    }

    private fun useV(u: Double) = 0.6 + 0.03 * StrictMath.sin(2 * Math.PI * u / 1.7) + 0.015 * StrictMath.sin(2 * Math.PI * u / 0.55)

    override fun drawStart(f: FloatArray, p: Double) {
        val v = if (p < 0.5) eio(seg(p, 0.1, 0.45)) else 1 - 0.4 * eo(seg(p, 0.5, 0.85))
        draw(f, v, seg(p, 0.0, 0.1), seg(p, 0.1, 0.2), if (p >= 0.42 && p < 0.55) FLASH else REST)
        put(f, c, c, CENTER * seg(p, 0.0, 0.1))
    }

    override fun drawUse(f: FloatArray, u: Double) {
        draw(f, useV(u), 1.0, 1.0)
        put(f, c, c, CENTER)
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        val v = useV(u0) * (1 - eio(seg(p, 0.0, 0.5)))
        val base = 1 - seg(p, 0.4, 0.8)
        layer.fill(0f)
        draw(layer, v, 1.0, 1 - seg(p, 0.5, 0.85))
        // 弧は針より先に消える
        for (i in f.indices) set(f, i, layer[i] * (if (d(i) > 4.3 * s) base else 1.0))
        put(f, c, c, CENTER * (1 - seg(p, 0.6, 0.95)))
    }
}

/** Netflix・Prime Video：緞帳（`curtain_m`）。暗い部屋で見る人のために、USE の間は消灯する。 */
private class Curtain(geo: Geo) : CueAnim(geo) {
    override val darkUse = true

    private fun fold(u: Double) = lerp(0.18, 0.5, 0.5 + 0.5 * StrictMath.cos(2 * Math.PI * u))

    /** 上の飾り幕と、その下の波形のすそ。 */
    private fun valance(f: FloatArray, k: Double) {
        if (k <= 0) return
        val y0 = rnd(c - 5 * s)
        for (x in 0 until n) {
            for (y in 0..y0) put(f, x.toDouble(), y.toDouble(), REST * k)
            if (StrictMath.cos((x - c) * Math.PI / (1.5 * s)) > 0.2) {
                var dd = 1
                while (dd <= s) {
                    put(f, x.toDouble(), (y0 + dd).toDouble(), REST * k)
                    dd++
                }
            }
        }
    }

    /** 上の縁から [hem] の行までの幕。すその行は明るい。 */
    private fun drop(f: FloatArray, hem0: Double, k: Double, t: Double) {
        if (k <= 0) return
        val hem = rnd(hem0)
        for (y in 0..min(hem, n - 1)) for (x in 0 until n) {
            val u = (x - c) / (2 * s) + 0.15 * StrictMath.sin(t * 2 + y * 0.4 / s)
            put(f, x.toDouble(), y.toDouble(), (if (y == hem) MAIN else fold(u)) * k)
        }
    }

    override fun drawStart(f: FloatArray, p: Double) {
        val k = seg(p, 0.0, 0.2)
        drop(f, lerp(c + 6 * s + 1, c - 6 * s - 1, eio(seg(p, 0.25, 0.85))), k, p * 6)
        valance(f, k * (1 - seg(p, 0.85, 1.0)))
    }

    override fun drawUse(f: FloatArray, u: Double) {}

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        val q = seg(p, 0.0, 0.55)
        // 下りきる手前で、少しだけ跳ね返る
        val hem = lerp(c - 6 * s - 1, c + 6 * s + 1, eo(q)) + (if (q > 0.8) -1.2 * s * StrictMath.sin((q - 0.8) / 0.2 * Math.PI) else 0.0)
        val k = 1 - seg(p, 0.6, 0.95)
        drop(f, hem, k * seg(p, 0.0, 0.1), p * 6)
        valance(f, k * seg(p, 0.0, 0.1))
    }
}

/** Nothing Recorder：2 つのリール（`tape_a`）。録音中は本体の赤ランプが点滅するので、こちらは点滅させない。 */
private class TapeReels(geo: Geo) : CueAnim(geo) {
    override val animatedUse = true

    private fun spin(t: Double) = 360 * t / 1.5

    private fun reels(f: FloatArray, a: Double, k: Double, tapeK: Double) {
        val l = c - 3 * s
        val r = c + 3 * s
        for (cx in doubleArrayOf(l, r)) {
            ringAt(f, cx, c, 2 * s, REST * k)
            put(f, cx, c, CENTER * k)
            // 軸のまわりを回る 2 点
            for (d0 in doubleArrayOf(0.0, 180.0)) {
                val q = rot(0.0, -s, a + d0)
                put(f, cx + q[0], c + q[1], MAIN * k)
            }
        }
        if (tapeK > 0) line(f, l, c + 2 * s, r, c + 2 * s, DIM * tapeK)
    }

    override fun drawStart(f: FloatArray, p: Double) = reels(f, spin(tStart(p)), seg(p, 0.0, 0.3), seg(p, 0.3, 0.5))
    override fun drawUse(f: FloatArray, u: Double) = reels(f, spin(u), 1.0, 1.0)

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        val k = 1 - seg(p, 0.3, 0.9)
        reels(f, spin(u0 + 0.5 * p * (2 - p)), k, k)
    }
}

/** Essential Space：ひらめく（`ess_d`）。ユーザーが描いたドット絵（中心の十字と 6 本の光の線）。 */
private class EssentialSpark(geo: Geo) : CueAnim(geo) {
    private val core = listOf(pts(5, 6, 6, 6, 7, 6), pts(6, 5, 6, 6, 6, 7))

    /** 光の線：上から時計回り、内 → 外。 */
    private val rays = listOf(
        pts(6, 3, 6, 2, 6, 1), pts(9, 4, 10, 3), pts(9, 8, 10, 9), pts(6, 9, 6, 10, 6, 11), pts(3, 8, 2, 9), pts(3, 4, 2, 3),
    )

    private fun rayLevel(j: Int, len: Int) = if (len == 3) doubleArrayOf(MAIN, 0.6, REST)[j] else doubleArrayOf(MAIN, REST)[j]

    private fun core(f: FloatArray, v: Double, cv: Double) {
        for (line in core) bmPath(f, line) { i -> if (i == 1) cv else v }
    }

    override fun drawStart(f: FloatArray, p: Double) {
        val v = if (p < 0.15) FLASH * seg(p, 0.0, 0.05) else lerp(FLASH, MAIN, seg(p, 0.5, 0.8))
        val cv = if (p < 0.15) FLASH * seg(p, 0.0, 0.05) else lerp(FLASH, CENTER, seg(p, 0.5, 0.8))
        core(f, v, cv)
        rays.forEachIndexed { r, ray ->
            bmPath(f, ray) { j ->
                val t = 0.15 + 0.12 * j + 0.02 * r
                if (p < t) 0.0 else lerp(FLASH, rayLevel(j, ray.size), seg(p, t + 0.1, t + 0.3))
            }
        }
    }

    override fun drawUse(f: FloatArray, u: Double) {
        core(f, MAIN, CENTER)
        for (ray in rays) bmPath(f, ray) { j -> rayLevel(j, ray.size) }
    }

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        // 線は外から消える
        for (ray in rays) bmPath(f, ray) { j -> if (p < 0.05 + 0.15 * (ray.size - 1 - j) + 0.15) rayLevel(j, ray.size) else 0.0 }
        val k = 1 - seg(p, 0.45, 0.9)
        core(f, MAIN * k, CENTER * k)
    }
}

/** Pellica：フィルム（`film_b`）。横向き。縁の線と、穴を抜いた帯。 */
private class Film(geo: Geo) : CueAnim(geo) {
    private fun film(f: FloatArray, o0: Double, gain: Double, divV: Double) {
        val pitch = 8 * s
        val o = rnd(o0)
        for (u in 0 until n) {
            val ud = u.toDouble()
            val hole = mod(ud - o, 2 * s) < s
            for (sg in intArrayOf(-1, 1)) {
                val outer = c + sg * 5 * s
                val inner = c + sg * 3 * s
                put(f, ud, outer, REST * gain)
                put(f, ud, inner, REST * gain)
                var w = min(outer, inner) + 1
                while (w < maxOf(outer, inner)) {
                    if (!hole) put(f, ud, w, REST * gain)
                    w++
                }
            }
            // コマの仕切り
            if (mod(ud - (c - 4 * s) - o, pitch) == 0.0) {
                var w = c - 2 * s
                while (w <= c + 2 * s) {
                    put(f, ud, w, divV * gain)
                    w++
                }
            }
        }
    }

    override fun drawStart(f: FloatArray, p: Double) {
        val div = if (p < 0.65) DIM else if (p < 0.75) FLASH else lerp(FLASH, DIM, seg(p, 0.75, 0.85))
        // 右へ 1 コマずれた位置から、左へ巻き上げる
        film(f, 8 * s * (1 - eo(seg(p, 0.15, 0.65))), seg(p, 0.0, 0.2), div)
    }

    override fun drawUse(f: FloatArray, u: Double) = film(f, 0.0, 1.0, DIM)

    override fun drawEnd(f: FloatArray, p: Double, u0: Double) {
        val q = seg(p, 0.0, 0.65)
        film(f, -16 * s * q * q, 1 - seg(p, 0.35, 0.7), DIM)
    }
}
