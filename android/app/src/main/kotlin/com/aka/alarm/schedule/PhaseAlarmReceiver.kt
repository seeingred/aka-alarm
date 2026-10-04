package com.aka.alarm.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aka.alarm.AlarmApp

/**
 * Delivers the exact wakeup scheduled for the current phase's timed transition.
 * Validation lives in [com.aka.alarm.model.AlarmStore.onPhaseAlarmFired] so a
 * stale alarm is ignored. The system holds a wake lock for the duration of
 * [onReceive]; the transition starts the mic or the alarm player synchronously,
 * and either of those keeps the device awake from then on.
 */
class PhaseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PhaseAlarm.ACTION) return
        val request = PhaseAlarm.requestFrom(intent) ?: return
        (context.applicationContext as AlarmApp).alarmStore.onPhaseAlarmFired(request)
    }
}
