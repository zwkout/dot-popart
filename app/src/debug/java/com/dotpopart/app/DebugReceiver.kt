package com.dotpopart.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.dotpopart.app.glyph.AppCue
import com.dotpopart.app.glyph.DebugFlags
import com.dotpopart.app.glyph.TestPattern
import com.dotpopart.engine.ToyCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * デバッグ版だけ。端末を伏せたまま、adb からおもちゃと明るさを切り替える（段階6の確認用）。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es toy clock [--ei brightness 2]
 * 明るさの下限を測る縦線を出す／消す（値は実効値。左から1列おきに並ぶ）。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es pattern 16,32,48,64,96,128
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es pattern off
 * アプリ連動のアニメーション（vwfndr）を、前面のアプリに関係なく出す（START）／消す（END）／元に戻す。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es cue on|off|auto [--es cueApp maps]
 * cueApp はアプリの id（AppCueTargets。なければ vwfndr）。
 * 伏せたまま、画面を上にして置いたときと同じに止める／戻す（電池の減り方の基準を測る）。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es pause on|off
 * 省電力の工夫を外して、前の動き方に戻す（off）／使う（on）。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es saver on|off
 * 区間を決めて、端末の中で自動で切り替える（モードと分。終わったらふだんの動きに戻る）。
 *   adb shell am broadcast -a com.dotpopart.app.debug.SET_TOY -p com.dotpopart.app --es plan legacy:120,saver:120,pause:150
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as DotPopArtApp).settings
        val toy = intent.getStringExtra("toy")
        val brightness = intent.getIntExtra("brightness", -1)
        intent.getStringExtra("pattern")?.let { p ->
            if (p == "off") TestPattern.clear() else TestPattern.show(p.split(',').mapNotNull { it.trim().toIntOrNull() })
        }
        intent.getStringExtra("cueApp")?.let { AppCue.forcedApp = it }
        intent.getStringExtra("cue")?.let { AppCue.forced = when (it) { "on" -> true; "off" -> false; else -> null } }
        intent.getStringExtra("pause")?.let { DebugFlags.pause = it == "on" }
        intent.getStringExtra("saver")?.let { DebugFlags.legacy = it == "off" }
        intent.getStringExtra("plan")?.let { DebugFlags.startPlan(it, SystemClock.elapsedRealtime()) }
        val done = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (toy != null) {
                    if (ToyCatalog[toy] != null) repo.applyToy(toy, emptyMap()) else Log.w("DPA", "debug: unknown toy $toy")
                }
                if (brightness in 0..3) repo.setBrightness(brightness)
            } catch (e: Exception) {
                Log.e("DPA", "debug: write failed", e)
            } finally {
                done.finish()
            }
        }
    }
}
