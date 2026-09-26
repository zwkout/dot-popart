package com.dotpopart.app.device

import android.content.Context
import android.os.Build
import android.util.Log
import com.dotpopart.engine.Geo
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph

/**
 * 端末の判定。Application.onCreate で行う（サービスのためにバックグラウンドで起動されたときも判定するため）。
 * 結果はハードの性質で変わらないので、SharedPreferences にキャッシュする。
 */
object DeviceInfo {
    private const val TAG = "DPA"

    /** マトリクスの一辺（13 または 25）。 */
    var matrixLength: Int = 13
        private set

    /** Glyph Matrix を持つ Nothing の端末か。違う端末では表示の送信をしない。 */
    var hasMatrix: Boolean = false
        private set

    val geo: Geo get() = Geo.of(matrixLength)

    /** Phone (4a) Pro のような、AOD のおもちゃだけを使える端末か（Glyph ボタンなし）。 */
    val aodOnly: Boolean get() = matrixLength == 13

    val glyphDevice: String get() = if (matrixLength == 25) Glyph.DEVICE_23112 else Glyph.DEVICE_25111p

    val displayName: String get() = if (matrixLength == 25) "PHONE (3)" else "PHONE (4a) PRO"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        val cached = prefs.getInt("matrixLength", 0)
        if (cached == 13 || cached == 25) {
            matrixLength = cached
            hasMatrix = true
            return
        }
        val detected = runCatching {
            when {
                Common.is25111p() -> 13
                Common.is23112() -> 25
                else -> Common.getDeviceMatrixLength()
            }
        }.getOrElse {
            Log.w(TAG, "matrix length detection failed", it)
            0
        }
        matrixLength = when {
            detected == 13 || detected == 25 -> detected
            Build.MODEL.contains("A069P") -> 13
            else -> 0
        }
        hasMatrix = matrixLength != 0
        if (!hasMatrix) matrixLength = 13
        Log.i(TAG, "device model=${Build.MODEL} detected=$detected matrix=$matrixLength hasMatrix=$hasMatrix")
        if (hasMatrix) prefs.edit().putInt("matrixLength", matrixLength).apply()
    }
}
