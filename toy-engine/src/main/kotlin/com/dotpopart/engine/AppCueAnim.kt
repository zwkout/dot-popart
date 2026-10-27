package com.dotpopart.engine

/**
 * アプリ連動のアニメーション。決まったアプリを開いている間だけ、おもちゃの代わりに出す。
 * 開いたときの [start]、使っている間の [use]、閉じたときの [end] の3つで書く。
 * どれも長さ N×N の配列に 0.0〜1.0 を書いて返す（円の外は 0 のまま）。おもちゃのコマとの重ね方は含めない。
 * START の p = 1 と USE の u = 0 は同じコマ、END の p = 1 は全部 0 にする。
 */
interface AppCueAnim {
    val geo: Geo

    /** START の長さ（秒）。 */
    val startSeconds: Double get() = START_SECONDS

    /** END の長さ（秒）。 */
    val endSeconds: Double get() = END_SECONDS

    /** USE の間も動くか（動かないものは、USE のコマがずっと同じ）。 */
    val animatedUse: Boolean get() = false

    /** USE の間は消灯するか（映画）。 */
    val darkUse: Boolean get() = false

    /** 方位（[heading]）を使うか（マップ）。 */
    val usesHeading: Boolean get() = false

    /** 北が向くべき向き（度。背面から見て 12 時が 0、時計回り）。[usesHeading] のものだけが使う。 */
    var heading: Double

    /** 端末を横にしたとき、絵を横向きに回すか（vwfndr）。 */
    val turnsWithDevice: Boolean get() = false

    /**
     * 絵を回す角度（度。背面から見て時計回り）。縦持ちは 0、横持ちは ±90、逆さまは 180。途中の角度は回っている途中。
     * [turnsWithDevice] のものだけが使う。
     */
    var turn: Double

    /** START のコマ。[p] は経過 / [startSeconds]（0〜1）。 */
    fun start(p: Double, out: FloatArray = FloatArray(geo.size)): FloatArray

    /** USE のコマ。[u] は USE の経過秒。 */
    fun use(u: Double, out: FloatArray = FloatArray(geo.size)): FloatArray

    /** END のコマ。[p] は経過 / [endSeconds]（0〜1）、[u0] は USE を何秒続けたか（START の途中で閉じたら 0）。 */
    fun end(p: Double, u0: Double, out: FloatArray = FloatArray(geo.size)): FloatArray

    /** START の間におもちゃのコマに掛ける倍率（消えていく）。 */
    fun toyMulStart(p: Double): Float = (1 - 3 * p).coerceIn(0.0, 1.0).toFloat()

    /** END の間におもちゃのコマに掛ける倍率（戻ってくる）。USE の間は 0。 */
    fun toyMulEnd(p: Double): Float = ((p - 0.6) / 0.4).coerceIn(0.0, 1.0).toFloat()

    companion object {
        const val START_SECONDS = 1.2
        const val END_SECONDS = 0.7
    }
}

/**
 * vwfndr の「B2 ロック」（docs/app-cue.md の 2.3。参照実装は docs/prototype/app-cue.js）。
 * アイコンの十字（横の腕 round(4N/13)・縦の腕 round(2N/13)）に、AF 枠の四隅の角を組み合わせる。
 * 角は開いたときと閉じたときだけ出て、使っている間は十字だけを止めて出す。
 * 端末を横にしたときは、十字を [turn] だけ回す（±90° で横 5・縦 9 になる）。四隅の角は回しても同じ形なので、回さない。
 */
class VwfndrLockAnim(override val geo: Geo) : AppCueAnim {
    private val n = geo.n
    private val c = (n - 1) / 2
    private val armH = Math.round(4.0 * n / 13).toInt()
    private val armV = Math.round(2.0 * n / 13).toInt()

    /** 中心から角までのマス数（横の腕と同じ）。 */
    private val box = armH

    /** 角から中心向きに、横と縦へ伸ばす脚のマス数。 */
    private val leg = if (geo.big) 2 else 1

    override var heading = 0.0
    override val turnsWithDevice = true
    override var turn = 0.0

    override fun start(p: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        cross(out, Math.round(cl(p / 0.35) * armH).toInt(), ARM, cl(p / 0.08) * CENTER)
        if (p >= 0.3 && p < 0.85) {
            val v = when {
                p < 0.42 -> FLASH
                p < 0.62 -> REST
                else -> REST * (1 - cl((p - 0.62) / 0.23))
            }
            corners(out, if (p < 0.62) box else box + 1, v)
        }
        return out
    }

    override fun use(u: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        cross(out, armH, ARM, CENTER)
        return out
    }

    override fun end(p: Double, u0: Double, out: FloatArray): FloatArray {
        out.fill(0f)
        if (p < 0.6) {
            val v = when {
                p < 0.22 -> REST * cl(p / 0.12)
                p < 0.36 -> FLASH
                else -> REST * (1 - cl((p - 0.36) / 0.24))
            }
            corners(out, if (p < 0.22) box + 1 else box, v)
        }
        val center = if (p < 0.75) CENTER else CENTER * (1 - cl((p - 0.75) / 0.25))
        cross(out, Math.round((1 - cl((p - 0.35) / 0.5)) * armH).toInt(), ARM, center)
        return out
    }

    /** 十字。[len] は横の腕の長さ（縦は縦の腕で止める）、[v] は腕、[cv] は中心。 */
    private fun cross(f: FloatArray, len: Int, v: Double, cv: Double) {
        if (cv > 0) put(f, c, c, cv)
        if (turn != 0.0) {
            // 回した十字：腕を、中心から回した向きへ引いた線で描く（中心のマスは除く）
            val t = Math.toRadians(turn)
            val ux = StrictMath.cos(t)
            val uy = StrictMath.sin(t)
            val short = minOf(len, armV)
            arm(f, ux, uy, len, v)
            arm(f, -ux, -uy, len, v)
            arm(f, -uy, ux, short, v)
            arm(f, uy, -ux, short, v)
            return
        }
        for (k in 1..len) {
            put(f, c + k, c, v)
            put(f, c - k, c, v)
            if (k <= armV) {
                put(f, c, c + k, v)
                put(f, c, c - k, v)
            }
        }
    }

    /** 中心から向き ([dx], [dy]) へ長さ [len] の腕（Bresenham）。 */
    private fun arm(f: FloatArray, dx: Double, dy: Double, len: Int, v: Double) {
        if (len <= 0) return
        var x = c
        var y = c
        val x1 = Math.floor(c + dx * len + 0.5).toInt()
        val y1 = Math.floor(c + dy * len + 0.5).toInt()
        val ddx = kotlin.math.abs(x1 - x)
        val ddy = -kotlin.math.abs(y1 - y)
        val sx = if (x < x1) 1 else -1
        val sy = if (y < y1) 1 else -1
        var e = ddx + ddy
        while (x != x1 || y != y1) {
            val e2 = 2 * e
            if (e2 >= ddy) { e += ddy; x += sx }
            if (e2 <= ddx) { e += ddx; y += sy }
            put(f, x, y, v)
        }
    }

    /** 四隅の角。[b] は中心から角までのマス数。 */
    private fun corners(f: FloatArray, b: Int, v: Double) {
        if (v <= 0) return
        for (sy in intArrayOf(-1, 1)) for (sx in intArrayOf(-1, 1)) {
            val x = c + sx * b
            val y = c + sy * b
            put(f, x, y, v)
            for (k in 1..leg) {
                put(f, x - sx * k, y, v)
                put(f, x, y - sy * k, v)
            }
        }
    }

    /** 円の外のマスは描かない（角を外へ動かしたときに自然に欠ける）。 */
    private fun put(f: FloatArray, x: Int, y: Int, v: Double) {
        if (!geo.inside(x, y)) return
        val i = geo.idx(x, y)
        if (v > f[i]) f[i] = v.toFloat()
    }

    private fun cl(v: Double) = v.coerceIn(0.0, 1.0)

    private companion object {
        const val ARM = 0.75
        const val CENTER = 0.9
        const val FLASH = 1.0
        const val REST = 0.45
    }
}
