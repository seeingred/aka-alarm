package com.aka.alarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.aka.alarm.Tuning
import java.io.File
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Plays the alarm sound with a gradual volume fade-up and pulses the device
 * vibrator alongside it, mirroring the iOS [AlarmPlayer].
 *
 * The sound is either the user's own file (see [CustomAlarmSound]), looped
 * through [MediaPlayer], or the built-in tone: a 1-second loop of 0.5 s of
 * three-harmonic beep (880 / 1320 / 1760 Hz) followed by 0.5 s of silence,
 * synthesised into a static [AudioTrack]. If the custom file fails to open the
 * built-in tone plays instead — an alarm that stays silent is the one failure
 * mode this class must never have.
 *
 * Volume ramps from [Tuning.ALARM_START_VOLUME] to [Tuning.ALARM_END_VOLUME]
 * over [Tuning.alarmFadeDuration] in equal dB steps, so the first half-minute
 * is genuinely quiet rather than merely starting quiet.
 */
class AlarmPlayer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var fadeRunnable: Runnable? = null
    private var vibrationRunnable: Runnable? = null

    fun start(customSound: File? = null) {
        stop()
        if (customSound == null || !startCustom(customSound)) {
            startTone()
        }
        startFade()
        startVibration()
    }

    fun stop() {
        fadeRunnable?.let { mainHandler.removeCallbacks(it) }
        fadeRunnable = null

        vibrationRunnable?.let { mainHandler.removeCallbacks(it) }
        vibrationRunnable = null

        track?.run {
            try {
                pause()
                flush()
                stop()
            } catch (_: IllegalStateException) {}
            release()
        }
        track = null

        mediaPlayer?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {}
            release()
        }
        mediaPlayer = null
    }

    // MARK: Sources

    private fun startCustom(file: File): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttributes())
            mp.setDataSource(file.absolutePath)
            mp.isLooping = true
            mp.prepare()
            mp.setVolume(Tuning.ALARM_START_VOLUME, Tuning.ALARM_START_VOLUME)
            mp.start()
            mediaPlayer = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "Custom alarm sound failed, falling back to built-in tone", e)
            mp.release()
            false
        }
    }

    private fun startTone() {
        val pcm = buildBeepBuffer()
        val t = AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        t.setLoopPoints(0, pcm.size, -1) // -1 = loop forever
        t.setVolume(Tuning.ALARM_START_VOLUME)
        t.play()
        track = t
    }

    private fun alarmAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun setVolume(v: Float) {
        track?.setVolume(v)
        mediaPlayer?.setVolume(v, v)
    }

    // MARK: Fade

    private fun startFade() {
        val steps = 60
        val stepIntervalMs = Tuning.alarmFadeDuration.inWholeMilliseconds / steps
        // Exponential in gain = linear in dB. With 0.01 → 1.0 that is −40 dB → 0 dB:
        // −20 dB at the half-way mark, where a linear ramp would already be at −6 dB.
        val ratio = Tuning.ALARM_END_VOLUME / Tuning.ALARM_START_VOLUME
        var stepIndex = 0
        val runnable = object : Runnable {
            override fun run() {
                stepIndex++
                val v = (Tuning.ALARM_START_VOLUME * ratio.pow(stepIndex / steps.toFloat()))
                    .coerceAtMost(Tuning.ALARM_END_VOLUME)
                setVolume(v)
                if (stepIndex < steps) {
                    mainHandler.postDelayed(this, stepIntervalMs)
                }
            }
        }
        fadeRunnable = runnable
        mainHandler.postDelayed(runnable, stepIntervalMs)
    }

    // MARK: Vibration

    private fun startVibration() {
        // VibratorManager is API 31+; below that the legacy Vibrator service is
        // the only path (deprecated on 31+, still fully functional on 26–30).
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return

        val pulse = VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
        v.vibrate(pulse)
        val intervalMs = Tuning.vibrationPulseInterval.inWholeMilliseconds
        val runnable = object : Runnable {
            override fun run() {
                v.vibrate(pulse)
                mainHandler.postDelayed(this, intervalMs)
            }
        }
        vibrationRunnable = runnable
        mainHandler.postDelayed(runnable, intervalMs)
    }

    // MARK: Tone synthesis

    private fun buildBeepBuffer(): FloatArray {
        val totalSamples = SAMPLE_RATE // 1 second
        val beepSamples = SAMPLE_RATE / 2 // 0.5 s
        val rampSeconds = 0.02
        val twoPi = 2.0 * PI

        val h1 = 0.55; val h2 = 0.30; val h3 = 0.13   // peaks ≤ 0.98 → no clipping

        val out = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            if (i >= beepSamples) {
                out[i] = 0f
                continue
            }
            val t = i.toDouble() / SAMPLE_RATE
            val s1 = sin(twoPi * 880 * t)
            val s2 = sin(twoPi * 1320 * t)
            val s3 = sin(twoPi * 1760 * t)
            val beepDuration = 0.5
            val env = when {
                t < rampSeconds -> t / rampSeconds
                t > beepDuration - rampSeconds ->
                    maxOf(0.0, (beepDuration - t) / rampSeconds)
                else -> 1.0
            }
            out[i] = (env * (h1 * s1 + h2 * s2 + h3 * s3)).toFloat()
        }
        return out
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        const val SAMPLE_RATE = 44_100
    }
}
