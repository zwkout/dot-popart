package com.dotpopart.app.data

/** 切り替えるタイミング。[FLIP] は「伏せるたび」。 */
enum class RotationInterval(val key: String, val millis: Long?, val label: String) {
    MIN15("15m", 15 * 60_000L, "15分"),
    HOUR1("1h", 60 * 60_000L, "1時間"),
    HOUR3("3h", 3 * 60 * 60_000L, "3時間"),
    FLIP("flip", null, "伏せるたび"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: HOUR1
    }
}

enum class RotationOrder(val key: String, val label: String) {
    LIST("list", "リスト順"),
    RANDOM("random", "ランダム"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: LIST
    }
}

/** Glyph ボタンの長押し（Phone (3) だけ）。 */
enum class ButtonAction(val key: String, val label: String) {
    NEXT("next", "次の Toys へ"),
    ACT("act", "試し操作"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: NEXT
    }
}

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: DARK
    }
}

/**
 * アプリの設定。入れたばかりのときは、ローテーションもアプリ連動もオフで、何も選んでいない。
 * @property toyParams おもちゃごとの設定値（`toy.<id>.<controlKey>`）。保存されていない項目は定義の既定値を使う。
 * @property rotationCursor ローテーションで最後に出したおもちゃ（✓ で割り込んでも変わらない）。
 * @property rotationLastSwitch ローテーションで最後に切り替えた時刻（エポックミリ秒）。
 * @property appCue アプリ連動のアプリごとのオン／オフ（`appCue.<id>`）。保存されていないアプリはオフ（使う人が自分でオンにする）。
 * @property previewDevice 開発用：プレビューで見る端末の一辺（0 なら実機に合わせる）。隠し操作のあとだけ効く。
 */
data class AppSettings(
    val activeToy: String = "pong",
    val brightness: Int = 2,
    val theme: ThemeMode = ThemeMode.DARK,
    val rotationEnabled: Boolean = false,
    val rotationToys: Set<String> = emptySet(),
    val rotationInterval: RotationInterval = RotationInterval.HOUR1,
    val rotationOrder: RotationOrder = RotationOrder.LIST,
    val buttonAction: ButtonAction = ButtonAction.NEXT,
    val appCue: Map<String, Boolean> = emptyMap(),
    val toyParams: Map<String, Map<String, Float>> = emptyMap(),
    val rotationCursor: String? = null,
    val rotationLastSwitch: Long = 0L,
    val previewDevice: Int = 0,
) {
    fun paramsOf(id: String): Map<String, Float> = toyParams[id] ?: emptyMap()

    fun appCueOn(id: String): Boolean = appCue[id] ?: false
}
