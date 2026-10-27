package com.dotpopart.app.data

import com.dotpopart.engine.AppCueAnim
import com.dotpopart.engine.Geo
import com.dotpopart.engine.VwfndrLockAnim

/**
 * アプリ連動の対象アプリ。開いている間だけ、おもちゃの代わりに [anim] を出す。
 * @property id 設定のキー（`appCue.<id>`）に使う。
 */
class AppCueTarget(val id: String, val packageName: String, val label: String, val anim: (Geo) -> AppCueAnim)

object AppCueTargets {
    val all = listOf(
        AppCueTarget("vwfndr", "com.vwfndr.mbl", "vwfndr", ::VwfndrLockAnim),
    )

    fun ofPackage(packageName: String?): AppCueTarget? = all.firstOrNull { it.packageName == packageName }
}
