import Foundation
import Combine
import AVFoundation

enum AlarmPhase: Equatable {
    case idle
    case monitoring(start: Date, end: Date)
    case inWindow(start: Date, end: Date)
    case alarming(windowEnd: Date)
    case snoozing(until: Date, windowEnd: Date)

    enum Kind: Equatable { case idle, monitoring, inWindow, alarming, snoozing }
    var kind: Kind {
        switch self {
        case .idle: return .idle
        case .monitoring: return .monitoring
        case .inWindow: return .inWindow
        case .alarming: return .alarming
        case .snoozing: return .snoozing
        }
    }

    var window: (start: Date, end: Date)? {
        switch self {
        case .monitoring(let s, let e), .inWindow(let s, let e): return (s, e)
        default: return nil
        }
    }

    var windowEnd: Date? {
        switch self {
        case .monitoring(_, let e), .inWindow(_, let e),
             .alarming(let e), .snoozing(_, let e): return e
        case .idle: return nil
        }
    }
}

final class AlarmStore: ObservableObject {
    @Published private(set) var phase: AlarmPhase = .idle
    @Published var micLevelDB: Double = Tuning.dbFloor
    @Published var baselineDB: Double = Tuning.dbFloor

    @Published var selectedHour: Int
    @Published var selectedMinute: Int
    @Published var micPermissionDenied: Bool = false

    private let mic = MicMonitor()
    private let motion = MotionMonitor()
    private let player = AlarmPlayer()

    private var phaseTimer: Timer?

    init() {
        let (h, m) = Self.currentWindowDefault()
        self.selectedHour = h
        self.selectedMinute = m

        mic.onLevelUpdate = { [weak self] db in
            DispatchQueue.main.async { self?.micLevelDB = db }
        }
        mic.onBaselineUpdate = { [weak self] db in
            DispatchQueue.main.async { self?.baselineDB = db }
        }
        mic.onSpike = { [weak self] in
            DispatchQueue.main.async { self?.handleSpike() }
        }
        motion.onSnoozeNudge = { [weak self] in
            DispatchQueue.main.async { self?.handleNudge() }
        }
    }

    // MARK: User actions

    func startAlarm(now: Date = .now) async {
        let granted = await mic.requestPermission()
        await MainActor.run {
            if !granted {
                self.micPermissionDenied = true
                return
            }
            let (start, end) = self.computeWindow(now: now)
            if now >= start {
                // "Now" is already inside the window — start listening for a stir immediately.
                self.transition(to: .inWindow(start: start, end: end))
            } else {
                self.transition(to: .monitoring(start: start, end: end))
            }
        }
    }

    func cancelAlarm() {
        transition(to: .idle)
    }

    /// Snap the pickers to the wake window that currently contains "now"
    /// (e.g. 18:38 → 18:30, giving a 18:30–19:00 window). Call when the
    /// set-alarm screen appears.
    func resetSelectedToCurrentWindow() {
        let (h, m) = Self.currentWindowDefault()
        selectedHour = h
        selectedMinute = m
    }

    private static func currentWindowDefault(now: Date = .now) -> (Int, Int) {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: now)
        let h = comps.hour ?? 0
        let m = comps.minute ?? 0
        return (h, (m / 15) * 15)
    }

    // MARK: Internal events

    private func handleSpike() {
        guard case .inWindow(_, let end) = phase else { return }
        transition(to: .alarming(windowEnd: end))
    }

    private func handleNudge() {
        guard case .alarming(let windowEnd) = phase else { return }
        let remaining = windowEnd.timeIntervalSince(.now)
        guard remaining >= Tuning.snoozeMinDuration else { return }
        let upper = min(Tuning.snoozeMaxDuration, remaining)
        let duration = Double.random(in: Tuning.snoozeMinDuration...upper)
        transition(to: .snoozing(until: .now.addingTimeInterval(duration), windowEnd: windowEnd))
    }

    // MARK: State machine

    private func transition(to next: AlarmPhase) {
        phaseTimer?.invalidate()
        phaseTimer = nil

        switch next {
        case .idle:
            mic.stop()
            motion.stop()
            player.stop()
            // Centralized session deactivation: the monitors and player no longer
            // touch session lifetime themselves, which avoids races between mic-stop
            // and player-start when the alarm fires inside an active window.
            try? AVAudioSession.sharedInstance()
                .setActive(false, options: [.notifyOthersOnDeactivation])
            micLevelDB = Tuning.dbFloor
            baselineDB = Tuning.dbFloor

        case .monitoring(let start, _):
            if !mic.isRunning {
                do { try mic.start() } catch { print("Mic start failed: \(error)") }
            }
            mic.setSpikeDetectionEnabled(false)
            motion.stop()
            player.stop()
            scheduleTransition(at: start) { [weak self] in
                guard let self else { return }
                guard case .monitoring(let s, let e) = self.phase else { return }
                self.transition(to: .inWindow(start: s, end: e))
            }

        case .inWindow(_, let end):
            if !mic.isRunning {
                do { try mic.start() } catch { print("Mic start failed: \(error)") }
            }
            mic.setSpikeDetectionEnabled(true)
            motion.stop()
            player.stop()
            scheduleTransition(at: end) { [weak self] in
                guard let self else { return }
                guard case .inWindow(_, let e) = self.phase else { return }
                self.transition(to: .alarming(windowEnd: e))
            }

        case .alarming:
            mic.stop()
            motion.start()
            player.start()

        case .snoozing(let until, _):
            motion.stop()
            player.stop()
            scheduleTransition(at: until) { [weak self] in
                guard let self else { return }
                guard case .snoozing(_, let e) = self.phase else { return }
                self.transition(to: .alarming(windowEnd: e))
            }
        }

        phase = next
    }

    private func scheduleTransition(at date: Date, _ action: @escaping () -> Void) {
        let interval = max(0.01, date.timeIntervalSinceNow)
        phaseTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { _ in
            DispatchQueue.main.async(execute: action)
        }
    }

    // MARK: Helpers

    private func computeWindow(now: Date) -> (Date, Date) {
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month, .day], from: now)
        comps.hour = selectedHour
        comps.minute = selectedMinute
        comps.second = 0
        var start = calendar.date(from: comps) ?? now
        var end = start.addingTimeInterval(Tuning.wakeWindowDuration)
        // Only roll to tomorrow if the *entire* window has already passed.
        if end <= now {
            start = calendar.date(byAdding: .day, value: 1, to: start) ?? start
            end = start.addingTimeInterval(Tuning.wakeWindowDuration)
        }
        return (start, end)
    }
}
