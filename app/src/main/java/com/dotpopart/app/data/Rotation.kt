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
     * 一度も切り替えていない（lastSwitch = 0）ときは、今を起点にするだけで切り替えない。
     */
    fun due(s: AppSettings, now: Long, flipped: Boolean): Boolean {
        if (!active(s)) return false
        val interval = s.rotationInterval.millis ?: return flipped
        if (s.rotationLastSwitch <= 0L) return false
        // 時計が戻ったときも、次の切り替えまで待つ
        return now - s.rotationLastSwitch >= interval
    }

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
