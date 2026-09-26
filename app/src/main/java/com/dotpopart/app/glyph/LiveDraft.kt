package com.dotpopart.app.glyph

/**
 * 設定シートで編集中のおもちゃ。シートを開いている間、おもちゃのサービスはこれを背面に描く（おもちゃの層のまま）。
 * 画面とサービスは同じプロセスなので、値を渡すだけで済む（アプリ層は使わない。開くとおもちゃの層より優先され、画面が消えている間はコマが途切れるため）。
 */
object LiveDraft {
    data class Draft(val toyId: String, val params: Map<String, Float>, val brightness: Int)

    @Volatile var current: Draft? = null
}
