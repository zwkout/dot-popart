package com.dotpopart.engine

/**
 * アプリ連動のアニメーション。決まったアプリを開いている間だけ、おもちゃの代わりに出す。
 * 開いたときの [start]、使っている間の [use]、閉じたときの [end] の3つで書く。
 * どれも長さ N×N の配列に 0.0〜1.0 を書いて返す（円の外は 0 のまま）。おもちゃのコマとの重ね方は含めない。
 */
interface AppCueAnim {
    val geo: Geo

    /** START の長さ（秒）。 */
    val startSeconds: Double

    /** END の長さ（秒）。 */
    val endSeconds: Double

    /** START のコマ。[p] は経過 / [startSeconds]（0〜1）。 */
    fun start(p: Double, out: FloatArray = FloatArray(geo.size)): FloatArray

    /** USE のコマ。 */
    fun use(out: FloatArray = FloatArray(geo.size)): FloatArray

    /** END のコマ。[p] は経過 / [endSeconds]（0〜1）。 */
    fun end(p: Double, out: FloatArray = FloatArray(geo.size)): FloatArray

    /** START の間におもちゃのコマに掛ける倍率（消えていく）。 */
    fun toyMulStart(p: Double): Float

    /** END の間におもちゃのコマに掛ける倍率（戻ってくる）。USE の間は 0。 */
    fun toyMulEnd(p: Double): Float
}

/**
 * vwfndr の「B2 ロック」（docs/app-cue.md の 2.3。参照実装は docs/prototype/app-cue.js）。
 * アイコンの十字（横の腕 round(4N/13)・縦の腕 round(2N/13)）に、AF 枠の四隅の角を組み合わせる。
 * 角は開いたときと閉じたときだけ出て、使っている間は十字だけを止めて出す。
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

    override val startSeconds = 1.2
    override val endSeconds = 0.7

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

    override fun use(out: FloatArray): FloatArray {
        out.fill(0f)
        cross(out, armH, ARM, CENTER)
        return out
    }

    override fun end(p: Double, out: FloatArray): FloatArray {
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

    override fun toyMulStart(p: Double): Float = cl(1 - 3 * p).toFloat()

    override fun toyMulEnd(p: Double): Float = cl((p - 0.6) / 0.4).toFloat()

    /** 十字。[len] は横の腕の長さ（縦は縦の腕で止める）、[v] は腕、[cv] は中心。 */
    private fun cross(f: FloatArray, len: Int, v: Double, cv: Double) {
        if (cv > 0) put(f, c, c, cv)
        for (k in 1..len) {
            put(f, c + k, c, v)
            put(f, c - k, c, v)
            if (k <= armV) {
                put(f, c, c + k, v)
                put(f, c, c - k, v)
            }
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
