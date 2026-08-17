import Foundation
import AVFoundation

/// Continuously samples the microphone, maintains a rolling baseline of ambient noise level
/// (in dBFS), and reports spike events when the current level rises above baseline by
/// `spikeThresholdDB` (user-tunable via the sensitivity slider).
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
    private var cellSumSquares: Double = 0       // for cell-mean RMS (drives baseline)
    private var cellSampleCount: Int = 0
    private var cellPeakDB: Double = Tuning.dbFloor // loudest moment in the cell (drives spike)
    private var cellStart: TimeInterval = 0
    private var baselineRing: [Double] = []
    private var lastEmit: TimeInterval = 0

    // Cross-thread state.
    private let lock = NSLock()
    private var _spikeDetectionEnabled = false
    private var _spikeThresholdDB = Tuning.spikeThresholdDB(sensitivity: Tuning.defaultSensitivity)
    private var _running = false
    /// Time (systemUptime) of the most recent buffer received by the audio tap.
    /// Audio thread writes, main thread reads for the watchdog.
    private var _lastBufferTime: TimeInterval = 0
    /// Set true when the watchdog decides we're stuck (≥ watchdogTimeout without
    /// a buffer). Cleared when buffers resume. Used to debounce the
    /// onInterruption(true → false) edge to a single round-trip.
    private var _wasStuck = false

    // Notification observers + debounce/watchdog plumbing (all main-thread).
    private var routeChangeObserver: NSObjectProtocol?
    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeWork: DispatchWorkItem?
    private var watchdogTimer: Timer?

    var isRunning: Bool { lockedRead { _running } }

    /// Live-tunable via the sensitivity slider; AlarmStore pushes updates.
    var spikeThresholdDB: Double {
        get { lockedRead { _spikeThresholdDB } }
        set { lockedWrite { _spikeThresholdDB = newValue } }
    }

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
        // A2DP, not HFP: Bluetooth is allowed for OUTPUT only. With HFP allowed,
        // connected AirPods become the session *input* — their low-bandwidth
        // Bluetooth mic delivers no usable buffers in .measurement mode, leaving
        // the app deaf until the AirPods switch to another device. The phone on
        // the nightstand is the sensor; the built-in mic must always be the input.
        try session.setCategory(.playAndRecord, mode: .measurement,
                                options: [.defaultToSpeaker, .allowBluetoothA2DP])
        try session.setActive(true, options: [])
        // Belt-and-braces: pin the input to the built-in mic even if the system
        // prefers some other attached input (wired headset, USB interface).
        if let builtIn = session.availableInputs?
            .first(where: { $0.portType == .builtInMic }) {
            try? session.setPreferredInput(builtIn)
        }

        try configureEngineForCurrentRoute()

        lockedWrite {
            _running = true
            _lastBufferTime = ProcessInfo.processInfo.systemUptime
            _wasStuck = false
        }
        registerObservers()
        startWatchdog()
    }

    func stop() {
        if !isRunning { return }
        stopWatchdog()
        routeChangeWork?.cancel()
        routeChangeWork = nil
        unregisterObservers()

        engine.inputNode.removeTap(onBus: 0)
        if silentPlayer.isPlaying { silentPlayer.stop() }
        engine.stop()

        lockedWrite {
            _running = false
            _spikeDetectionEnabled = false
            _wasStuck = false
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
        cellSumSquares = 0
        cellSampleCount = 0
        cellPeakDB = Tuning.dbFloor
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
        // Rapid AirPods on/off (or any cluster of route changes) used to overlap
        // tear-down/rebuild cycles and wedge the engine. Debounce: only reconfigure
        // once the changes quiet down for `Tuning.routeChangeDebounce`.
        routeChangeWork?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.isRunning else { return }
            do {
                try self.configureEngineForCurrentRoute()
            } catch {
                print("MicMonitor: engine restart after route change failed: \(error)")
            }
        }
        routeChangeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Tuning.routeChangeDebounce, execute: work)
    }

    // MARK: Watchdog

    /// `AVAudioSession.interruptionNotification` only fires for *exclusive* session
    /// takeovers (phone call, Siri). When another app uses the `.playback` category
    /// (YouTube, Spotify, browser video, etc.) the OS routes audio to them silently
    /// without telling us, but our `.measurement`-mode tap stops receiving useful
    /// buffers. The watchdog catches this by checking once a second whether we've
    /// received a buffer recently; if not, we declare the mic stuck, notify the
    /// user via `onInterruption(true)`, and rebuild the engine. When buffers resume,
    /// `onInterruption(false)` clears the notification.
    private func startWatchdog() {
        stopWatchdog()
        watchdogTimer = Timer.scheduledTimer(
            withTimeInterval: Tuning.micWatchdogInterval,
            repeats: true
        ) { [weak self] _ in
            self?.checkWatchdog()
        }
    }

    private func stopWatchdog() {
        watchdogTimer?.invalidate()
        watchdogTimer = nil
    }

    private func checkWatchdog() {
        guard isRunning else { return }
        let now = ProcessInfo.processInfo.systemUptime
        let (last, wasStuck) = lockedRead { (_lastBufferTime, _wasStuck) }
        guard last > 0 else { return }
        let gap = now - last

        if gap > Tuning.micWatchdogTimeout {
            // Keep retrying the rebuild while stuck — a single attempt can fail
            // permanently when the input route is still settling right after
            // session activation (inputFormat reports sampleRate=0 and no tap
            // gets installed), which used to leave the mic deaf until an
            // unrelated route change. CRITICAL: stamp the liveness clock after
            // every attempt so the next judgment happens a full timeout later —
            // without the grace period this loop tears the engine down each
            // second, faster than it can deliver its first buffer, and the mic
            // never comes up at all.
            if !wasStuck {
                lockedWrite { _wasStuck = true }
                onInterruption?(true)
            }
            do {
                try configureEngineForCurrentRoute()
            } catch {
                print("MicMonitor watchdog: rebuild failed: \(error)")
            }
            lockedWrite { _lastBufferTime = ProcessInfo.processInfo.systemUptime }
        } else if wasStuck {
            // Recovered — buffers resumed (process() updated lastBufferTime).
            lockedWrite { _wasStuck = false }
            onInterruption?(false)
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
        let frameDB = max(Tuning.dbFloor, 20 * log10(max(rms, 1e-9)))

        let now = ProcessInfo.processInfo.systemUptime
        // Watchdog liveness stamp.
        lockedWrite { _lastBufferTime = now }

        // UI throttle.
        if now - lastEmit >= 1.0 / Tuning.levelMeterHz {
            lastEmit = now
            onLevelUpdate?(frameDB)
        }

        // Accumulate for cell mean (drives baseline) and track peak (drives spike).
        cellSumSquares += sumSquares
        cellSampleCount += frameLen
        if frameDB > cellPeakDB { cellPeakDB = frameDB }

        if now - cellStart >= Tuning.baselineCellSeconds {
            let cellMeanRMS = sqrt(cellSumSquares / Double(max(cellSampleCount, 1)))
            let cellMeanDB = max(Tuning.dbFloor, 20 * log10(max(cellMeanRMS, 1e-9)))
            let cellPeak = cellPeakDB

            // Reset cell accumulators for the next window.
            cellSumSquares = 0
            cellSampleCount = 0
            cellPeakDB = Tuning.dbFloor
            cellStart = now

            // Baseline tracks the *mean* — stays stable across single loud events.
            let capacity = max(1, Int(Tuning.baselineWindow / Tuning.baselineCellSeconds))
            baselineRing.append(cellMeanDB)
            if baselineRing.count > capacity {
                baselineRing.removeFirst(baselineRing.count - capacity)
            }
            let baseline = baselineRing.reduce(0, +) / Double(baselineRing.count)
            onBaselineUpdate?(baseline)

            if spikeEnabled, baselineRing.count >= Tuning.minBaselineCells {
                // Spike comparator uses the *peak* moment in the cell so brief
                // stirring sounds aren't washed out by the surrounding silence.
                // Baseline reference excludes the just-appended cell so a single
                // loud event can't pull its own threshold up.
                let prior = baselineRing.dropLast()
                let priorBaseline = prior.reduce(0, +) / Double(prior.count)
                if cellPeak > priorBaseline + spikeThresholdDB {
                    onSpike?()
                }
            }
        }
    }
}
