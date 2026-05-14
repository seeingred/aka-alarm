package com.aka.alarm.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.aka.alarm.Tuning
import kotlin.math.sqrt

/**
 * Reads gyroscope rotation rate and reports a "snooze nudge" whenever the
 * magnitude exceeds [Tuning.SNOOZE_ROTATION_THRESHOLD]. Mirrors the iOS
 * [MotionMonitor]; gyro instead of accelerometer to ignore the alarm's own
 * vibration buzz.
 */
class MotionMonitor(context: Context) : SensorEventListener {
    var onSnoozeNudge: (() -> Unit)? = null

    private val manager = context.getSystemService(SensorManager::class.java)
    private val gyroscope: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cooldownUntilNanos: Long = 0
    private var running = false

    fun start() {
        if (running || gyroscope == null) return
        val periodUs = (1_000_000.0 / Tuning.MOTION_SAMPLING_HZ).toInt()
        manager?.registerListener(this, gyroscope, periodUs)
        cooldownUntilNanos = 0
        running = true
    }

    fun stop() {
        if (!running) return
        manager?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        val (x, y, z) = event.values
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        if (magnitude > Tuning.SNOOZE_ROTATION_THRESHOLD) {
            val now = System.nanoTime()
            if (now >= cooldownUntilNanos) {
                cooldownUntilNanos = now + 1_000_000_000L // 1 s
                mainHandler.post { onSnoozeNudge?.invoke() }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

private operator fun FloatArray.component1(): Float = this[0]
private operator fun FloatArray.component2(): Float = this[1]
private operator fun FloatArray.component3(): Float = this[2]
