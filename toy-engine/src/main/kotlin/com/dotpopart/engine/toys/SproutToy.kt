package com.dotpopart.engine.toys

import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * SPROUT：充電中だけ育ち、満ちると花が咲く。
 * 茎の高さは今の充電量に比例する。挿したときは種から今の高さまで数秒で伸び、その後は充電と一緒に伸びる。
 * 抜くと約1.4秒でしおれて、種に戻る。
 */
class SproutToy(ctx: ToyContext) : Toy(ctx) {
    private var charging = true
    private var level = 8.0
    private var hold = 0
    private var wilt = 0
    private var idle = 0

    override val stepsPerSecond: Double get() = 10.0

    override fun init() {
        if (demo) {
            charging = true
            level = 8.0
        } else {
            charging = inputs.charging
            level = 0.0
        }
        hold = 0
        wilt = 0
        idle = 0
    }

    /** 抜き挿し（プレビュー用）。実機では本物の充電状態に従うので何もしない。 */
    override fun act() {
        if (demo) togglePlug()
    }

    private fun togglePlug() {
        if (charging) {
            charging = false
            wilt = WILT_STEPS
        } else {
            charging = true
            wilt = 0
            idle = 0
            if (!demo) level = 0.0
        }
    }

    override fun step() {
        if (demo) stepDemo() else stepReal()
    }

    /** プレビュー：充電を早回しで見せる。 */
    private fun stepDemo() {
        if (charging) {
            if (level < 100) {
                level = minOf(100.0, level + 0.7)
            } else if (++hold > 50) {
                hold = 0
                togglePlug()
            }
        } else if (wilt > 0) {
            if (--wilt == 0) level = 0.0
        } else if (++idle > 25) {
            togglePlug()
        }
    }

    private fun stepReal() {
        if (inputs.charging != charging) togglePlug()
        if (charging) {
            val target = inputs.batteryLevel.toDouble().coerceIn(0.0, 100.0)
            level = if (level < target) minOf(target, level + GROW_PER_STEP) else target
        } else if (wilt > 0) {
            if (--wilt == 0) level = 0.0
        }
    }

    private val full: Boolean get() = if (demo) level >= 100 else inputs.batteryLevel >= 100

    override fun draw(out: FloatArray) {
        val c = geo.c.toInt()
        val big = geo.big
        val f = if (wilt > 0) wilt / WILT_STEPS.toDouble() else 1.0
        val bl = odd(time, 2.0)
        // 鉢は常に薄く出す
        val pot = if (big) {
            arrayOf(intArrayOf(c - 4, c + 4, n - 4), intArrayOf(c - 3, c + 3, n - 3), intArrayOf(c - 3, c + 3, n - 2))
        } else {
            arrayOf(intArrayOf(c - 2, c + 2, n - 2), intArrayOf(c - 1, c + 1, n - 1))
        }
        // 鉢は PONG WARS の暗い陣地と同じ明るさ（LedMapper）
        for (r in pot) for (x in r[0]..r[1]) put(out, x, r[2], 0.14)
        val base = pot[0][2] - 1
        val maxH = if (big) 12 else 6
        if (!charging && wilt == 0) {
            set(out, c, base, 0.3) // 種
            return
        }
        val h = floor(level / 100 * (maxH + 0.99)).toInt()
        val top = base - h
        for (y in base downTo top) set(out, c, y, 0.75 * f)
        if (big) {
            for (lf in arrayOf(intArrayOf(base - 2, 1), intArrayOf(base - 5, -1), intArrayOf(base - 8, 1), intArrayOf(base - 11, -1))) {
                val y = lf[0]
                val sd = lf[1]
                if (top <= y - 1) {
                    put(out, c + sd, y, 0.45 * f)
                    put(out, c + 2 * sd, y - 1, 0.42 * f)
                }
                if (top <= y - 2) {
                    put(out, c + 3 * sd, y - 1, 0.35 * f)
                    put(out, c + 2 * sd, y, 0.3 * f)
                    put(out, c + 4 * sd, y - 2, 0.3 * f)
                }
            }
        } else {
            // (y, x) の組。13×13 の盤面に合わせた葉の位置
            for (lf in arrayOf(intArrayOf(9, 5), intArrayOf(8, 7), intArrayOf(7, 5), intArrayOf(6, 7), intArrayOf(5, 5))) {
                if (top <= lf[0] - 1) {
                    set(out, lf[1], lf[0], 0.42 * f)
                    if (top <= lf[0] - 3) set(out, if (lf[1] == 5) 4 else 8, lf[0] - 1, 0.3 * f)
                }
            }
        }
        if (level >= params["bloom"]) {
            val cy = top - if (big) 3 else 2
            val star = params.int("shape") == 1
            if (big) {
                for (y in 0 until n) for (x in 0 until n) {
                    if (!geo.inside(x, y)) continue
                    val dx = x - c
                    val dy = y - cy
                    val d = sqrt((dx * dx + dy * dy).toDouble())
                    if (d <= 1.2) {
                        put(out, x, y, (if (bl) 1.0 else 0.6) * f)
                    } else if (!star && d > 1.7 && d <= 2.9) {
                        put(out, x, y, 0.85 * f)
                    } else if (star && d <= 3.6 && (dx == 0 || dy == 0 || abs(dx) == abs(dy))) {
                        put(out, x, y, (if (d > 2.5) (if (bl) 0.9 else 0.4) else 0.85) * f)
                    }
                }
            } else {
                set(out, c, cy, (if (bl) 1.0 else 0.6) * f)
                for (d in D4) put(out, c + d[0], cy + d[1], 0.9 * f)
                if (star) {
                    for (d in arrayOf(intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(1, 1))) {
                        put(out, c + d[0] * 2, cy + d[1] * 2, (if (bl) 0.9 else 0.35) * f)
                    }
                }
            }
        }
        // 充電中は、茎の下から上へ光の粒が昇っていく
        if (charging && !full) {
            val yy = base - floor((time * (if (big) 10 else 6)) % (h + 1)).toInt()
            set(out, c, yy, 1.0)
        }
    }

    private companion object {
        const val WILT_STEPS = 14
        /** 挿した直後、今の高さまで伸びる速さ（%/ステップ）。10ステップ/秒で 15%/秒。 */
        const val GROW_PER_STEP = 1.5
    }
}
