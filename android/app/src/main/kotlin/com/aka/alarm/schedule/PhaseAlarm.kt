package com.aka.alarm.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aka.alarm.model.AlarmPhase
import com.aka.alarm.model.AlarmSchedule

/**
 * Exact-alarm scheduling for every *timed* phase transition:
 *
 *   Armed → Monitoring   at the activation lead
 *   Monitoring → InWindow at window start
 *   InWindow → Alarming   at window end
 *   Snoozing → Alarming   at the snooze deadline
 *
 * Coroutine `delay()` runs on the uptime clock, which stops while the CPU
 * deep-sleeps, and a foreground service alone does not keep the CPU awake.
 * While Armed or Snoozing nothing else does either (mic, player and motion
 * sensor are all off), so an in-process timer can fire hours late.
 * [AlarmManager.setExactAndAllowWhileIdle] with `RTC_WAKEUP` is the supported
 * way to be woken on time. Only one transition is ever pending, so a single
 * request code is enough.
 */
object PhaseAlarm {

    const val ACTION = "com.aka.alarm.action.PHASE_TRANSITION"
    const val EXTRA_FROM_KIND = "fromKind"
    const val EXTRA_START = "start"
    const val EXTRA_END = "end"
    const val EXTRA_FIRE_AT = "fireAt"
    const val REQUEST_CODE = 0x50484153 // "PHAS" — single pending transition at a time

    /**
     * The next timed transition out of a phase. [startMillis]/[endMillis] are
     * the wake window; for [AlarmPhase.Kind.SNOOZING], which has no window
     * start of its own, [startMillis] carries the snooze deadline instead.
     * Equality is the staleness check: a request built from the current phase
     * and lead must equal the one that was scheduled.
     */
    data class Request(
        val fromKind: AlarmPhase.Kind,
        val startMillis: Long,
        val endMillis: Long,
        val fireAtMillis: Long,
    )

    /** Next timed transition for [phase], or null when the phase has none. */
    fun requestFor(phase: AlarmPhase, activationLeadMinutes: Int): Request? = when (phase) {
        is AlarmPhase.Armed -> {
            val at = AlarmSchedule.baselineStartMillis(phase.start, activationLeadMinutes)
            // "Right after starting" never enters Armed; guard anyway.
            if (at == Long.MIN_VALUE) null
            else Request(AlarmPhase.Kind.ARMED, phase.start, phase.end, at)
        }
        is AlarmPhase.Monitoring ->
            Request(AlarmPhase.Kind.MONITORING, phase.start, phase.end, phase.start)
        is AlarmPhase.InWindow ->
            Request(AlarmPhase.Kind.IN_WINDOW, phase.start, phase.end, phase.end)
        is AlarmPhase.Snoozing ->
            Request(AlarmPhase.Kind.SNOOZING, phase.until, phase.end, phase.until)
        AlarmPhase.Idle, is AlarmPhase.Alarming -> null
    }

    /** The phase a timed transition out of [phase] leads to, or null if it has none. */
    fun nextPhase(phase: AlarmPhase): AlarmPhase? = when (phase) {
        is AlarmPhase.Armed -> AlarmPhase.Monitoring(phase.start, phase.end)
        is AlarmPhase.Monitoring -> AlarmPhase.InWindow(phase.start, phase.end)
        is AlarmPhase.InWindow -> AlarmPhase.Alarming(phase.end)
        is AlarmPhase.Snoozing -> AlarmPhase.Alarming(phase.end)
        AlarmPhase.Idle, is AlarmPhase.Alarming -> null
    }

    /**
     * Phase to enter when [fired] is delivered while in [current], or null when
     * the request is stale: the phase moved on or was cancelled, the window
     * differs, or it was scheduled under a different activation lead.
     * [activationLeadMinutes] is the *current* setting; the store re-arms the
     * alarm whenever it changes, so an intent from before the change no longer
     * matches and is dropped in favour of the rescheduled one.
     */
    fun transitionFor(
        current: AlarmPhase,
        fired: Request,
        activationLeadMinutes: Int,
    ): AlarmPhase? {
        val expected = requestFor(current, activationLeadMinutes) ?: return null
        if (expected != fired) return null
        return nextPhase(current)
    }

    fun intent(context: Context, request: Request): Intent =
        Intent(context, PhaseAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_FROM_KIND, request.fromKind.name)
            putExtra(EXTRA_START, request.startMillis)
            putExtra(EXTRA_END, request.endMillis)
            putExtra(EXTRA_FIRE_AT, request.fireAtMillis)
        }

    /** Inverse of [intent]; null if the extras are missing or malformed. */
    fun requestFrom(intent: Intent): Request? {
        val kind = intent.getStringExtra(EXTRA_FROM_KIND)
            ?.let { name -> AlarmPhase.Kind.entries.firstOrNull { it.name == name } }
            ?: return null
        val start = intent.getLongExtra(EXTRA_START, -1L)
        val end = intent.getLongExtra(EXTRA_END, -1L)
        val fireAt = intent.getLongExtra(EXTRA_FIRE_AT, -1L)
        if (start < 0 || end < 0 || fireAt < 0) return null
        return Request(kind, start, end, fireAt)
    }

    fun pendingIntent(context: Context, request: Request?, flags: Int): PendingIntent? {
        val intent = if (request != null) {
            intent(context, request)
        } else {
            Intent(context, PhaseAlarmReceiver::class.java).apply { action = ACTION }
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class PhaseAlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(request: PhaseAlarm.Request) {
        val pendingIntent = PhaseAlarm.pendingIntent(
            context,
            request,
            PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return
        // Below API 31 exact alarms need no permission at all. On 31–32
        // SCHEDULE_EXACT_ALARM is granted by default but user-revocable; calling
        // setExactAndAllowWhileIdle without it throws SecurityException.
        // USE_EXACT_ALARM (33+) can't be revoked, but keep the guard uniform.
        // (canScheduleExactAlarms itself only exists on 31+.) Inexact delivery
        // still exits Doze, just possibly minutes late — the in-process fallback
        // timer in AlarmStore covers the same gap.
        val exactAllowed = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (_: SecurityException) {
                false
            }
        }
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    request.fireAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    request.fireAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // Revoked between check and call — fall back to inexact.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                request.fireAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel() {
        val pendingIntent = PhaseAlarm.pendingIntent(
            context,
            request = null,
            PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
