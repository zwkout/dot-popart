package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * MARBLE：傾けると転がり、縁に当たると震える。
 * 重力は加速度センサーの端末面に沿った成分（m/s²）から、1ステップあたりの加速度に換算する。
 * プレビューでも実機の傾きを使う。
 */
class MarbleToy(ctx: ToyContext) : Toy(ctx) {
    private class Hit(val a: Double, var age: Int, val k: Double)

    private var x = 0.0
    private var y = 0.0
    private var vx = 0.0
    private var vy = 0.0
    private val trail = ArrayDeque<DoubleArray>()
    private val hits = ArrayList<Hit>()
    private var haptic = 0f

    private val sc: Double get() = if (geo.big) 1.9 else 1.0

    override val stepsPerSecond: Double get() = 14.0

    override fun init() {
        x = geo.c
        y = geo.c
        vx = 0.0
        vy = 0.0
        trail.clear()
        hits.clear()
        haptic = 0f
    }

    /** 揺らす：ランダムな向きに弾く。 */
    override fun act() {
        val k = if (geo.big) 3.6 else 2.0
        vx += (random.nextDouble() - 0.5) * k
        vy += (random.nextDouble() - 0.5) * k
    }

    override fun step() {
        // 端末を 90° 立てたとき（1G）に 0.12×倍率 マス/ステップ² になるように換算する。
        val gx = inputs.gravityX / G * ACCEL_PER_G * sc
        val gy = inputs.gravityY / G * ACCEL_PER_G * sc
        vx = (vx + gx) * FRICTION
        vy = (vy + gy) * FRICTION
        trail.addFirst(doubleArrayOf(x, y))
        if (trail.size > 4) trail.removeLast()
        x += vx
        y += vy
        val c = geo.c
        val dx = x - c
        val dy = y - c
        val d = sqrt(dx * dx + dy * dy)
        val lim = geo.r - 1.6 * sc
        if (d > lim) {
            val nx = dx / d
            val ny = dy / d
            x = c + nx * lim
            y = c + ny * lim
            val vn = vx * nx + vy * ny
            if (vn > 0) {
                val bounce = params["bounce"]
                vx -= (1 + bounce) * vn * nx
                vy -= (1 + bounce) * vn * ny
                // 縁の光は、強く当たったときだけ（15°以上傾けて勢いよく当てた最初の1回）。
                // 跳ね返りや、縁に沿って転がるときの小さな当たりでは光らせない
                if (vn > GLOW_MIN * sc) hits.add(Hit(atan2(ny, nx), 0, min(1.0, 0.4 + (vn / sc - GLOW_MIN))))
                if (vn > 0.1 * sc) {
                    val k = min(1.0, vn * 1.6 / sc)
                    val amp = VIB_AMP[params.int("vib")]
                    if (amp > 0) haptic = maxOf(haptic, (amp * k).toFloat())
                }
            }
        }
        hits.forEach { it.age++ }
        hits.removeAll { it.age >= HIT_STEPS }
    }

    override fun consumeHaptic(): Float = haptic.also { haptic = 0f }

    override fun draw(out: FloatArray) {
        val amp = VIB_AMP[params.int("vib")]
        val rimR = geo.r - 1.3 * (if (geo.big) 1.3 else 1.0)
        val rim = rimR * rimR
        val c = geo.c
        if (amp > 0) {
            // 当たった場所の縁を扇状に光らせ、約0.6秒で消す
            for (h in hits) {
                for (i in 0 until geo.size) {
                    if (!geo.inside(i)) continue
                    val dx = i % n - c
                    val dy = i / n - c
                    if (dx * dx + dy * dy < rim) continue
                    var da = abs(atan2(dy, dx) - h.a)
                    if (da > PI) da = 2 * PI - da
                    if (da < hitWidth(h)) put(out, i % n, i / n, hitGlow(h, amp))
                }
            }
        }
        for (k in trail.indices.reversed()) disc(out, trail[k][0], trail[k][1], 0.32 - k * 0.07)
        disc(out, x, y, 1.0)
    }

    /**
     * 縁の光の広がり（当たった方向からの角度）。当たった瞬間がいちばん広く、両端から消えていき、
     * 最後は当たった位置の1マスだけが残る（ぶつかった場所へ収束して見える）。
     */
    private fun hitWidth(h: Hit): Double {
        val p = h.age / HIT_STEPS.toDouble()
        return 0.12 + 0.88 * (1 - p)
    }

    /**
     * 縁の光の明るさ。強さ（バイブの強さ × 当たった速さ）から0へまっすぐ下げると、
     * 実機では 0.35 以下がほぼ同じ暗さになるので、途中で急に暗くなり、そのまま消えたように見えた。
     * 主な帯（0.36〜）の中で時間の8割をかけて下げ、残りで薄い帯を下限まで下げて消す（LedMapper）。
     */
    private fun hitGlow(h: Hit, amp: Double): Double {
        val p = h.age / HIT_STEPS.toDouble()
        val start = 0.45 + 0.55 * amp * h.k
        return if (p < 0.8) 0.36 + (start - 0.36) * (1 - p / 0.8) else 0.35 - 0.2 * ((p - 0.8) / 0.2)
    }

    /** ふちのぼやけた円（ビー玉）。 */
    private fun disc(out: FloatArray, px: Double, py: Double, k: Double) {
        val rs = sc
        val e = ceil(2 * rs).toInt()
        for (yy in floor(py).toInt() - e..ceil(py).toInt() + e) {
            for (xx in floor(px).toInt() - e..ceil(px).toInt() + e) {
                val d = sqrt((xx - px) * (xx - px) + (yy - py) * (yy - py)) / rs
                val v = if (d < 0.75) 1.0 else if (d < 1.2) 0.85 else if (d < 1.65) 0.38 else 0.0
                if (v > 0) put(out, xx, yy, v * k)
            }
        }
    }

    private companion object {
        const val G = 9.80665
        const val ACCEL_PER_G = 0.12
        const val FRICTION = 0.985
        /** 縁を光らせる、当たったときの速さの下限（マス/ステップ、玉の大きさの倍率あたり）。 */
        const val GLOW_MIN = 0.5

        /** 縁の光が消えるまでのステップ数（約0.9秒）。 */
        const val HIT_STEPS = 13
        val VIB_AMP = doubleArrayOf(0.0, 0.55, 1.0)
    }
}
