package com.aka.alarm.baseline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aka.alarm.AlarmApp

/**
 * Delivers the exact baseline-start wakeup scheduled while [com.aka.alarm.model.AlarmPhase.Armed].
 * Validation lives in [AlarmStore.onBaselineStartAlarmFired] so stale alarms are ignored.
 */
class BaselineStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BaselineStartAlarm.ACTION) return
        val windowStart = intent.getLongExtra(BaselineStartAlarm.EXTRA_WINDOW_START, -1L)
        val windowEnd = intent.getLongExtra(BaselineStartAlarm.EXTRA_WINDOW_END, -1L)
        val baselineAt = intent.getLongExtra(BaselineStartAlarm.EXTRA_BASELINE_AT, -1L)
        if (windowStart < 0 || windowEnd < 0 || baselineAt < 0) return

        val store = (context.applicationContext as AlarmApp).alarmStore
        store.onBaselineStartAlarmFired(windowStart, windowEnd, baselineAt)
    }
}
