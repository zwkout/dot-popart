package com.dotpopart.engine.toys

import com.dotpopart.engine.DotFont
import com.dotpopart.engine.Toy
import com.dotpopart.engine.ToyContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * BATTERY：外周のリングで残量を表す。警告レベルを下回るとリングが点滅する。
 * 100% のときは「FL」と出す（3桁の「100」は幅が足りずリングに重なるので、実機で見比べて決めた）。
 */
class BatteryToy(ctx: ToyContext) : Toy(ctx) {
    override val stepsPerSecond: Double get() = 10.0

    override fun init() {}

    override fun step() {}

    /** 点滅していなければ、残量が変わるまで同じ。 */
    override fun secondsUntilChange(): Double? =
        if (inputs.batteryLevel.coerceIn(0, 100) < params["warn"]) null else Double.POSITIVE_INFINITY

    override fun draw(out: FloatArray) {
        val lvl = inputs.batteryLevel.coerceIn(0, 100)
        val blinkOff = lvl < params["warn"] && odd(time, 2.0).not()
        val big = geo.big
        val ringR = if (big) geo.r - 2.2 else 5.2
        val oy = if (big) 9 else 4
        val digits = params.int("digits") == 1
        val text = if (lvl >= 100) "FL" else lvl.toString()
        val ox = if (text.length == 2) DotFont.twoDigitsX(big) else (n - DotFont.textWidth(text, big)) / 2
        val c = geo.c
        for (y in 0 until n) for (x in 0 until n) {
            if (!geo.inside(x, y)) continue
            val i = geo.idx(x, y)
            val dx = x - c
            val dy = y - c
            val d = sqrt(dx * dx + dy * dy)
            val onText = digits && DotFont.on(text, ox, oy, x, y, big)
            if (d >= ringR) {
                var a = atan2(dx, -dy)
                if (a < 0) a += 2 * PI
                out[i] = if (a / (2 * PI) <= lvl / 100.0) (if (blinkOff) 0.2f else 1f) else 0.12f
            } else if (digits) {
                out[i] = if (onText) 1f else 0f
            } else {
                out[i] = if (d < (if (big) 2.5 else 1.5)) 0.5f else 0f
            }
        }
    }
}
