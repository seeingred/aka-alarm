import Foundation
import AVFoundation
import AudioToolbox

/// Plays the alarm sound with a gradual volume fade-up from `alarmStartVolume` to
/// `alarmEndVolume` over `alarmFadeDuration` seconds, in equal dB steps so the
/// first half-minute is genuinely quiet rather than merely starting quiet.
///
/// The sound is either the user's own file (see `AlarmStore.importAlarmSound`),
/// decoded whole into a looping buffer, or the built-in tone: a procedurally
/// generated 1-second loop of a 0.5 s three-harmonic beep followed by 0.5 s of
/// silence, so it self-loops without clicks. If the custom file fails to decode
/// the built-in tone plays instead — a silent alarm is the one failure this
/// class must never have.
final class AlarmPlayer {
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var toneBuffer: AVAudioPCMBuffer?
    private var fadeTimer: Timer?
    private var vibrationTimer: Timer?
    private var attached = false

    func start(customSound url: URL? = nil) {
        stop()

        let buffer: AVAudioPCMBuffer
        if let url, let custom = Self.loadLoop(from: url) {
            buffer = custom
        } else {
            if toneBuffer == nil { toneBuffer = makeBeepBuffer() }
            guard let tone = toneBuffer else { return }
            buffer = tone
        }

        // Reconfigure the shared audio session for *loud playback*. MicMonitor uses
        // `.measurement` mode which keeps mic input clean but attenuates output
        // significantly. By the time we reach here, MicMonitor is stopped, so we can
        // safely flip to plain `.playback` — speaker-routed, ignores silent mode.
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [.duckOthers])
            try session.setActive(true, options: [])
        } catch {
            print("AlarmPlayer session setup failed: \(error)")
        }

        if !attached {
            engine.attach(player)
            attached = true
        }
        // (Re)connect in this buffer's format: a custom file is typically stereo
        // 48 kHz where the built-in tone is mono 44.1 kHz. The engine is stopped
        // here (see stop()), so rewiring is safe.
        engine.disconnectNodeOutput(player)
        engine.connect(player, to: engine.mainMixerNode, format: buffer.format)

        do {
            if !engine.isRunning { try engine.start() }
            player.scheduleBuffer(buffer, at: nil, options: .loops, completionHandler: nil)
            player.volume = Tuning.alarmStartVolume
            engine.mainMixerNode.outputVolume = 1.0
            player.play()
            startFade()
        } catch {
            print("AlarmPlayer.start failed: \(error)")
        }

        startVibration()
    }

    func stop() {
        fadeTimer?.invalidate()
        fadeTimer = nil
        vibrationTimer?.invalidate()
        vibrationTimer = nil
        if player.isPlaying { player.stop() }
        if engine.isRunning { engine.stop() }
        // Session lifecycle is owned by AlarmStore; do not deactivate here.
    }

    // MARK: Custom sound

    /// Decodes a whole file into memory (length-capped) so it loops gaplessly.
    /// Returns nil — and the caller falls back to the tone — if it won't decode.
    private static func loadLoop(from url: URL) -> AVAudioPCMBuffer? {
        do {
            let file = try AVAudioFile(forReading: url)
            let format = file.processingFormat
            let cap = AVAudioFramePosition(format.sampleRate * Tuning.maxCustomSoundSeconds)
            let frames = AVAudioFrameCount(min(file.length, cap))
            guard frames > 0,
                  let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames)
            else { return nil }
            try file.read(into: buffer, frameCount: frames)
            return buffer.frameLength > 0 ? buffer : nil
        } catch {
            print("AlarmPlayer: custom sound failed (\(error)); using the built-in tone")
            return nil
        }
    }

    // MARK: Vibration

    private func startVibration() {
        vibrationTimer?.invalidate()
        // Fire one pulse immediately so the buzz lines up with audio start.
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        vibrationTimer = Timer.scheduledTimer(
            withTimeInterval: Tuning.vibrationPulseInterval, repeats: true
        ) { _ in
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    // MARK: Fade

    private func startFade() {
        let steps = 60
        let stepInterval = Tuning.alarmFadeDuration / Double(steps)
        // Exponential in gain = linear in dB. With 0.01 → 1.0 that is −40 dB → 0 dB:
        // −20 dB at the half-way mark, where a linear ramp would already be at −6 dB.
        let ratio = Tuning.alarmEndVolume / Tuning.alarmStartVolume
        var stepIndex = 0
        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: stepInterval, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            stepIndex += 1
            let v = min(
                Tuning.alarmEndVolume,
                Tuning.alarmStartVolume * powf(ratio, Float(stepIndex) / Float(steps))
            )
            self.player.volume = v
            if stepIndex >= steps { timer.invalidate() }
        }
    }

    // MARK: Tone synthesis

    private func makeBeepBuffer() -> AVAudioPCMBuffer? {
        let sampleRate: Double = 44100
        guard let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1) else { return nil }
        let frames = AVAudioFrameCount(sampleRate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames) else { return nil }
        buf.frameLength = frames
        guard let ch = buf.floatChannelData?[0] else { return nil }

        let beepDuration = 0.5
        let beepFrames = Int(sampleRate * beepDuration)
        let rampSeconds = 0.02
        let twoPi = 2 * Double.pi
        // Three-harmonic blend. Coefficients chosen so the worst-case peak
        // (all sines aligning at +1) stays just under 1.0 to avoid clipping.
        let h1: Double = 0.55  // 880 Hz
        let h2: Double = 0.30  // 1320 Hz
        let h3: Double = 0.13  // 1760 Hz   sum = 0.98

        for i in 0..<Int(frames) {
            if i < beepFrames {
                let t = Double(i) / sampleRate
                let s1 = sin(twoPi * 880 * t)
                let s2 = sin(twoPi * 1320 * t)
                let s3 = sin(twoPi * 1760 * t)
                let elapsed = t
                let env: Double
                if elapsed < rampSeconds {
                    env = elapsed / rampSeconds
                } else if elapsed > beepDuration - rampSeconds {
                    env = max(0, (beepDuration - elapsed) / rampSeconds)
                } else {
                    env = 1
                }
                ch[i] = Float(env * (h1 * s1 + h2 * s2 + h3 * s3))
            } else {
                ch[i] = 0
            }
        }
        return buf
    }
}
