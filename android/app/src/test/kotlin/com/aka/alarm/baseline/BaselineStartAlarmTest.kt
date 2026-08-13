package com.aka.alarm.baseline

import com.aka.alarm.Tuning
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BaselineStartAlarmTest {

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun request_baselineAtMatchesSchedule() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val request = BaselineStartAlarm.Request(start, end)
        assertEquals(AlarmSchedule.baselineStartMillis(start), request.baselineAtMillis)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 11, 10), request.baselineAtMillis)
    }

    @Test
    fun requestFrom_armedWindow() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val armed = AlarmPhase.Armed(start, end)
        val request = BaselineStartAlarm.requestFrom(armed)
        assertEquals(start, request.windowStartMillis)
        assertEquals(end, request.windowEndMillis)
    }

    @Test
    fun shouldTransitionToMonitoring_acceptsMatchingArmedPhase() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val baselineAt = AlarmSchedule.baselineStartMillis(start)
        val phase = AlarmPhase.Armed(start, end)
        assertTrue(
            BaselineStartAlarm.shouldTransitionToMonitoring(phase, start, end, baselineAt),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsWrongPhase() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val baselineAt = AlarmSchedule.baselineStartMillis(start)
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                AlarmPhase.Monitoring(start, end),
                start,
                end,
                baselineAt,
            ),
        )
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                AlarmPhase.Idle,
                start,
                end,
                baselineAt,
            ),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsStaleWindow() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val baselineAt = AlarmSchedule.baselineStartMillis(start)
        val phase = AlarmPhase.Armed(start, end)
        val otherStart = start + 60_000L
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                phase,
                otherStart,
                end,
                AlarmSchedule.baselineStartMillis(otherStart),
            ),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsMismatchedBaselineAt() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val phase = AlarmPhase.Armed(start, end)
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                phase,
                start,
                end,
                AlarmSchedule.baselineStartMillis(start) + 1,
            ),
        )
    }

    @Test
    fun pendingIntentRequestCode_isStable() {
        assertEquals(0x42415345, BaselineStartAlarm.REQUEST_CODE)
    }
}
