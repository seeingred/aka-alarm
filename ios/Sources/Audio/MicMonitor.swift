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
///
/// Background reliability:
/// - Subscribes to `routeChangeNotification` so AirPods connect/disconnect (or any
///   other route swap) cleanly rebuilds the tap with the new input format. Without
///   this the tap silently stops delivering buffers and the mic appears "dead".
/// - Subscribes to `interruptionNotification` so phone calls / Siri / other audio apps
///   that grab exclusive session ownership don't permanently kill us — the engine
///   restarts on `.ended .shouldResume`. The `.began` event also fires `onInterruption`
///   so the surrounding `AlarmStore` can post a local notification to the user.
/// - Keeps a silent `AVAudioPlayerNode` looping a zero-buffer attached to the same
///   engine. This guarantees the audio session is *always* counted as producing audio
///   even during the brief gap when recording is reinitialising after a route change.
///   Same trick Sleep Cycle uses for overnight reliability.
final class MicMonitor {
    var onLevelUpdate: ((Double) -> Void)?
    var onBaselineUpdate: ((Double) -> Void)?
    var onSpike: (() -> Void)?
    /// Fired with `true` when the session is interrupted (phone call, another audio
    /// app grabbing exclusive ownership) and `false` when the interruption ends and
    /// we successfully resumed.
    var onInterruption: ((Bool) -> Void)?

    private let engine = AVAudioEngine()
    private let session = AVAudioSession.sharedInstance()

    /// Silent audio player keeping the session "actively producing audio" during gaps.
    private let silentPlayer = AVAudioPlayerNode()
    private var silentBuffer: AVAudioPCMBuffer?
    private var silentAttached = false

    // Audio-thread-only state.
    private var cellSamples: [Double] = []
    private var cellStart: TimeInterval = 0
    private var baselineRing: [Double] = []
    private var lastEmit: TimeInterval = 0

    // Cross-thread state.
    private let lock = NSLock()
    private var _spikeDetectionEnabled = false
    private var _running = false

    // Notification observers.
    private var routeChangeObserver: NSObjectProtocol?
    private var interruptionObserver: NSObjectProtocol?

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

        try configureEngineForCurrentRoute()

        lockedWrite { _running = true }
        registerObservers()
    }

    func stop() {
        if !isRunning { return }
        unregisterObservers()

        engine.inputNode.removeTap(onBus: 0)
        if silentPlayer.isPlaying { silentPlayer.stop() }
        engine.stop()

        lockedWrite {
            _running = false
            _spikeDetectionEnabled = false
        }
        // Session lifecycle is owned by AlarmStore; do not deactivate here.
    }

    func setSpikeDetectionEnabled(_ enabled: Bool) {
        lockedWrite { _spikeDetectionEnabled = enabled }
    }

    // MARK: Engine setup

    /// Tears down (if needed) and (re-)installs the input tap + silent keepalive with
    /// whatever format the current audio route surfaces. Called both on `start()` and
    /// in response to route changes.
    private func configureEngineForCurrentRoute() throws {
        let input = engine.inputNode
        input.removeTap(onBus: 0)
        if silentPlayer.isPlaying { silentPlayer.stop() }
        if engine.isRunning { engine.stop() }

        let format = input.inputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else {
            print("MicMonitor: input format unavailable (sampleRate=\(format.sampleRate), channels=\(format.channelCount))")
            return
        }

        let now = ProcessInfo.processInfo.systemUptime
        cellSamples.removeAll(keepingCapacity: true)
        baselineRing.removeAll(keepingCapacity: true)
        cellStart = now
        lastEmit = 0

        input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.process(buffer: buffer)
        }

        // Attach + connect the silent keepalive player once. Connection format must
        // be queryable from the main mixer, which may itself depend on input format,
        // so we (re)connect on every configuration pass to be safe.
        if !silentAttached {
            engine.attach(silentPlayer)
            silentAttached = true
        }
        let outFormat = engine.mainMixerNode.outputFormat(forBus: 0)
        engine.connect(silentPlayer, to: engine.mainMixerNode, format: outFormat)
        if silentBuffer == nil || silentBuffer?.format != outFormat {
            silentBuffer = makeSilentBuffer(format: outFormat)
        }
        if let buf = silentBuffer {
            silentPlayer.scheduleBuffer(buf, at: nil, options: .loops, completionHandler: nil)
            silentPlayer.volume = 0
        }

        try engine.start()
        if silentBuffer != nil {
            silentPlayer.play()
        }
    }

    private func makeSilentBuffer(format: AVAudioFormat) -> AVAudioPCMBuffer? {
        // 1-second buffer of zeros. `.loops` schedule keeps it cheap forever.
        let frames = AVAudioFrameCount(format.sampleRate)
        guard let buf = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames) else { return nil }
        buf.frameLength = frames
        // Float buffers are zero-initialised by Apple's allocator; explicit zeroing
        // is unnecessary but cheap insurance.
        if let ch0 = buf.floatChannelData?[0] {
            ch0.update(repeating: 0, count: Int(frames))
        }
        return buf
    }

    // MARK: Notification observers

    private func registerObservers() {
        let nc = NotificationCenter.default
        routeChangeObserver = nc.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: session, queue: .main
        ) { [weak self] note in
            self?.handleRouteChange(note)
        }
        interruptionObserver = nc.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: session, queue: .main
        ) { [weak self] note in
            self?.handleInterruption(note)
        }
    }

    private func unregisterObservers() {
        let nc = NotificationCenter.default
        routeChangeObserver.map(nc.removeObserver)
        interruptionObserver.map(nc.removeObserver)
        routeChangeObserver = nil
        interruptionObserver = nil
    }

    private func handleRouteChange(_ notification: Notification) {
        guard isRunning else { return }
        // Any route change can invalidate the format the input node was using. The
        // safe play is to always rebuild — covers AirPods connect/disconnect, wired
        // headphones, BT car kit, etc.
        do {
            try configureEngineForCurrentRoute()
        } catch {
            print("MicMonitor: engine restart after route change failed: \(error)")
        }
    }

    private func handleInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeRaw = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeRaw)
        else { return }

        switch type {
        case .began:
            // Phone call, Siri, another audio app, etc. iOS already stopped our
            // engine. Tell AlarmStore so it can warn the user via a notification.
            onInterruption?(true)

        case .ended:
            guard isRunning else { return }
            let shouldResume: Bool
            if let optsRaw = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt {
                shouldResume = AVAudioSession.InterruptionOptions(rawValue: optsRaw)
                    .contains(.shouldResume)
            } else {
                shouldResume = true
            }
            guard shouldResume else { return }
            do {
                try session.setActive(true, options: [])
                try configureEngineForCurrentRoute()
                onInterruption?(false)
            } catch {
                print("MicMonitor: interruption resume failed: \(error)")
            }

        @unknown default:
            break
        }
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
