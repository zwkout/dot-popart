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
import kotlin.math.PI
import kotlin.math.sin

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

    /**
     * [version] と同じだけ進む、State でない数。[frame]／[cueFrame] は組み立ての中で呼ばれるので、
     * そこで [version] を読むと、呼んだカードが 0.1秒ごとに組み立て直される。こちらを使う。
     */
    @Volatile private var published = 0L

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
            // マップはセンサーを使わず、ゆっくり揺れる擬似の方位で見せる
            a.heading = (CUE_SWAY_DEG * sin(2 * PI * time / CUE_SWAY_S) + 360) % 360
            if (!live) {
                // USE の最初のコマを薄く止めて出す。USE が消灯のもの（映画）は、START の途中のコマを使う
                if (a.darkUse) a.start(CUE_DARK_P, back) else a.use(0.0, back)
                for (i in back.indices) back[i] *= CUE_IDLE_DIM
                return
            }
            val t = time % (a.startSeconds + CUE_USE_S + a.endSeconds + CUE_OFF_S)
            when {
                t < a.startSeconds -> a.start(t / a.startSeconds, back)
                t < a.startSeconds + CUE_USE_S -> a.use(t - a.startSeconds, back)
                t < a.startSeconds + CUE_USE_S + a.endSeconds -> a.end((t - a.startSeconds - CUE_USE_S) / a.endSeconds, CUE_USE_S, back)
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
                current.lastUsed = published
                return current.front
            }
            val runner = ToyRunner(def, geo, p, inputs, options = EngineOptions(demo = true))
            val e = Entry(sig, runner, geo.size, published)
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
                current.lastUsed = published
                return current.front
            }
            val e = CueEntry(anim(geo), live, published)
            e.draw()
            System.arraycopy(e.back, 0, e.front, 0, e.front.size)
            cues[key] = e
            return e.front
        }
    }

    /**
     * 描く段階から呼ぶ。このコマ（[frame] が返した配列）を、まだ画面に出ているとして残す。
     * 画面から外れて描かれなくなったものは、しばらくして [step] が捨てる。
     */
    fun touch(front: FloatArray) {
        synchronized(lock) {
            val e = entries.values.firstOrNull { it.front === front }
            if (e != null) e.lastUsed = published else cues.values.firstOrNull { it.front === front }?.lastUsed = published
        }
    }

    /** 試し操作。次の [step] で、計算のスレッドの上で行う。 */
    fun act(key: String) = synchronized(lock) { entries[key]?.pendingAct = true }

    /** 裏のスレッドから呼ぶ。全部のプレビューを進めて back に描く。 */
    fun step(dtSec: Double) {
        val list = synchronized(lock) {
            val stale = published - 30
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
        published++
        version++
    }
}

private const val CUE_USE_S = 4.0
private const val CUE_OFF_S = 1.0
private const val CUE_IDLE_DIM = 0.35f
private const val CUE_DARK_P = 0.2
private const val CUE_SWAY_DEG = 60.0
private const val CUE_SWAY_S = 9.0
