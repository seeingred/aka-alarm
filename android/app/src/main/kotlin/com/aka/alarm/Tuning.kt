package com.aka.alarm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** All tunable behaviour lives here. Tweak values and rebuild. */
object Tuning {
    // Wake window
    val wakeWindowDuration: Duration = 30.minutes

    // Snooze
    val snoozeMinDuration: Duration = 60.seconds
    val snoozeMaxDuration: Duration = 15.minutes

    // Microphone / spike detection
    val baselineWindow: Duration = 5.minutes
    val baselineCellSeconds: Duration = 1.seconds
    const val SPIKE_THRESHOLD_DB: Double = 6.0
    const val MIN_BASELINE_CELLS: Int = 10
    const val LEVEL_METER_HZ: Double = 30.0
    const val DB_FLOOR: Double = -80.0

    // Motion / snooze nudge
    /** Gyroscope rotation magnitude (rad/s) above which a tilt counts as a snooze nudge. */
    const val SNOOZE_ROTATION_THRESHOLD: Double = 1.5
    const val MOTION_SAMPLING_HZ: Double = 50.0

    // Alarm tone
    val alarmFadeDuration: Duration = 60.seconds
    const val ALARM_START_VOLUME: Float = 0.01f
    const val ALARM_END_VOLUME: Float = 1.0f

    // Vibration
    val vibrationPulseInterval: Duration = 1500.milliseconds
}
