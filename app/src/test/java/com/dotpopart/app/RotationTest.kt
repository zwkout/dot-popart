package com.dotpopart.app

import com.dotpopart.app.data.AppSettings
import com.dotpopart.app.data.Rotation
import com.dotpopart.app.data.RotationInterval
import com.dotpopart.app.data.RotationOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RotationTest {
    private val hour = 60 * 60_000L
    private val base = AppSettings(activeToy = "pong", rotationCursor = "pong", rotationLastSwitch = 1_000_000L)

    @Test
    fun listOrderFollowsCatalog() {
        assertEquals(listOf("pong", "life", "globe", "meteor"), Rotation.list(base))
        assertEquals("life", Rotation.next(base))
        assertEquals("pong", Rotation.next(base.copy(activeToy = "meteor", rotationCursor = "meteor")))
    }

    @Test
    fun dueAfterInterval() {
        assertFalse(Rotation.due(base, base.rotationLastSwitch + hour - 1, flipped = false))
        assertTrue(Rotation.due(base, base.rotationLastSwitch + hour, flipped = false))
        val quarter = base.copy(rotationInterval = RotationInterval.MIN15)
        assertTrue(Rotation.due(quarter, base.rotationLastSwitch + 15 * 60_000L, flipped = false))
    }

    @Test
    fun flipIntervalOnlyOnFlip() {
        val flip = base.copy(rotationInterval = RotationInterval.FLIP)
        assertFalse(Rotation.due(flip, base.rotationLastSwitch + 10 * hour, flipped = false))
        assertTrue(Rotation.due(flip, base.rotationLastSwitch, flipped = true))
    }

    @Test
    fun notDueWhenOffOrEmptyOrNeverStarted() {
        assertFalse(Rotation.due(base.copy(rotationEnabled = false), base.rotationLastSwitch + 10 * hour, false))
        assertFalse(Rotation.due(base.copy(rotationToys = emptySet()), base.rotationLastSwitch + 10 * hour, false))
        assertFalse(Rotation.due(base.copy(rotationLastSwitch = 0L), 10 * hour, false))
    }

    /** ✓ で割り込んでも、次の切り替えではローテーションの続き（cursor の次）に戻る。 */
    @Test
    fun interruptReturnsToCursor() {
        val interrupted = base.copy(activeToy = "maze", rotationCursor = "life")
        assertEquals("globe", Rotation.next(interrupted))
    }

    @Test
    fun randomPicksAnotherToy() {
        val r = base.copy(rotationOrder = RotationOrder.RANDOM)
        repeat(50) { assertNotEquals("pong", Rotation.next(r, Random(it))) }
        val single = r.copy(rotationToys = setOf("pong"))
        assertEquals("pong", Rotation.next(single, Random(1)))
    }

    @Test
    fun manualNextWithoutRotationWalksCatalog() {
        val off = base.copy(rotationEnabled = false, activeToy = "battery")
        assertEquals("pong", Rotation.manualNext(off))
        assertEquals("othello", Rotation.manualNext(off.copy(activeToy = "pong")))
    }
}
