package com.dotpopart.app.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dotpopart.app.device.DeviceInfo
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Glyph サービスへの接続をプロセスで1つにまとめる。
 * 接続するのはおもちゃのサービスだけにする。画面（アプリ層）が先に接続すると、同じプロセスから送るコマが
 * おもちゃの層に送ったものまでアプリ層（ID 123・明るさ 4095）として扱われ、伏せている間に途切れた
 * （(4a) Pro のログで確認。おもちゃの層は ID 121・明るさ 1023）。
 * いま使うのはおもちゃのサービスだけだが、`GlyphMatrixManager` はシングルトンなので、ほかから init／unInit すると
 * 接続を切ってしまう。使う側は [acquire]／[release] で参照を持ち、最後の1人が離れたときだけ切る。
 * メインスレッドから呼ぶ。
 */
object GlyphLink {
    private const val TAG = "DPA"
    private val main = Handler(Looper.getMainLooper())
    private var gmm: GlyphMatrixManager? = null
    private val waiting = LinkedHashMap<Any, () -> Unit>()
    private val owners = LinkedHashSet<Any>()

    /** 接続の世代。切ったあとに届いた前の接続の知らせを捨てるのに使う。 */
    @Volatile private var generation = 0

    @Volatile var connected = false
        private set

    val manager: GlyphMatrixManager? get() = if (connected) gmm else null

    private fun callback(gen: Int) = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            main.post {
                val m = gmm ?: return@post
                if (gen != generation) return@post
                val ok = m.register(DeviceInfo.glyphDevice)
                Log.i(TAG, "glyph connected device=${DeviceInfo.glyphDevice} registered=$ok")
                // 登録に失敗しても送ってみる（どんなときに false になるかは分かっていない）。送れなければ送信の警告に出る
                if (!ok) Log.w(TAG, "glyph register returned false")
                connected = true
                val ready = waiting.values.toList()
                waiting.clear()
                ready.forEach { it() }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (gen != generation) return
            connected = false
            Log.i(TAG, "glyph disconnected")
        }
    }

    /** 接続を使い始める。つながったら [onReady] を呼ぶ（すでにつながっていればすぐ呼ぶ）。 */
    fun acquire(context: Context, owner: Any, onReady: () -> Unit) {
        if (!DeviceInfo.hasMatrix) return
        owners.add(owner)
        if (connected) {
            onReady()
            return
        }
        waiting[owner] = onReady
        if (gmm == null) {
            val gen = ++generation
            gmm = GlyphMatrixManager.getInstance(context.applicationContext).also { it.init(callback(gen)) }
        }
    }

    fun release(owner: Any) {
        waiting.remove(owner)
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        gmm?.unInit()
        gmm = null
        connected = false
        Log.i(TAG, "glyph released")
    }
}
