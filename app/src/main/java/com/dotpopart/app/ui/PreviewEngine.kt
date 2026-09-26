package com.dotpopart.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.dotpopart.engine.EngineOptions
import com.dotpopart.engine.Geo
import com.dotpopart.engine.Inputs
import com.dotpopart.engine.ToyDef
import com.dotpopart.engine.ToyRunner

/**
 * 画面内のプレビューをまとめて 10fps で進める。実機のマトリクスと同じエンジン（:toy-engine）を使う。
 * プレビューは [frame] で名前（例：一覧の "L:pong"、設定シートの "S"）ごとに1つずつ持ち、
 * 設定が変わったら最初からやり直す。しばらく使われなかったものは捨てる。
 *
 * シミュレーションの計算（[step]）は裏のスレッドで行い、描画側には出来上がったコマ（front）だけを渡す。
 * UI スレッドでは [publish] で front を差し替えて [version] を進めるだけにする。
 */
class PreviewEngine(val inputs: Inputs) {
    /** 1コマ進むたびに増える。描画側は描く段階（draw）でこれを読んで描き直す。 */
    var version by mutableLongStateOf(0L)
        private set

    private class Entry(val sig: String, val runner: ToyRunner, size: Int, var lastUsed: Long) {
        /** 裏のスレッドが書く。 */
        val back = FloatArray(size)
        /** 描画が読む。[publish] のときだけ書き換える。 */
        val front = FloatArray(size)
        var pendingAct = false
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    /** UI スレッドから呼ぶ。描画用のコマを返す（同じ名前・同じ設定なら同じ配列）。 */
    fun frame(key: String, def: ToyDef, params: Map<String, Float>, geo: Geo): FloatArray {
        val p = def.params(params)
        val sig = "${def.id}|${p.asMap()}|${geo.n}"
        synchronized(lock) {
            val current = entries[key]
            if (current != null && current.sig == sig) {
                current.lastUsed = version
                return current.front
            }
            val runner = ToyRunner(def, geo, p, inputs, options = EngineOptions(demo = true))
            val e = Entry(sig, runner, geo.size, version)
            runner.draw(e.front)
            System.arraycopy(e.front, 0, e.back, 0, e.front.size)
            entries[key] = e
            return e.front
        }
    }

    fun bufferOf(key: String): FloatArray? = synchronized(lock) { entries[key]?.front }

    /** 試し操作。次の [step] で、計算のスレッドの上で行う。 */
    fun act(key: String) = synchronized(lock) { entries[key]?.pendingAct = true }

    /** 裏のスレッドから呼ぶ。全部のプレビューを進めて back に描く。 */
    fun step(dtSec: Double) {
        val list = synchronized(lock) {
            val stale = version - 30
            entries.values.removeAll { it.lastUsed < stale }
            entries.values.toList()
        }
        for (e in list) {
            val act = synchronized(lock) { e.pendingAct.also { e.pendingAct = false } }
            if (act) e.runner.toy.act()
            e.runner.advance(dtSec)
            e.runner.draw(e.back)
        }
    }

    /**
     * スクロール中や、シート・メニューを指で動かしている間は、描き直しを止める（計算は続ける）。
     * 中身が 0.1秒ごとに変わるだけで、スクロールの1コマが 9ms から 13ms に重くなるため。
     */
    @Volatile var scrolling = false
        set(v) { field = v; hold() }
    @Volatile var overlayMoving = false
        set(v) { field = v; hold() }
    private var holdRedraw = false
    private fun hold() { holdRedraw = scrolling || overlayMoving }

    /** UI スレッドから呼ぶ。back を front に写して、描き直しを知らせる。 */
    fun publish() {
        if (holdRedraw) return
        synchronized(lock) {
            for (e in entries.values) System.arraycopy(e.back, 0, e.front, 0, e.front.size)
        }
        version++
    }
}
