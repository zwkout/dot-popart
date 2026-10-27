package com.dotpopart.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.dotpopart.engine.AppCueAnim
import com.dotpopart.engine.EngineOptions
import com.dotpopart.engine.Geo
import com.dotpopart.engine.Inputs
import com.dotpopart.engine.ToyDef
import com.dotpopart.engine.ToyRunner

/**
 * 画面内のプレビューをまとめて 10fps で進める。実機のマトリクスと同じエンジン（:toy-engine）を使う。
 * プレビューは [frame] で名前（例：一覧の "L:pong"、設定シートの "S"）ごとに1つずつ持ち、
 * 設定が変わったら最初からやり直す。しばらく使われなかったものは捨てる。
 * アプリ連動のアニメーションも [cueFrame] で同じように持つ。
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

    /** アプリ連動のプレビュー。START → USE → END → 消灯をくり返す。[live] でなければ USE のコマを薄く止めて出す。 */
    private class CueEntry(val anim: AppCueAnim, val live: Boolean, var lastUsed: Long) {
        val back = FloatArray(anim.geo.size)
        val front = FloatArray(anim.geo.size)
        var time = 0.0

        fun draw() {
            val a = anim
            if (!live) {
                a.use(back)
                for (i in back.indices) back[i] *= CUE_IDLE_DIM
                return
            }
            val t = time % (a.startSeconds + CUE_USE_S + a.endSeconds + CUE_OFF_S)
            when {
                t < a.startSeconds -> a.start(t / a.startSeconds, back)
                t < a.startSeconds + CUE_USE_S -> a.use(back)
                t < a.startSeconds + CUE_USE_S + a.endSeconds -> a.end((t - a.startSeconds - CUE_USE_S) / a.endSeconds, back)
                else -> back.fill(0f)
            }
        }
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private val cues = HashMap<String, CueEntry>()

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

    /** UI スレッドから呼ぶ。アプリ連動のプレビューのコマを返す（[live] が変わったら最初からやり直す）。 */
    fun cueFrame(key: String, geo: Geo, live: Boolean, anim: (Geo) -> AppCueAnim): FloatArray {
        synchronized(lock) {
            val current = cues[key]
            if (current != null && current.live == live && current.anim.geo == geo) {
                current.lastUsed = version
                return current.front
            }
            val e = CueEntry(anim(geo), live, version)
            e.draw()
            System.arraycopy(e.back, 0, e.front, 0, e.front.size)
            cues[key] = e
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
            cues.values.removeAll { it.lastUsed < stale }
            entries.values.toList()
        }
        val cueList = synchronized(lock) { cues.values.toList() }
        for (e in cueList) {
            e.time += dtSec
            e.draw()
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
            for (e in cues.values) System.arraycopy(e.back, 0, e.front, 0, e.front.size)
        }
        version++
    }
}

private const val CUE_USE_S = 4.0
private const val CUE_OFF_S = 1.0
private const val CUE_IDLE_DIM = 0.35f
