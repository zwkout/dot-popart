package com.dotpopart.app.data

import com.dotpopart.engine.AppCueAnim
import com.dotpopart.engine.AppCueAnims
import com.dotpopart.engine.Geo

/**
 * アプリ連動の対象アプリ。開いている間だけ、おもちゃの代わりに [animId] のアニメーションを出す。
 * @property id 設定のキー（`appCue.<id>`）に使う。
 * @property animId アニメーションの id（[AppCueAnims]）。Netflix と Prime Video は同じ。
 */
class AppCueTarget(val id: String, val packageName: String, val label: String, val animId: String) {
    fun anim(geo: Geo): AppCueAnim = AppCueAnims.create(animId, geo)
}

/** 並びはアプリ連動の画面の順（docs/app-cue.md の 4.1）。 */
object AppCueTargets {
    val all = listOf(
        AppCueTarget("vwfndr", "com.vwfndr.mbl", "vwfndr", "b2_lock"),
        AppCueTarget("shazam", "com.shazam.android", "Shazam", "shz_b"),
        AppCueTarget("termux", "com.termux", "Termux", "prompt"),
        AppCueTarget("flightradar24", "com.flightradar24free", "Flightradar24", "radar"),
        AppCueTarget("lens", "com.google.ar.lens", "Google Lens", "scan_d"),
        AppCueTarget("maps", "com.google.android.apps.maps", "Google Maps", "nav_d"),
        AppCueTarget("github", "com.github.android", "GitHub", "git_b"),
        AppCueTarget("obsidian", "md.obsidian", "Obsidian", "link_a"),
        AppCueTarget("speedtest", "org.zwanoo.android.speedtest", "Speedtest", "gauge"),
        AppCueTarget("netflix", "com.netflix.mediaclient", "Netflix", "curtain_m"),
        AppCueTarget("primevideo", "com.amazon.avod.thirdpartyclient", "Prime Video", "curtain_m"),
        AppCueTarget("recorder", "com.nothing.soundrecorder", "Recorder", "tape_a"),
        AppCueTarget("essential", "com.nothing.ntessentialspace", "Essential Space", "ess_d"),
        AppCueTarget("pellica", "com.imad.rollify", "Pellica", "film_b"),
    )

    fun ofPackage(packageName: String?): AppCueTarget? = all.firstOrNull { it.packageName == packageName }

    fun byId(id: String?): AppCueTarget? = all.firstOrNull { it.id == id }
}
