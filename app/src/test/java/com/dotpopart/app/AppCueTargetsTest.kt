package com.dotpopart.app

import com.dotpopart.app.data.AppCueTargets
import com.dotpopart.engine.AppCueAnims
import com.dotpopart.engine.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCueTargetsTest {
    /**
     * 対象アプリのアニメーションの id は、文字列でアニメーションの一覧と結んでいる。食い違うと、
     * そのアプリを開いた瞬間に例外になるので、全部が作れることをここで確かめる。
     */
    @Test
    fun everyTargetHasAnAnimation() {
        for (t in AppCueTargets.all) {
            assertTrue("${t.id}: ${t.animId}", t.animId in AppCueAnims.ids)
            for (geo in listOf(Geo.PHONE_4A_PRO, Geo.PHONE_3)) assertEquals(geo, t.anim(geo).geo)
        }
    }

    @Test
    fun idsAndPackagesAreUnique() {
        assertEquals(AppCueTargets.all.size, AppCueTargets.all.map { it.id }.toSet().size)
        assertEquals(AppCueTargets.all.size, AppCueTargets.all.map { it.packageName }.toSet().size)
    }
}
