package com.aka.alarm.model

import com.aka.alarm.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmScheduleTest {

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun baselineStart_isOneBaselineWindowBeforeWindowStart() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        assertEquals(
            start - Tuning.baselineWindow.inWholeMilliseconds,
            AlarmSchedule.baselineStartMillis(start),
        )
    }

    @Test
    fun initialPhase_armedWhenWellBeforeBaseline() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val now = epoch(2026, Calendar.AUGUST, 12, 22, 0)
        assertTrue(AlarmSchedule.initialPhase(now, start, end) is AlarmPhase.Armed)
    }

    @Test
    fun initialPhase_monitoringDuringBaselineInterval() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val now = epoch(2026, Calendar.AUGUST, 13, 7, 58)
        assertTrue(AlarmSchedule.initialPhase(now, start, end) is AlarmPhase.Monitoring)
    }

    @Test
    fun initialPhase_monitoringAtBaselineStartBoundary() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val now = AlarmSchedule.baselineStartMillis(start)
        assertTrue(AlarmSchedule.initialPhase(now, start, end) is AlarmPhase.Monitoring)
    }

    @Test
    fun initialPhase_inWindowAfterStart() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val now = epoch(2026, Calendar.AUGUST, 13, 8, 5)
        assertTrue(AlarmSchedule.initialPhase(now, start, end) is AlarmPhase.InWindow)
    }

    @Test
    fun initialPhase_inWindowAtWindowStartBoundary() {
        val start = epoch(2026, Calendar.AUGUST, 13, 8, 0)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        assertTrue(AlarmSchedule.initialPhase(start, start, end) is AlarmPhase.InWindow)
    }

    @Test
    fun computeWakeWindow_rollsToNextDayWhenWindowPassed() {
        val now = epoch(2026, Calendar.AUGUST, 13, 9, 0)
        val (start, end) = AlarmSchedule.computeWakeWindow(now, 8, 0)
        assertEquals(epoch(2026, Calendar.AUGUST, 14, 8, 0), start)
        assertEquals(
            epoch(2026, Calendar.AUGUST, 14, 8, 0) + Tuning.wakeWindowDuration.inWholeMilliseconds,
            end,
        )
    }
}
