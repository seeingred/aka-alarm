import Foundation
import AVFoundation

/// Continuously samples the microphone, maintains a rolling baseline of ambient noise level
/// (in dBFS), and reports spike events when the current level rises above baseline by
/// `Tuning.spikeThresholdDB`.
///
/// Levels are throttled for UI updates; baseline is recomputed once per
/// `Tuning.baselineCellSeconds`. AVAudioSession is configured with
/// `mode: .measurement` so input is *not* run through the system AGC — this preserves
/// the signal's natural dynamic range, which is what makes a faint stir distinguishable
/// from a quiet room.
final class MicMonitor {
    var onLevelUpdate: ((Double) -> Void)?
    var onBaselineUpdate: ((Double) -> Void)?
    var onSpike: (() -> Void)?

    private let engine = AVAudioEngine()
    private let session = AVAudioSession.sharedInstance()

    // Audio-thread-only state.
    private var cellSamples: [Double] = []
    private var cellStart: TimeInterval = 0
    private var baselineRing: [Double] = []
    private var lastEmit: TimeInterval = 0

    // Cross-thread state.
    private let lock = NSLock()
    private var _spikeDetectionEnabled = false
    private var _running = false

    var isRunning: Bool { lockedRead { _running } }

    private var spikeEnabled: Bool { lockedRead { _spikeDetectionEnabled } }

    private func lockedRead<T>(_ block: () -> T) -> T {
        lock.lock(); defer { lock.unlock() }
        return block()
    }

    private func lockedWrite(_ block: () -> Void) {
        lock.lock(); block(); lock.unlock()
    }

    func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            AVAudioApplication.requestRecordPermission { granted in
                cont.resume(returning: granted)
            }
        }
    }

    func start() throws {
        if isRunning { return }
        try session.setCategory(.playAndRecord, mode: .measurement,
                                options: [.defaultToSpeaker, .allowBluetoothHFP])
        try session.setActive(true, options: [])

        let input = engine.inputNode
        let format = input.inputFormat(forBus: 0)
        let now = ProcessInfo.processInfo.systemUptime
        cellSamples.removeAll(keepingCapacity: true)
        baselineRing.removeAll(keepingCapacity: true)
        cellStart = now
        lastEmit = 0

        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.process(buffer: buffer)
        }
        try engine.start()
        lockedWrite { _running = true }
    }

    func stop() {
        if !isRunning { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        lockedWrite {
            _running = false
            _spikeDetectionEnabled = false
        }
        try? session.setActive(false, options: [.notifyOthersOnDeactivation])
    }

    func setSpikeDetectionEnabled(_ enabled: Bool) {
        lockedWrite { _spikeDetectionEnabled = enabled }
    }

    // MARK: Audio thread

    private func process(buffer: AVAudioPCMBuffer) {
        guard let channel = buffer.floatChannelData?[0] else { return }
        let frameLen = Int(buffer.frameLength)
        if frameLen == 0 { return }

        var sumSquares: Double = 0
        for i in 0..<frameLen {
            let v = Double(channel[i])
            sumSquares += v * v
        }
        let rms = sqrt(sumSquares / Double(frameLen))
        let dB = max(Tuning.dbFloor, 20 * log10(max(rms, 1e-9)))

        let now = ProcessInfo.processInfo.systemUptime

        // UI throttle.
        if now - lastEmit >= 1.0 / Tuning.levelMeterHz {
            lastEmit = now
            onLevelUpdate?(dB)
        }

        // Aggregate into baseline buckets.
        cellSamples.append(dB)
        if now - cellStart >= Tuning.baselineCellSeconds {
            let cellAvg = cellSamples.reduce(0, +) / Double(cellSamples.count)
            cellSamples.removeAll(keepingCapacity: true)
            cellStart = now

            let capacity = max(1, Int(Tuning.baselineWindow / Tuning.baselineCellSeconds))
            baselineRing.append(cellAvg)
            if baselineRing.count > capacity {
                baselineRing.removeFirst(baselineRing.count - capacity)
            }
            let baseline = baselineRing.reduce(0, +) / Double(baselineRing.count)
            onBaselineUpdate?(baseline)

            if spikeEnabled, baselineRing.count >= Tuning.minBaselineCells {
                // Compare current cell to baseline excluding itself, so a single loud cell
                // doesn't pull its own reference up.
                let prior = baselineRing.dropLast()
                let priorBaseline = prior.reduce(0, +) / Double(prior.count)
                if cellAvg > priorBaseline + Tuning.spikeThresholdDB {
                    onSpike?()
                }
            }
        }
    }
}
