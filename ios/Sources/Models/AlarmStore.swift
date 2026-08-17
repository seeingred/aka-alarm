import Foundation
import Combine
import AVFoundation
import UIKit
import UserNotifications

enum AlarmPhase: Equatable {
    case idle
    /// Armed overnight: alarm scheduled, mic analysis not started yet.
    case armed(start: Date, end: Date)
    case monitoring(start: Date, end: Date)
    case inWindow(start: Date, end: Date)
    case alarming(windowEnd: Date)
    case snoozing(until: Date, windowEnd: Date)

    enum Kind: Equatable { case idle, armed, monitoring, inWindow, alarming, snoozing }
    var kind: Kind {
        switch self {
        case .idle: return .idle
        case .armed: return .armed
        case .monitoring: return .monitoring
        case .inWindow: return .inWindow
        case .alarming: return .alarming
        case .snoozing: return .snoozing
        }
    }

    var window: (start: Date, end: Date)? {
        switch self {
        case .armed(let s, let e), .monitoring(let s, let e), .inWindow(let s, let e):
            return (s, e)
        default: return nil
        }
    }

    var windowEnd: Date? {
        switch self {
        case .armed(_, let e), .monitoring(_, let e), .inWindow(_, let e),
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
    /// 0.0 = very low, 1.0 = very high. Persisted immediately on change and
    /// pushed live to the running mic monitor.
    @Published var sensitivity: Double {
        didSet {
            mic.spikeThresholdDB = Tuning.spikeThresholdDB(sensitivity: sensitivity)
            UserDefaults.standard.set(sensitivity, forKey: Self.kSensitivity)
        }
    }

    /// Minutes before the wake window at which the mic starts listening;
    /// -1 = right after starting. Persisted immediately; if an alarm is
    /// currently armed or monitoring, the phase is recomputed so the change
    /// applies tonight, not tomorrow.
    @Published var activationLeadMinutes: Int {
        didSet {
            UserDefaults.standard.set(activationLeadMinutes, forKey: Self.kActivationLead)
            applyLeadChangeToActivePhase()
        }
    }
    @Published var micPermissionDenied: Bool = false

    private let mic = MicMonitor()
    private let motion = MotionMonitor()
    private let player = AlarmPlayer()

    private var phaseTimer: Timer?
    private var appActiveObserver: NSObjectProtocol?

    init() {
        let (h, m) = Self.loadPersistedSelection() ?? Self.currentWindowDefault()
        self.selectedHour = h
        self.selectedMinute = m

        let s = UserDefaults.standard.object(forKey: Self.kSensitivity) as? Double
            ?? Tuning.defaultSensitivity
        self.sensitivity = min(1, max(0, s))

        let lead = UserDefaults.standard.object(forKey: Self.kActivationLead) as? Int
            ?? Tuning.defaultActivationLeadMinutes
        self.activationLeadMinutes = Tuning.activationLeadOptionsMinutes.contains(lead)
            ? lead
            : Tuning.defaultActivationLeadMinutes

        // didSet doesn't fire during init — push the loaded value explicitly.
        mic.spikeThresholdDB = Tuning.spikeThresholdDB(sensitivity: self.sensitivity)

        mic.onLevelUpdate = { [weak self] db in
            DispatchQueue.main.async { self?.micLevelDB = db }
        }
        mic.onBaselineUpdate = { [weak self] db in
            DispatchQueue.main.async { self?.baselineDB = db }
        }
        mic.onSpike = { [weak self] in
            DispatchQueue.main.async { self?.handleSpike() }
        }
        mic.onInterruption = { [weak self] began in
            DispatchQueue.main.async { self?.handleInterruption(began: began) }
        }
        motion.onSnoozeNudge = { [weak self] in
            DispatchQueue.main.async { self?.handleNudge() }
        }

        // If the OS suspended us and we just came back to the foreground while an
        // alarm is armed, kick the mic back to life. Belt-and-braces on top of the
        // route/interruption observers inside MicMonitor.
        appActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil, queue: .main
        ) { [weak self] _ in
            self?.handleAppDidBecomeActive()
        }
    }

    deinit {
        appActiveObserver.map(NotificationCenter.default.removeObserver)
    }

    // MARK: User actions

    func startAlarm(now: Date = .now) async {
        let granted = await mic.requestPermission()
        await MainActor.run {
            if !granted {
                self.micPermissionDenied = true
                return
            }
            // Best-effort notification permission for the "audio interrupted" warning.
            // Failure to grant doesn't break anything — we just skip posting later.
            Task { _ = try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound]) }

            self.persistSelection()
            let (start, end) = self.computeWindow(now: now)
            self.transition(to: self.initialPhase(now: now, start: start, end: end))
        }
    }

    /// Mirrors Android's `AlarmSchedule.initialPhase`.
    private func initialPhase(now: Date, start: Date, end: Date) -> AlarmPhase {
        if now >= start {
            return .inWindow(start: start, end: end)
        }
        if let baselineStart = Tuning.baselineStart(
            windowStart: start, leadMinutes: activationLeadMinutes
        ), now < baselineStart {
            return .armed(start: start, end: end)
        }
        return .monitoring(start: start, end: end)
    }

    private func applyLeadChangeToActivePhase() {
        switch phase {
        case .armed(let s, let e), .monitoring(let s, let e):
            transition(to: initialPhase(now: .now, start: s, end: e))
        default:
            break
        }
    }

    // MARK: Persistence

    private static let kSelectedHour = "akaalarm.selectedHour"
    private static let kSelectedMinute = "akaalarm.selectedMinute"
    private static let kSensitivity = "akaalarm.sensitivity"
    private static let kActivationLead = "akaalarm.activationLeadMinutes"

    private func persistSelection() {
        UserDefaults.standard.set(selectedHour, forKey: Self.kSelectedHour)
        UserDefaults.standard.set(selectedMinute, forKey: Self.kSelectedMinute)
    }

    /// `nil` on a fresh install; subsequent launches read the last value the user
    /// confirmed (i.e., from the last successful `startAlarm`).
    private static func loadPersistedSelection() -> (Int, Int)? {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: kSelectedHour) != nil,
              defaults.object(forKey: kSelectedMinute) != nil else { return nil }
        return (defaults.integer(forKey: kSelectedHour),
                defaults.integer(forKey: kSelectedMinute))
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

    private func handleInterruption(began: Bool) {
        // Only meaningful while we're actively listening — once the alarm is firing
        // or snoozing, the mic isn't running anyway.
        guard phase.kind == .monitoring || phase.kind == .inWindow else { return }
        if began {
            postInterruptionNotification()
        } else {
            cancelInterruptionNotification()
        }
    }

    private func handleAppDidBecomeActive() {
        switch phase {
        case .armed(let start, let end):
            // Self-heal: if the phase timer stalled while suspended and the
            // activation time has passed, catch up now.
            if let baselineStart = Tuning.baselineStart(
                windowStart: start, leadMinutes: activationLeadMinutes
            ), Date.now >= baselineStart {
                transition(to: initialPhase(now: .now, start: start, end: end))
            }
        case .monitoring, .inWindow:
            if !mic.isRunning {
                do { try mic.start() } catch { print("Mic restart on foreground failed: \(error)") }
                mic.setSpikeDetectionEnabled(phase.kind == .inWindow)
            }
        default:
            break
        }
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
            cancelInterruptionNotification()

        case .armed(let start, _):
            // Alarm set, mic deliberately off until the activation lead. The
            // screen stays on (idle timer disabled below) so the phase timer
            // keeps ticking; handleAppDidBecomeActive catches up if we were
            // suspended past the activation time.
            mic.stop()
            motion.stop()
            player.stop()
            micLevelDB = Tuning.dbFloor
            baselineDB = Tuning.dbFloor
            if let baselineStart = Tuning.baselineStart(
                windowStart: start, leadMinutes: activationLeadMinutes
            ) {
                scheduleTransition(at: baselineStart) { [weak self] in
                    guard let self else { return }
                    guard case .armed(let s, let e) = self.phase else { return }
                    self.transition(to: .monitoring(start: s, end: e))
                }
            }

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

        // Keep the screen on whenever an alarm is armed. Without this iOS auto-locks
        // and (in some scenarios) suspends us harder, especially on a non-charging device.
        UIApplication.shared.isIdleTimerDisabled = (next.kind != .idle)
    }

    private func scheduleTransition(at date: Date, _ action: @escaping () -> Void) {
        let interval = max(0.01, date.timeIntervalSinceNow)
        phaseTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { _ in
            DispatchQueue.main.async(execute: action)
        }
    }

    // MARK: Local notifications

    private func postInterruptionNotification() {
        let content = UNMutableNotificationContent()
        content.title = "aka Alarm was paused"
        content.body = "Another app took over the microphone. Tap to reopen and resume listening."
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: Self.interruptionNotificationID,
            content: content,
            trigger: nil // deliver immediately
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    private func cancelInterruptionNotification() {
        UNUserNotificationCenter.current()
            .removeDeliveredNotifications(withIdentifiers: [Self.interruptionNotificationID])
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [Self.interruptionNotificationID])
    }

    private static let interruptionNotificationID = "akaalarm.interruption"

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

#if DEBUG
    /// Preview-only: construct a store pinned to a specific phase, skipping
    /// the normal transition pipeline so no mic/motion/audio side effects fire.
    static func preview(phase: AlarmPhase) -> AlarmStore {
        let store = AlarmStore()
        store.phase = phase
        return store
    }
#endif
}
