package com.dotpopart.engine

import kotlin.random.Random

/** おもちゃが動くのに必要なものをまとめたもの。 */
class ToyContext(
    val geo: Geo,
    val params: Params,
    val inputs: Inputs,
    val random: Random,
    val options: EngineOptions = EngineOptions(),
)

/**
 * @property demo プレビュー用。充電（SPROUT の早回し）・通知（AQUARIUM の自動のエサ）・流れ星の頻度を、
 *   疑似的な動きで見せる。傾きと電池残量はプレビューでも実機の値を使う。
 */
data class EngineOptions(
    val demo: Boolean = false,
)

/**
 * おもちゃの共通の型。「初期化 → 1ステップ進める → 描画」の3つで書く。
 *
 * - [stepsPerSecond] の頻度で [step] が呼ばれる。
 * - [time] は [init] からの経過秒。点滅などの見た目の時間はこれで測る（ステップ頻度に依存しない）。
 * - [draw] は長さ N×N の配列に 0.0〜1.0 を書く。円の外は 0 のままにする。
 */
abstract class Toy(protected val ctx: ToyContext) {
    protected val geo: Geo get() = ctx.geo
    protected val n: Int get() = ctx.geo.n
    protected val params: Params get() = ctx.params
    protected val inputs: Inputs get() = ctx.inputs
    protected val random: Random get() = ctx.random
    protected val demo: Boolean get() = ctx.options.demo

    /** [init] からの経過秒。[ToyRunner] が進める。 */
    var time: Double = 0.0
        internal set

    /** 自分で最初からやり直した回数（終局・全滅・ゴールなど）。テストで使う。 */
    var resets: Int = 0
        protected set

    abstract val stepsPerSecond: Double

    abstract fun init()

    abstract fun step()

    abstract fun draw(out: FloatArray)

    /** 試し操作（流れ星を呼ぶ、エサを落とす、など）。ないおもちゃは何もしない。 */
    open fun act() {}

    /**
     * バイブを鳴らしてほしい強さ（0 なら鳴らさない）。呼ぶと 0 に戻る。
     * MARBLE が縁に当たったときに使う。
     */
    open fun consumeHaptic(): Float = 0f

    /**
     * 入力（時刻を除く）が変わらなければ、見た目が次に変わるまでの秒数。いつ変わるかわからなければ null。
     * 時刻で変わるおもちゃは、次に見た目が変わる時刻までの秒数を返す。入力が変わるまで変わらなければ無限大。
     * 伏せて置いている間、サービスはこの時間まで刻みを休む（電池の消費を減らすため）。
     */
    open fun secondsUntilChange(): Double? = null

    /** 最初からやり直す（テストで使う。「振るとリセット」は作らないことにした）。 */
    fun restart() {
        time = 0.0
        init()
    }

    /** 選択肢のスピード（0〜2）から、表の値を選ぶ。 */
    protected fun bySpeed(slow: Double, normal: Double, fast: Double): Double =
        when (params.int("speed")) {
            0 -> slow
            2 -> fast
            else -> normal
        }

    /** 円の中なら、今の値より明るいときだけ書く。 */
    protected fun put(out: FloatArray, x: Int, y: Int, v: Double) {
        if (geo.inside(x, y)) {
            val i = y * n + x
            if (v > out[i]) out[i] = v.toFloat()
        }
    }

    protected fun set(out: FloatArray, x: Int, y: Int, v: Double) {
        if (geo.inside(x, y)) out[y * n + x] = v.toFloat()
    }

    /** JavaScript の Math.round と同じ丸め（.5 は正の方向へ）。 */
    protected fun jr(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()

    protected fun rnd(n: Int): Int = random.nextInt(n)

    /** floor(t × hz) が奇数か。 */
    protected fun odd(t: Double, hz: Double): Boolean = kotlin.math.floor(t * hz).toLong() % 2 == 1L
}
