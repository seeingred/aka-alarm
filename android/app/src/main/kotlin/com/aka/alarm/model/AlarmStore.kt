package com.aka.alarm.model

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.aka.alarm.Tuning
import com.aka.alarm.audio.AlarmPlayer
import com.aka.alarm.audio.MicMonitor
import com.aka.alarm.motion.MotionMonitor
import com.aka.alarm.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/**
 * Singleton-ish state machine for the alarm. Mirrors the iOS `AlarmStore`:
 *   idle → monitoring → inWindow → alarming ⇄ snoozing → … → idle
 *
 * Owns the lifecycle of [MicMonitor], [AlarmPlayer], [MotionMonitor]. Starts and
 * stops [AlarmService] when entering/leaving non-idle phases so the foreground
 * notification + microphone stay alive while the screen is off.
 */
class AlarmStore(private val app: Application) {

    var phase by mutableStateOf<AlarmPhase>(AlarmPhase.Idle)
        private set

    var micLevelDb by mutableDoubleStateOf(Tuning.DB_FLOOR)
        private set

    var baselineDb by mutableDoubleStateOf(Tuning.DB_FLOOR)
        private set

    var selectedHour by mutableIntStateOf(7)
    var selectedMinute by mutableIntStateOf(0)

    var micPermissionDenied by mutableStateOf(false)

    private val mic = MicMonitor().apply {
        onLevelUpdate = { micLevelDb = it }
        onBaselineUpdate = { baselineDb = it }
        onSpike = { handleSpike() }
    }
    private val motion = MotionMonitor(app).apply {
        onSnoozeNudge = { handleNudge() }
    }
    private val player = AlarmPlayer(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var phaseJob: Job? = null

    private val prefs: SharedPreferences =
        app.getSharedPreferences("akaalarm", Context.MODE_PRIVATE)

    init {
        val savedHour = prefs.getInt(KEY_HOUR, -1)
        val savedMinute = prefs.getInt(KEY_MINUTE, -1)
        if (savedHour in 0..23 && savedMinute in setOf(0, 15, 30, 45)) {
            selectedHour = savedHour
            selectedMinute = savedMinute
        } else {
            resetSelectedToCurrentWindow()
        }
    }

    // MARK: User actions

    fun startAlarm(now: Long = System.currentTimeMillis()) {
        persistSelection()
        val (start, end) = computeWindow(now)
        if (now >= start) {
            transition(AlarmPhase.InWindow(start, end))
        } else {
            transition(AlarmPhase.Monitoring(start, end))
        }
    }

    private fun persistSelection() {
        prefs.edit {
            putInt(KEY_HOUR, selectedHour)
            putInt(KEY_MINUTE, selectedMinute)
        }
    }

    private companion object {
        const val KEY_HOUR = "selectedHour"
        const val KEY_MINUTE = "selectedMinute"
    }

    fun cancelAlarm() {
        transition(AlarmPhase.Idle)
    }

    /** Snap pickers to the 15-minute boundary containing "now". */
    fun resetSelectedToCurrentWindow(now: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        selectedHour = cal.get(Calendar.HOUR_OF_DAY)
        selectedMinute = (cal.get(Calendar.MINUTE) / 15) * 15
    }

    // MARK: Internal events

    private fun handleSpike() {
        val p = phase
        if (p is AlarmPhase.InWindow) {
            transition(AlarmPhase.Alarming(p.end))
        }
    }

    private fun handleNudge() {
        val p = phase
        if (p is AlarmPhase.Alarming) {
            val now = System.currentTimeMillis()
            val remaining = p.end - now
            if (remaining < Tuning.snoozeMinDuration.inWholeMilliseconds) return
            val upper = minOf(Tuning.snoozeMaxDuration.inWholeMilliseconds, remaining)
            val duration = Random.nextLong(
                Tuning.snoozeMinDuration.inWholeMilliseconds,
                upper + 1
            )
            transition(
                AlarmPhase.Snoozing(
                    until = now + duration,
                    end = p.end
                )
            )
        }
    }

    // MARK: State machine

    private fun transition(next: AlarmPhase) {
        phaseJob?.cancel()
        phaseJob = null

        val previouslyActive = phase !is AlarmPhase.Idle
        val nextActive = next !is AlarmPhase.Idle

        when (next) {
            AlarmPhase.Idle -> {
                mic.stop()
                motion.stop()
                player.stop()
                micLevelDb = Tuning.DB_FLOOR
                baselineDb = Tuning.DB_FLOOR
            }
            is AlarmPhase.Monitoring -> {
                if (!mic.isRunning) mic.start()
                mic.spikeDetectionEnabled = false
                motion.stop()
                player.stop()
                scheduleTransition(next.start) {
                    (phase as? AlarmPhase.Monitoring)?.let {
                        transition(AlarmPhase.InWindow(it.start, it.end))
                    }
                }
            }
            is AlarmPhase.InWindow -> {
                if (!mic.isRunning) mic.start()
                mic.spikeDetectionEnabled = true
                motion.stop()
                player.stop()
                scheduleTransition(next.end) {
                    (phase as? AlarmPhase.InWindow)?.let {
                        transition(AlarmPhase.Alarming(it.end))
                    }
                }
            }
            is AlarmPhase.Alarming -> {
                mic.stop()
                motion.start()
                player.start()
            }
            is AlarmPhase.Snoozing -> {
                motion.stop()
                player.stop()
                scheduleTransition(next.until) {
                    (phase as? AlarmPhase.Snoozing)?.let {
                        transition(AlarmPhase.Alarming(it.end))
                    }
                }
            }
        }

        phase = next

        // Service follows the phase so the persistent notification + mic stay alive
        // through screen-off / app-backgrounded.
        if (nextActive && !previouslyActive) {
            ContextCompat.startForegroundService(app, Intent(app, AlarmService::class.java))
        } else if (!nextActive && previouslyActive) {
            app.stopService(Intent(app, AlarmService::class.java))
        }
    }

    private fun scheduleTransition(targetEpochMillis: Long, action: () -> Unit) {
        val delayMs = (targetEpochMillis - System.currentTimeMillis()).coerceAtLeast(10)
        phaseJob = scope.launch {
            delay(delayMs)
            action()
        }
    }

    private fun computeWindow(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var start = cal.timeInMillis
        var end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        // Only roll forward a day if the *entire* window has already passed.
        if (end <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            start = cal.timeInMillis
            end = start + Tuning.wakeWindowDuration.inWholeMilliseconds
        }
        return start to end
    }
}
