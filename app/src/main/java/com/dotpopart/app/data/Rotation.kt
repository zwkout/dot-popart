package com.dotpopart.app.data

import com.dotpopart.engine.ToyCatalog
import kotlin.random.Random

/** ローテーションの判定（Android に依存しない。単体テストする）。 */
object Rotation {
    /** ローテーションに入っているおもちゃ（一覧の順）。 */
    fun list(s: AppSettings): List<String> = ToyCatalog.all.map { it.id }.filter { it in s.rotationToys }

    fun active(s: AppSettings): Boolean = s.rotationEnabled && list(s).isNotEmpty()

    /**
     * 時間で切り替えるときが来たか。「伏せるたび」は [flipped]（伏せた瞬間）で判定する。
     * 起点を取り直すべきとき（[needsStart]）は切り替えない。
     */
    fun due(s: AppSettings, now: Long, flipped: Boolean): Boolean {
        if (!active(s)) return false
        val interval = s.rotationInterval.millis ?: return flipped
        if (needsStart(s, now)) return false
        return now - s.rotationLastSwitch >= interval
    }

    /**
     * 今を起点として記録し直すべきか。一度も切り替えていない（lastSwitch = 0）ときと、時計が戻ったとき
     * （戻した幅のぶんまで待つと、手で日付を戻したときなどに長く止まるため）。
     */
    fun needsStart(s: AppSettings, now: Long): Boolean = s.rotationLastSwitch <= 0L || now < s.rotationLastSwitch

    /**
     * 次に出すおもちゃ。リスト順では、最後にローテーションで出したおもちゃ（cursor）の次。
     * ✓ で割り込んでいても、cursor の次に戻る。
     */
    fun next(s: AppSettings, random: Random = Random.Default): String {
        val list = list(s)
        if (list.isEmpty()) return s.activeToy
        return when (s.rotationOrder) {
            RotationOrder.LIST -> {
                val base = s.rotationCursor?.takeIf { it in list } ?: s.activeToy
                val at = list.indexOf(base)
                list[(at + 1).mod(list.size)]
            }
            RotationOrder.RANDOM -> {
                val others = list.filter { it != s.activeToy }
                if (others.isEmpty()) list.first() else others[random.nextInt(others.size)]
            }
        }
    }

    /** Glyph ボタンの「次へ」：ローテーション中はその次、そうでなければ一覧の次。 */
    fun manualNext(s: AppSettings): String {
        if (active(s)) return next(s)
        val ids = ToyCatalog.all.map { it.id }
        return ids[(ids.indexOf(s.activeToy) + 1).mod(ids.size)]
    }
}
