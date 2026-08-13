package com.aka.alarm.model

import com.aka.alarm.Tuning
import java.util.Calendar

/**
 * Pure schedule helpers for wake-window and deferred mic analysis.
 * [AlarmPhase.Armed] keeps the foreground service alive with no mic work;
 * mic sampling begins at [baselineStartMillis] and spike detection at window start.
 */
object AlarmSchedule {

    fun computeWakeWindow(
        nowMillis: Long,
        hour: Int,
        minute: Int,
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var start = cal.timeInMillis
        var end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        // Only roll forward a day if the *entire* window has already passed.
        if (end <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            start = cal.timeInMillis
            end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        }
        return start to end
    }

    /** Epoch millis when mic analysis should begin for the given window start. */
    fun baselineStartMillis(windowStartMillis: Long): Long =
        windowStartMillis - Tuning.baselineWindow.inWholeMilliseconds

    /** Phase to enter when the user taps Start at [nowMillis]. */
    fun initialPhase(
        nowMillis: Long,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): AlarmPhase = when {
        nowMillis >= windowStartMillis ->
            AlarmPhase.InWindow(windowStartMillis, windowEndMillis)
        nowMillis >= baselineStartMillis(windowStartMillis) ->
            AlarmPhase.Monitoring(windowStartMillis, windowEndMillis)
        else ->
            AlarmPhase.Armed(windowStartMillis, windowEndMillis)
    }
}
