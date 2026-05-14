import Foundation
import AVFoundation

/// Plays the alarm tone with a gradual volume fade-up from `alarmStartVolume` to
/// `alarmEndVolume` over `alarmFadeDuration` seconds. The tone is generated
/// procedurally as a 1-second loop: a 0.5 s dual-frequency beep followed by 0.5 s of
/// silence, so it self-loops without clicks.
final class AlarmPlayer {
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var buffer: AVAudioPCMBuffer?
    private var fadeTimer: Timer?
    private var connected = false

    func start() {
        if buffer == nil {
            buffer = makeBeepBuffer()
        }
        guard let buffer else { return }

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

        if !connected {
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: buffer.format)
            connected = true
        }

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
    }

    func stop() {
        fadeTimer?.invalidate()
        fadeTimer = nil
        if player.isPlaying { player.stop() }
        if engine.isRunning { engine.stop() }
        // Session lifecycle is owned by AlarmStore; do not deactivate here.
    }

    private func startFade() {
        let steps = 60
        let stepInterval = Tuning.alarmFadeDuration / Double(steps)
        let delta = (Tuning.alarmEndVolume - Tuning.alarmStartVolume) / Float(steps)
        var stepIndex = 0
        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: stepInterval, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            stepIndex += 1
            let v = min(Tuning.alarmEndVolume, Tuning.alarmStartVolume + delta * Float(stepIndex))
            self.player.volume = v
            if stepIndex >= steps { timer.invalidate() }
        }
    }

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
