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

    // Mic activation lead: how long before the wake window the microphone
    // starts listening. Until then the alarm sits in the Armed phase — process
    // alive via the foreground service, mic off. -1 = start listening right
    // after the user taps Start (pre-1.1.5 behaviour, mic runs all night).
    // The minimum equals [baselineWindow] so the rolling baseline is fully
    // built by the time spike detection arms at window start.
    val ACTIVATION_LEAD_OPTIONS_MINUTES = listOf(-1, 480, 240, 120, 60, 30, 15, 5)
    const val DEFAULT_ACTIVATION_LEAD_MINUTES = 60

    fun activationLeadLabel(minutes: Int): String = when {
        minutes < 0 -> "right after starting"
        minutes >= 60 && minutes % 60 == 0 ->
            if (minutes == 60) "1 hour before the window"
            else "${minutes / 60} hours before the window"
        else -> "$minutes minutes before the window"
    }

    // Microphone / spike detection
    val baselineWindow: Duration = 5.minutes
    val baselineCellSeconds: Duration = 1.seconds
    /**
     * The spike threshold (how far above baseline, in dB, the *peak* moment in a
     * cell must rise to count as a spike) is user-tunable via the sensitivity
     * slider. Slider 0.0 (very low) maps to [SENSITIVITY_MAX_THRESHOLD_DB],
     * 1.0 (very high) to [SENSITIVITY_MIN_THRESHOLD_DB]; the default 0.5 lands
     * on the historical 4.5 dB. Using peak rather than the cell mean lets brief
     * sheet rustles trigger the alarm — they'd otherwise vanish in a 1 s average.
     */
    const val SENSITIVITY_MIN_THRESHOLD_DB: Double = 1.0
    const val SENSITIVITY_MAX_THRESHOLD_DB: Double = 8.0
    const val DEFAULT_SENSITIVITY: Float = 0.5f

    fun spikeThresholdDb(sensitivity: Float): Double =
        SENSITIVITY_MAX_THRESHOLD_DB -
            sensitivity.coerceIn(0f, 1f) *
            (SENSITIVITY_MAX_THRESHOLD_DB - SENSITIVITY_MIN_THRESHOLD_DB)
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
