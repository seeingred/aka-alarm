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

    private val minLead = Tuning.baselineWindow.inWholeMinutes.toInt()

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun request_baselineAtMatchesSchedule() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val request = BaselineStartAlarm.Request(start, end, minLead)
        assertEquals(AlarmSchedule.baselineStartMillis(start, minLead), request.baselineAtMillis)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 11, 10), request.baselineAtMillis)
    }

    @Test
    fun request_baselineAtHonoursConfiguredLead() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val request = BaselineStartAlarm.Request(start, end, 60)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 10, 15), request.baselineAtMillis)
    }

    @Test
    fun requestFrom_armedWindow() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val armed = AlarmPhase.Armed(start, end)
        val request = BaselineStartAlarm.requestFrom(armed, 60)
        assertEquals(start, request.windowStartMillis)
        assertEquals(end, request.windowEndMillis)
        assertEquals(60, request.activationLeadMinutes)
    }

    @Test
    fun shouldTransitionToMonitoring_acceptsMatchingArmedPhase() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val baselineAt = AlarmSchedule.baselineStartMillis(start, 60)
        val phase = AlarmPhase.Armed(start, end)
        assertTrue(
            BaselineStartAlarm.shouldTransitionToMonitoring(phase, start, end, baselineAt, 60),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsWrongPhase() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val baselineAt = AlarmSchedule.baselineStartMillis(start, minLead)
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                AlarmPhase.Monitoring(start, end),
                start,
                end,
                baselineAt,
                minLead,
            ),
        )
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                AlarmPhase.Idle,
                start,
                end,
                baselineAt,
                minLead,
            ),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsStaleWindow() {
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val phase = AlarmPhase.Armed(start, end)
        val otherStart = start + 60_000L
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                phase,
                otherStart,
                end,
                AlarmSchedule.baselineStartMillis(otherStart, minLead),
                minLead,
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
                AlarmSchedule.baselineStartMillis(start, minLead) + 1,
                minLead,
            ),
        )
    }

    @Test
    fun shouldTransitionToMonitoring_rejectsIntentFromOldLeadAfterSettingChanged() {
        // User re-tunes the lead while Armed: the store reschedules, and the
        // previously queued intent (old baselineAt) must be ignored if it fires.
        val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
        val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        val phase = AlarmPhase.Armed(start, end)
        val staleBaselineAt = AlarmSchedule.baselineStartMillis(start, 60)
        assertFalse(
            BaselineStartAlarm.shouldTransitionToMonitoring(
                phase,
                start,
                end,
                staleBaselineAt,
                480,
            ),
        )
    }

    @Test
    fun pendingIntentRequestCode_isStable() {
        assertEquals(0x42415345, BaselineStartAlarm.REQUEST_CODE)
    }
}
