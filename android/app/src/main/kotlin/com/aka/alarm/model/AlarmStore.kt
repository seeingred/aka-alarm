package com.aka.alarm.model

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
 *   idle → armed → monitoring → inWindow → alarming ⇄ snoozing → … → idle
 *
 * Owns the lifecycle of [MicMonitor], [AlarmPlayer], [MotionMonitor]. Starts and
 * stops [AlarmService] when entering/leaving non-idle phases so the foreground
 * notification keeps the process alive overnight; mic analysis begins only
 * [Tuning.baselineWindow] before the wake-window start.
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

    /** 0.0 = very low, 1.0 = very high. Persisted immediately on change. */
    var sensitivity by mutableFloatStateOf(Tuning.DEFAULT_SENSITIVITY)
        private set

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
        sensitivity = prefs.getFloat(KEY_SENSITIVITY, Tuning.DEFAULT_SENSITIVITY)
            .coerceIn(0f, 1f)
        mic.spikeThresholdDb = Tuning.spikeThresholdDb(sensitivity)
    }

    /** Applies live to the running mic monitor and persists straight away. */
    fun updateSensitivity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        sensitivity = v
        mic.spikeThresholdDb = Tuning.spikeThresholdDb(v)
        prefs.edit { putFloat(KEY_SENSITIVITY, v) }
    }

    // MARK: User actions

    fun startAlarm(now: Long = System.currentTimeMillis()) {
        persistSelection()
        val (start, end) = AlarmSchedule.computeWakeWindow(now, selectedHour, selectedMinute)
        transition(AlarmSchedule.initialPhase(now, start, end))
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
        const val KEY_SENSITIVITY = "sensitivity"
    }

    fun cancelAlarm() {
        transition(AlarmPhase.Idle)
    }

    // -----------------------------------------------------------------------
    // Debug-only state forcing. Used for capturing Play-listing screenshots in
    // the simulator where the mic input loop makes natural spike detection
    // unpredictable. Triggered from MainActivity's BroadcastReceiver. The
    // methods are no-ops if the emitter never broadcasts, so they're harmless
    // to leave compiled into release builds too.
    // -----------------------------------------------------------------------

    fun debugForceAlarming() {
        val end = System.currentTimeMillis() + 30 * 60 * 1000L
        transition(AlarmPhase.Alarming(end))
    }

    fun debugForceSnoozing() {
        val now = System.currentTimeMillis()
        val end = phase.windowEnd ?: (now + 30 * 60 * 1000L)
        val until = now + 3 * 60 * 1000L
        transition(AlarmPhase.Snoozing(until, end))
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
            is AlarmPhase.Armed -> {
                mic.stop()
                motion.stop()
                player.stop()
                micLevelDb = Tuning.DB_FLOOR
                baselineDb = Tuning.DB_FLOOR
                scheduleTransition(AlarmSchedule.baselineStartMillis(next.start)) {
                    (phase as? AlarmPhase.Armed)?.let {
                        transition(AlarmPhase.Monitoring(it.start, it.end))
                    }
                }
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

        // Service follows the phase so the persistent notification keeps the process
        // alive through screen-off / app-backgrounded; mic work is deferred separately.
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
}
