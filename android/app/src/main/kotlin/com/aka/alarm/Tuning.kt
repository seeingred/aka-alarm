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
    /**
     * How far above baseline (in dB) the *peak* moment in a cell must exceed for
     * us to call it a spike. Using peak rather than the cell mean lets brief
     * sheet rustles trigger the alarm — they'd otherwise vanish in a 1 s average.
     */
    const val SPIKE_THRESHOLD_DB: Double = 4.5
    const val MIN_BASELINE_CELLS: Int = 10
    const val LEVEL_METER_HZ: Double = 30.0
    /** Detection floor: dB values below this clamp to silence. */
    const val DB_FLOOR: Double = -80.0
    /** Display-only floor for the level meter. Tighter so subtle movement is visible. */
    const val DISPLAY_DB_FLOOR: Double = -60.0

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
