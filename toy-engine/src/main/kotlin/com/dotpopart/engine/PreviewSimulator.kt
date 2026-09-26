package com.dotpopart.engine

/**
 * アプリ内プレビュー用の入力。傾き・充電・通知・流れ星の疑似的な動きは、
 * 各おもちゃが [EngineOptions.demo] のときに自分で作る。
 * ここでは時刻と、BATTERY に見せる電池残量だけを用意する。
 */
class PreviewSimulator(val inputs: Inputs = Inputs()) {
    fun advance(nowMillis: Long = System.currentTimeMillis()) {
        inputs.epochMillis = nowMillis
    }
}
