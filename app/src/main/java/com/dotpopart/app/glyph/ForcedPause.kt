package com.dotpopart.app.glyph

/**
 * 伏せたままでも、画面を上にして置いたときと同じに止める（デバッグ版の受け口から使う）。
 * おもちゃを止めたときの電池の減り方を、伏せた置き方のまま測るため。
 */
object ForcedPause {
    @Volatile var on = false
}
