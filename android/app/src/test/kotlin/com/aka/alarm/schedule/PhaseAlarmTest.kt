package com.aka.alarm.schedule

import com.aka.alarm.Tuning
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class PhaseAlarmTest {

    private val minLead = Tuning.baselineWindow.inWholeMinutes.toInt()

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val start = epoch(2026, Calendar.AUGUST, 13, 11, 15)
    private val end = start + Tuning.wakeWindowDuration.inWholeMilliseconds

    // ----- requestFor ---------------------------------------------------------

    @Test
    fun requestFor_armed_firesAtActivationLead() {
        val request = PhaseAlarm.requestFor(AlarmPhase.Armed(start, end), 60)!!
        assertEquals(AlarmPhase.Kind.ARMED, request.fromKind)
        assertEquals(start, request.startMillis)
        assertEquals(end, request.endMillis)
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 10, 15), request.fireAtMillis)
        assertEquals(AlarmSchedule.baselineStartMillis(start, 60), request.fireAtMillis)
    }

    @Test
    fun requestFor_armed_shortLeadIsClampedToBaselineWindow() {
        val request = PhaseAlarm.requestFor(AlarmPhase.Armed(start, end), minLead)!!
        assertEquals(epoch(2026, Calendar.AUGUST, 13, 11, 10), request.fireAtMillis)
    }

    @Test
    fun requestFor_armed_immediateLeadHasNoAlarm() {
        assertNull(PhaseAlarm.requestFor(AlarmPhase.Armed(start, end), -1))
    }

    @Test
    fun requestFor_monitoring_firesAtWindowStart() {
        val request = PhaseAlarm.requestFor(AlarmPhase.Monitoring(start, end), 60)!!
        assertEquals(AlarmPhase.Kind.MONITORING, request.fromKind)
        assertEquals(start, request.fireAtMillis)
    }

    @Test
    fun requestFor_inWindow_firesAtWindowEnd() {
        val request = PhaseAlarm.requestFor(AlarmPhase.InWindow(start, end), 60)!!
        assertEquals(AlarmPhase.Kind.IN_WINDOW, request.fromKind)
        assertEquals(end, request.fireAtMillis)
    }

    @Test
    fun requestFor_snoozing_firesAtSnoozeDeadline() {
        val until = start + 7 * 60_000L
        val request = PhaseAlarm.requestFor(AlarmPhase.Snoozing(until, end), 60)!!
        assertEquals(AlarmPhase.Kind.SNOOZING, request.fromKind)
        assertEquals(until, request.fireAtMillis)
        assertEquals(end, request.endMillis)
    }

    @Test
    fun requestFor_idleAndAlarming_haveNoTimedTransition() {
        assertNull(PhaseAlarm.requestFor(AlarmPhase.Idle, 60))
        assertNull(PhaseAlarm.requestFor(AlarmPhase.Alarming(end), 60))
    }

    // ----- nextPhase ----------------------------------------------------------

    @Test
    fun nextPhase_followsTheStateMachine() {
        assertEquals(
            AlarmPhase.Monitoring(start, end),
            PhaseAlarm.nextPhase(AlarmPhase.Armed(start, end)),
        )
        assertEquals(
            AlarmPhase.InWindow(start, end),
            PhaseAlarm.nextPhase(AlarmPhase.Monitoring(start, end)),
        )
        assertEquals(
            AlarmPhase.Alarming(end),
            PhaseAlarm.nextPhase(AlarmPhase.InWindow(start, end)),
        )
        assertEquals(
            AlarmPhase.Alarming(end),
            PhaseAlarm.nextPhase(AlarmPhase.Snoozing(start + 60_000L, end)),
        )
        assertNull(PhaseAlarm.nextPhase(AlarmPhase.Idle))
        assertNull(PhaseAlarm.nextPhase(AlarmPhase.Alarming(end)))
    }

    // ----- transitionFor ------------------------------------------------------

    @Test
    fun transitionFor_acceptsMatchingArmedRequest() {
        val phase = AlarmPhase.Armed(start, end)
        val fired = PhaseAlarm.requestFor(phase, 60)!!
        assertEquals(
            AlarmPhase.Monitoring(start, end),
            PhaseAlarm.transitionFor(phase, fired, 60),
        )
    }

    @Test
    fun transitionFor_acceptsMatchingSnoozeRequest() {
        val phase = AlarmPhase.Snoozing(start + 3 * 60_000L, end)
        val fired = PhaseAlarm.requestFor(phase, 60)!!
        assertEquals(AlarmPhase.Alarming(end), PhaseAlarm.transitionFor(phase, fired, 60))
    }

    @Test
    fun transitionFor_rejectsRequestFromAnotherPhase() {
        val fired = PhaseAlarm.requestFor(AlarmPhase.Armed(start, end), 60)!!
        assertNull(PhaseAlarm.transitionFor(AlarmPhase.Monitoring(start, end), fired, 60))
        assertNull(PhaseAlarm.transitionFor(AlarmPhase.Idle, fired, 60))
        assertNull(PhaseAlarm.transitionFor(AlarmPhase.Alarming(end), fired, 60))
    }

    @Test
    fun transitionFor_rejectsStaleWindow() {
        val otherStart = start + 60_000L
        val fired = PhaseAlarm.requestFor(
            AlarmPhase.Armed(otherStart, otherStart + (end - start)),
            60,
        )!!
        assertNull(PhaseAlarm.transitionFor(AlarmPhase.Armed(start, end), fired, 60))
    }

    @Test
    fun transitionFor_rejectsRequestFromOldLeadAfterSettingChanged() {
        // User re-tunes the lead while Armed: the store reschedules, and the
        // previously queued intent (old fireAt) must be ignored if it fires.
        val phase = AlarmPhase.Armed(start, end)
        val staleFired = PhaseAlarm.requestFor(phase, 60)!!
        assertNull(PhaseAlarm.transitionFor(phase, staleFired, 480))
    }

    @Test
    fun transitionFor_rejectsSupersededSnooze() {
        // A second nudge replaced the snooze; the first deadline must not ring.
        val first = PhaseAlarm.requestFor(AlarmPhase.Snoozing(start + 60_000L, end), 60)!!
        val current = AlarmPhase.Snoozing(start + 5 * 60_000L, end)
        assertNull(PhaseAlarm.transitionFor(current, first, 60))
    }

    @Test
    fun transitionFor_ignoresLeadForPhasesThatDoNotUseIt() {
        // Only the Armed exit depends on the activation lead; a snooze scheduled
        // before the user re-tuned it must still ring on time.
        val phase = AlarmPhase.Snoozing(start + 60_000L, end)
        val fired = PhaseAlarm.requestFor(phase, 60)!!
        assertEquals(AlarmPhase.Alarming(end), PhaseAlarm.transitionFor(phase, fired, 480))
    }

    @Test
    fun pendingIntentRequestCode_isStable() {
        assertEquals(0x50484153, PhaseAlarm.REQUEST_CODE)
    }
}
