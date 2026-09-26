package com.dotpopart.engine

import kotlin.random.Random

/**
 * おもちゃを経過時間に合わせて進める。
 * 描画のコマが抜けても、ステップは経過時間ぶん進むので動きの速さは変わらない。
 * ただし止まっていた時間の追いつきは [MAX_CATCH_UP_SEC] までにする（ワープを防ぐ）。
 */
class ToyRunner(
    val def: ToyDef,
    val geo: Geo,
    params: Params,
    val inputs: Inputs,
    random: Random = Random.Default,
    val options: EngineOptions = EngineOptions(),
) {
    val toy: Toy = def.create(ToyContext(geo, params, inputs, random, options)).also { it.init() }
    private var pending = 0.0

    fun advance(dtSec: Double) {
        val dt = dtSec.coerceIn(0.0, MAX_CATCH_UP_SEC)
        toy.time += dt
        pending += dt * toy.stepsPerSecond
        var guard = 0
        while (pending >= 1.0 && guard < MAX_STEPS_PER_ADVANCE) {
            toy.step()
            pending -= 1.0
            guard++
        }
        if (guard == MAX_STEPS_PER_ADVANCE) pending = 0.0
    }

    /** 描いて、円の外・NaN・範囲外を片付けた配列を返す。 */
    fun draw(out: FloatArray) {
        out.fill(0f)
        toy.draw(out)
        for (i in out.indices) {
            val v = out[i]
            out[i] = if (!geo.inside(i) || v.isNaN()) 0f else v.coerceIn(0f, 1f)
        }
    }

    companion object {
        const val MAX_CATCH_UP_SEC = 0.25
        private const val MAX_STEPS_PER_ADVANCE = 64
    }
}
