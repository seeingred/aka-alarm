package com.aka.alarm.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.aka.alarm.Tuning
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Continuously samples the microphone, maintains a rolling 5-minute baseline of
 * ambient noise (dBFS), and reports spike events when the current level exceeds
 * baseline by [spikeThresholdDb]. Callbacks are dispatched on the main
 * thread for easy Compose state updates.
 */
class MicMonitor {
    var onLevelUpdate: ((Double) -> Unit)? = null
    var onBaselineUpdate: ((Double) -> Unit)? = null
    var onSpike: (() -> Unit)? = null

    @Volatile var spikeDetectionEnabled: Boolean = false
    /** Live-tunable via the sensitivity slider; AlarmStore pushes updates. */
    @Volatile var spikeThresholdDb: Double = Tuning.spikeThresholdDb(Tuning.DEFAULT_SENSITIVITY)
    @Volatile var isRunning: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is gated by AlarmStore.startAlarm().
    fun start() {
        if (isRunning) return
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ).coerceAtLeast(4 * FRAMES_PER_READ)

        // UNPROCESSED skips system AGC so quiet stirs aren't squashed, matching the
        // iOS `mode: .measurement` decision. Falls back to MIC if the device claims
        // not to support it.
        val source = MediaRecorder.AudioSource.UNPROCESSED
        val r = AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBuffer)
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return
        }
        recorder = r
        r.startRecording()
        isRunning = true

        thread = thread(name = "MicMonitor", isDaemon = true) {
            run(r)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        spikeDetectionEnabled = false
        thread?.join(500)
        thread = null
        recorder?.run {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        recorder = null
    }

    private fun run(r: AudioRecord) {
        val buf = FloatArray(FRAMES_PER_READ)
        var cellSumSquares = 0.0          // cell-mean RMS basis → drives baseline
        var cellSampleCount = 0
        var cellPeakDb = Tuning.DB_FLOOR   // loudest moment in the cell → drives spike
        var cellStartNanos = System.nanoTime()
        val baselineRing = ArrayDeque<Double>()
        val baselineCapacity = (Tuning.baselineWindow / Tuning.baselineCellSeconds)
            .toInt().coerceAtLeast(1)
        var lastEmitNanos = 0L
        val emitIntervalNanos = (1_000_000_000.0 / Tuning.LEVEL_METER_HZ).toLong()
        val cellSpanNanos = Tuning.baselineCellSeconds.inWholeNanoseconds

        while (isRunning) {
            val n = r.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            if (n <= 0) continue

            var sumSquares = 0.0
            for (i in 0 until n) {
                val v = buf[i].toDouble()
                sumSquares += v * v
            }
            val rms = sqrt(sumSquares / n)
            val frameDb = maxOf(Tuning.DB_FLOOR, 20 * log10(maxOf(rms, 1e-9)))

            val nowNanos = System.nanoTime()
            if (nowNanos - lastEmitNanos >= emitIntervalNanos) {
                lastEmitNanos = nowNanos
                mainHandler.post { onLevelUpdate?.invoke(frameDb) }
            }

            cellSumSquares += sumSquares
            cellSampleCount += n
            if (frameDb > cellPeakDb) cellPeakDb = frameDb

            if (nowNanos - cellStartNanos >= cellSpanNanos) {
                val cellMeanRms = sqrt(cellSumSquares / cellSampleCount.coerceAtLeast(1))
                val cellMeanDb = maxOf(Tuning.DB_FLOOR, 20 * log10(maxOf(cellMeanRms, 1e-9)))
                val cellPeak = cellPeakDb

                cellSumSquares = 0.0
                cellSampleCount = 0
                cellPeakDb = Tuning.DB_FLOOR
                cellStartNanos = nowNanos

                baselineRing.addLast(cellMeanDb)
                while (baselineRing.size > baselineCapacity) baselineRing.removeFirst()
                val baseline = baselineRing.average()
                mainHandler.post { onBaselineUpdate?.invoke(baseline) }

                if (spikeDetectionEnabled && baselineRing.size >= Tuning.MIN_BASELINE_CELLS) {
                    val priorAvg = baselineRing.asSequence()
                        .take(baselineRing.size - 1)
                        .average()
                    if (cellPeak > priorAvg + spikeThresholdDb) {
                        mainHandler.post { onSpike?.invoke() }
                    }
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        const val FRAMES_PER_READ = 1024
    }
}
