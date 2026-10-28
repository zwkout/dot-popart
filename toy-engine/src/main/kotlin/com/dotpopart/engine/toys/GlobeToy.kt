package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import com.dotpopart.engine.WorldMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GLOBE：正射投影の地球（地軸の傾きはなし）。
 * 実時間では、画面中央の経度を「太陽直下点の経度 − 60°」にする。「今の昼夜」では右寄り60°の方向から光が当たる。
 *
 * 回してもなめらかに見えるように、陸か海かを次の2段で決める。
 * 1. LED の面積のうち陸が占める割合を、地図から正確に足す。地軸が傾いていないので、画面の横線はそのまま緯線になり、
 *    回すと経度がずれるだけになる。そこで LED ごとに、横に [LINES] 本の線で切った区間を「緯度と経度の範囲」として
 *    一度だけ求めておき、毎コマその範囲の陸の長さを足す。以前は LED ごとに数点だけ地図を拾っていたので、
 *    回るたびに拾うマスが不規則に入れ替わり、東南アジアの島などの LED が点いたり消えたりした（特に Phone (3)）。
 *    今は陸の割合が回転につれてなめらかに変わるので、LED は島や海岸が通り過ぎるときに1回ずつしか切り替わらない。
 * 2. 割合が半分以上なら陸にする。ただし前のコマの結果を少しだけ持ち越す（[pickLand]）。
 */
class GlobeToy(ctx: ToyContext) : Toy(ctx) {
    private companion object {
        /** 海の明るさ。PONG WARS の暗い陣地と同じ（いちばん暗い点灯。LedMapper）。 */
        const val OCEAN = 0.14

        /** LED を横に切る線の数。緯度は回っても変わらないので、ちらつきには関係しない（形の細かさだけに効く）。 */
        const val LINES = 8

        /** 1つの区間の経度の幅の上限（度）。縁の近くでは1つの LED が広い経度にまたがるので、細かく分けて重みを付ける。 */
        const val MAX_SPAN_DEG = 3.0

        /** 前のコマで陸だった LED は、陸の割合がこの幅だけ半分を下回っても陸のままにする。 */
        const val HOLD = 0.1
    }

    /** LED ごとの区間。LED i の区間は start[i] から start[i + 1] の手前まで。経度は画面中央からのずれ（度）。 */
    private class Footprints(
        val start: IntArray,
        val row: IntArray,
        val lon0: DoubleArray,
        val lon1: DoubleArray,
        val weight: DoubleArray,
    )

    private val re get() = geo.r + 0.3

    private val footprints: Footprints by lazy { buildFootprints() }

    /** LED ごとの陸の割合と、陸にしたかどうか（前のコマの結果として次のコマでも使う）。 */
    private val cover = DoubleArray(geo.size)
    private val land = BooleanArray(geo.size)

    /** [pickLand] で使う、1行ぶんの作業用の配列。 */
    private val rowCover = DoubleArray(geo.n)
    private val rowWas = BooleanArray(geo.n)
    private val rowLand = BooleanArray(geo.n)

    override val stepsPerSecond: Double get() = 10.0

    override fun init() {
        land.fill(false)
    }

    override fun step() {}

    private fun buildFootprints(): Footprints {
        val c = geo.c
        val start = IntArray(geo.size + 1)
        val row = ArrayList<Int>()
        val lon0 = ArrayList<Double>()
        val lon1 = ArrayList<Double>()
        val weight = ArrayList<Double>()
        for (i in 0 until geo.size) {
            start[i] = row.size
            if (!geo.inside(i)) continue
            val x = i % n
            val y = i / n
            for (k in 0 until LINES) {
                val ny = (y - 0.5 + (k + 0.5) / LINES - c) / re
                val rho2 = 1 - ny * ny
                if (rho2 <= 0) continue
                val rho = sqrt(rho2)
                val x0 = max((x - 0.5 - c) / re, -rho)
                val x1 = min((x + 0.5 - c) / re, rho)
                if (x1 <= x0) continue
                // 横線は緯線。画面の横位置 nx と、中央からの経度のずれ φ は nx = ρ sin φ で結ばれる
                val phi0 = asin(x0 / rho)
                val phi1 = asin(x1 / rho)
                val pieces = max(1, ceil((phi1 - phi0) * 180 / PI / MAX_SPAN_DEG).toInt())
                val r = WorldMap.row(-asin(ny) * 180 / PI)
                for (j in 0 until pieces) {
                    val a = phi0 + (phi1 - phi0) * j / pieces
                    val b = phi0 + (phi1 - phi0) * (j + 1) / pieces
                    row += r
                    lon0 += a * 180 / PI
                    lon1 += b * 180 / PI
                    // 重みは、この区間が LED（1×1）の中で占める面積
                    weight += rho * (sin(b) - sin(a)) * re / LINES
                }
            }
        }
        start[geo.size] = row.size
        return Footprints(start, row.toIntArray(), lon0.toDoubleArray(), lon1.toDoubleArray(), weight.toDoubleArray())
    }

    override fun draw(out: FloatArray) {
        val c = geo.c
        val re = re
        val fp = footprints
        val utcMillisOfDay = Math.floorMod(inputs.epochMillis, 86_400_000L)
        val utcH = utcMillisOfDay / 3_600_000.0
        val sunLon = (12 - utcH) * 15
        val center = when (params.int("spin")) {
            0 -> sunLon - 60
            1 -> 139.7 - time * 6
            else -> 139.7 - time * 20
        }
        // LED の面積のうち陸が占める割合（球の外にはみ出した部分は数えない）
        for (i in 0 until geo.size) {
            var f = 0.0
            for (s in fp.start[i] until fp.start[i + 1]) {
                f += fp.weight[s] * WorldMap.landBetween(fp.row[s], center + fp.lon0[s], center + fp.lon1[s])
            }
            cover[i] = f
        }
        pickLand()
        val night = params.int("night") == 1
        for (y in 0 until n) for (x in 0 until n) {
            val i = geo.idx(x, y)
            if (!geo.inside(x, y) || fp.start[i] == fp.start[i + 1]) continue
            val isLand = land[i]
            val cx = (x - c) / re
            val cy = (y - c) / re
            val cz = sqrt(max(0.0, 1 - cx * cx - cy * cy))
            // 昼：海はいちばん暗い点灯（球の輪郭が見える）、陸は主な帯（LedMapper）。
            // 海岸線のマスも陸か海のどちらかにする（間の明るさは、暗い帯と主な帯の境目で跳ぶため）
            var v = if (isLand) 0.62 + 0.38 * (0.35 + 0.65 * cz) else OCEAN
            if (night) {
                val lit = cx * 0.866 + cz * 0.5
                if (lit < -0.08) {
                    // 夜側は海も陸も薄い帯（明るさ1・2では消える）。夜側の陸で、まれに街の灯をまたたかせる
                    val twinkle = isLand && sin(x * 12.9 + y * 78.2 + time * 0.7) > 0.93
                    v = if (twinkle) 0.7 else if (isLand) 0.3 else OCEAN
                } else if (lit < 0.12 && isLand) {
                    v = 0.45 // 昼夜の境目の陸は少し暗く
                }
            }
            out[i] = v.toFloat()
        }
    }

    /**
     * 陸にする LED を選んで [land] に書く。回転は横向きだけなので、行ごとに左右の隣だけを見て決める。
     *
     * - 陸の割合が半分以上なら陸。前のコマで陸だった LED は、[HOLD] だけ下回っても陸のままにする
     *   （半分の前後を行き来する LED が、コマごとにばたつかないように）。
     * - LED 1つぶんくらいの小さな島は、LED の境目をまたぐ間、両側とも半分に届かず一瞬消える。
     *   そこで、前のコマで陸だった LED が半分を下回っても、隣と合わせて半分以上の陸があり、
     *   その外側に陸がなければ（ほかの陸とつながっていない島なら）、2つのうち陸が多いほう1つを陸にする。
     *   島は消えずに、隣のほうが多くなった時点で1マス横へ移る。
     */
    private fun pickLand() {
        for (y in 0 until n) {
            for (x in 0 until n) {
                rowCover[x] = cover[geo.idx(x, y)]
                rowWas[x] = land[geo.idx(x, y)]
            }
            for (x in 0 until n) rowLand[x] = isBig(x)
            for (x in 0 until n) {
                if (!rowWas[x] || isBig(x)) continue
                val side = if (coverAt(x - 1) > coverAt(x + 1)) -1 else 1
                val p = x + side
                if (p !in 0 until n || isBig(p) || isBig(p + side) || isBig(x - side)) continue
                if (coverAt(x) + coverAt(p) < 0.5) continue
                if (score(p) > score(x)) rowLand[p] = true else rowLand[x] = true
            }
            for (x in 0 until n) land[geo.idx(x, y)] = rowLand[x]
        }
    }

    private fun coverAt(x: Int): Double = if (x in 0 until n) rowCover[x] else 0.0

    private fun wasLand(x: Int): Boolean = x in 0 until n && rowWas[x]

    /** 陸の割合だけで陸になるか（前のコマで陸なら [HOLD] だけ甘くする）。 */
    private fun isBig(x: Int): Boolean = coverAt(x) >= 0.5 - (if (wasLand(x)) HOLD else 0.0)

    /** 島をどちらの LED に置くかを比べる値。前のコマで陸だったほうを [HOLD] だけ優先する。 */
    private fun score(x: Int): Double = coverAt(x) + (if (wasLand(x)) HOLD else 0.0)
}
