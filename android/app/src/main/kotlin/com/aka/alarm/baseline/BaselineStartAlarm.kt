package com.aka.alarm.baseline

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule

/**
 * Exact-alarm scheduling for the Armed → Monitoring transition.
 *
 * Coroutine [delay] is deferred under Doze even with a foreground service; the OS
 * can batch in-process timers until maintenance windows. [AlarmManager.setExactAndAllowWhileIdle]
 * is the supported way to wake at [AlarmSchedule.baselineStartMillis] so mic analysis
 * begins [com.aka.alarm.Tuning.baselineWindow] before the wake window without starting early.
 */
object BaselineStartAlarm {

    const val ACTION = "com.aka.alarm.action.BASELINE_START"
    const val EXTRA_WINDOW_START = "windowStart"
    const val EXTRA_WINDOW_END = "windowEnd"
    const val EXTRA_BASELINE_AT = "baselineAt"
    const val REQUEST_CODE = 0x42415345 // "BASE" — single pending baseline alarm at a time

    data class Request(
        val windowStartMillis: Long,
        val windowEndMillis: Long,
        val activationLeadMinutes: Int,
    ) {
        val baselineAtMillis: Long =
            AlarmSchedule.baselineStartMillis(windowStartMillis, activationLeadMinutes)
    }

    /**
     * True when the receiver should move [phase] from Armed into Monitoring.
     * [activationLeadMinutes] is the *current* setting; [AlarmStore] re-arms the
     * alarm whenever the setting changes, so a fired intent carrying a stale
     * baselineAt is rejected here and superseded by the rescheduled one.
     */
    fun shouldTransitionToMonitoring(
        phase: AlarmPhase,
        windowStartMillis: Long,
        windowEndMillis: Long,
        baselineAtMillis: Long,
        activationLeadMinutes: Int,
    ): Boolean {
        if (phase !is AlarmPhase.Armed) return false
        if (phase.start != windowStartMillis || phase.end != windowEndMillis) return false
        if (baselineAtMillis !=
            AlarmSchedule.baselineStartMillis(windowStartMillis, activationLeadMinutes)
        ) {
            return false
        }
        return true
    }

    fun requestFrom(armed: AlarmPhase.Armed, activationLeadMinutes: Int): Request =
        Request(armed.start, armed.end, activationLeadMinutes)

    fun intent(context: Context, request: Request): Intent =
        Intent(context, BaselineStartReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_WINDOW_START, request.windowStartMillis)
            putExtra(EXTRA_WINDOW_END, request.windowEndMillis)
            putExtra(EXTRA_BASELINE_AT, request.baselineAtMillis)
        }

    fun pendingIntent(context: Context, request: Request?, flags: Int): PendingIntent? {
        val intent = if (request != null) {
            intent(context, request)
        } else {
            Intent(context, BaselineStartReceiver::class.java).apply { action = ACTION }
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class BaselineStartAlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(request: BaselineStartAlarm.Request) {
        // "Right after starting" never enters Armed, but guard anyway.
        if (request.baselineAtMillis == Long.MIN_VALUE) return
        val pendingIntent = BaselineStartAlarm.pendingIntent(
            context,
            request,
            PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        // SCHEDULE_EXACT_ALARM (API 31–32) is granted by default but user-revocable;
        // calling setExactAndAllowWhileIdle without it throws SecurityException.
        // USE_EXACT_ALARM (33+) can't be revoked, but keep the guard uniform.
        // Inexact delivery still exits Doze, just possibly minutes late — the
        // in-process fallback timer in AlarmStore covers the same gap.
        val exactAllowed = try {
            alarmManager.canScheduleExactAlarms()
        } catch (_: SecurityException) {
            false
        }
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    request.baselineAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    request.baselineAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // Revoked between check and call — fall back to inexact.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                request.baselineAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel() {
        val pendingIntent = BaselineStartAlarm.pendingIntent(
            context,
            request = null,
            PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
