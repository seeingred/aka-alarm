package com.aka.alarm.service

import com.aka.alarm.model.AlarmPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmServiceLifecycleTest {

    private val windowStart = 1_700_000_000_000L
    private val windowEnd = windowStart + 30 * 60 * 1000L

    @Test
    fun shouldStartForegroundService_onlyOnIdleToNonIdle() {
        assertTrue(
            AlarmServiceLifecycle.shouldStartForegroundService(
                AlarmPhase.Idle,
                AlarmPhase.Armed(windowStart, windowEnd),
            ),
        )
        assertFalse(
            AlarmServiceLifecycle.shouldStartForegroundService(
                AlarmPhase.Armed(windowStart, windowEnd),
                AlarmPhase.Monitoring(windowStart, windowEnd),
            ),
        )
        assertFalse(
            AlarmServiceLifecycle.shouldStartForegroundService(
                AlarmPhase.Armed(windowStart, windowEnd),
                AlarmPhase.Idle,
            ),
        )
        assertFalse(
            AlarmServiceLifecycle.shouldStartForegroundService(
                AlarmPhase.Idle,
                AlarmPhase.Idle,
            ),
        )
    }

    @Test
    fun shouldStopService_onlyOnNonIdleToIdle() {
        assertTrue(
            AlarmServiceLifecycle.shouldStopService(
                AlarmPhase.Armed(windowStart, windowEnd),
                AlarmPhase.Idle,
            ),
        )
        assertFalse(
            AlarmServiceLifecycle.shouldStopService(
                AlarmPhase.Idle,
                AlarmPhase.Armed(windowStart, windowEnd),
            ),
        )
        assertFalse(
            AlarmServiceLifecycle.shouldStopService(
                AlarmPhase.Monitoring(windowStart, windowEnd),
                AlarmPhase.InWindow(windowStart, windowEnd),
            ),
        )
    }

    @Test
    fun startCommandAction_stopsImmediatelyWhenPhaseIsIdle() {
        assertEquals(
            ServiceStartCommandAction.StopImmediately,
            AlarmServiceLifecycle.startCommandAction(AlarmPhase.Idle),
        )
    }

    @Test
    fun startCommandAction_promotesToForegroundForActivePhases() {
        assertEquals(
            ServiceStartCommandAction.PromoteToForeground,
            AlarmServiceLifecycle.startCommandAction(
                AlarmPhase.Armed(windowStart, windowEnd),
            ),
        )
        assertEquals(
            ServiceStartCommandAction.PromoteToForeground,
            AlarmServiceLifecycle.startCommandAction(
                AlarmPhase.Monitoring(windowStart, windowEnd),
            ),
        )
    }

    @Test
    fun shouldUpdateNotification_falseForIdle() {
        assertFalse(AlarmServiceLifecycle.shouldUpdateNotification(AlarmPhase.Idle))
        assertTrue(
            AlarmServiceLifecycle.shouldUpdateNotification(
                AlarmPhase.Armed(windowStart, windowEnd),
            ),
        )
    }
}
