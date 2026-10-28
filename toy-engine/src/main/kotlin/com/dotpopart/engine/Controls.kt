package com.dotpopart.engine

/** 設定シートに並べる設定項目。値はすべて Float で持つ（区切りボタンは選択肢の番号）。 */
sealed class Control(val key: String, val label: String) {
    abstract val default: Float

    class Slider(
        key: String,
        label: String,
        val min: Float,
        val max: Float,
        val step: Float,
        override val default: Float,
        /** 現在の値の表示。例：`{ "${it.toInt()}%" }` */
        val format: (Float) -> String,
    ) : Control(key, label)

    class Choice(
        key: String,
        label: String,
        val options: List<String>,
        defaultIndex: Int = 0,
    ) : Control(key, label) {
        override val default: Float = defaultIndex.toFloat()
    }
}

/** スピードの3段階（既定は「普通」）。選べるのは DODGE だけで、ほかのおもちゃは「普通」で固定する。 */
fun speedControl() = Control.Choice("speed", "スピード", listOf("遅い", "普通", "速い"), defaultIndex = 1)

/**
 * おもちゃの設定値。設定項目（[controls]）は、キーがなければ定義の既定値を使う。
 * 固定値（[fixed]）は保存せず、いつも定義の値を使う。
 * [values] に固定値のキーが入っていても無視する（前の版で保存した値を持ち込まないため）。
 * テストで固定値を変えたいときは [overrideFixed] を使う。
 */
class Params(
    private val controls: List<Control>,
    fixed: Map<String, Float> = emptyMap(),
    values: Map<String, Float> = emptyMap(),
    overrideFixed: Map<String, Float> = emptyMap(),
) {
    private val fixedValues: Map<String, Float> = fixed + overrideFixed.filterKeys { it in fixed }
    private val all: Map<String, Float> = fixedValues + controls.associate { c ->
        c.key to clamp(c, values[c.key] ?: c.default)
    }

    operator fun get(key: String): Float =
        all[key] ?: error("unknown control '$key'")

    fun int(key: String): Int = Math.round(get(key))

    /** 保存する値（設定項目だけ）。作ったあと変わらないので、1回だけ作る。 */
    private val saved: Map<String, Float> by lazy { all.filterKeys { k -> controls.any { it.key == k } } }

    fun asMap(): Map<String, Float> = saved

    private fun clamp(c: Control, v: Float): Float = when (c) {
        is Control.Slider -> if (v.isNaN()) c.default else v.coerceIn(c.min, c.max)
        is Control.Choice -> if (v.isNaN()) c.default else Math.round(v).coerceIn(0, c.options.size - 1).toFloat()
    }
}
