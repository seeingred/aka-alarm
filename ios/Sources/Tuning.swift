import Foundation

/// All tunable behaviour lives here. Tweak values and rebuild.
enum Tuning {
    // MARK: Wake window
    static let wakeWindowDuration: TimeInterval = 30 * 60

    // MARK: Snooze
    static let snoozeMinDuration: TimeInterval = 60
    static let snoozeMaxDuration: TimeInterval = 15 * 60

    // MARK: Microphone / spike detection
    /// Rolling window over which the ambient baseline is averaged.
    static let baselineWindow: TimeInterval = 5 * 60
    /// How wide each baseline bucket is (we average buffers into one bucket every N seconds).
    static let baselineCellSeconds: TimeInterval = 1.0
    /// The spike threshold (how far above baseline, in dB, the *peak* moment in a
    /// cell must rise to count as a spike) is user-tunable via the sensitivity
    /// slider. Slider 0.0 (very low) maps to `sensitivityMaxThresholdDB`,
    /// 1.0 (very high) to `sensitivityMinThresholdDB`; the default 0.5 lands on
    /// the historical 4.5 dB. Using peak (loudest moment in the cell) rather than
    /// the cell mean lets brief sheet rustles and sighs trigger the alarm — they'd
    /// otherwise vanish in a 1-second average.
    static let sensitivityMinThresholdDB: Double = 1.0
    static let sensitivityMaxThresholdDB: Double = 8.0
    static let defaultSensitivity: Double = 0.5

    static func spikeThresholdDB(sensitivity: Double) -> Double {
        let s = min(1, max(0, sensitivity))
        return sensitivityMaxThresholdDB - s * (sensitivityMaxThresholdDB - sensitivityMinThresholdDB)
    }
    /// Minimum number of baseline cells before we trust spike detection.
    static let minBaselineCells: Int = 10
    /// UI-side level meter refresh rate.
    static let levelMeterHz: Double = 30
    /// Detection floor: dB values below this are clamped (silence). Used by the
    /// spike algorithm — keep the full dynamic range.
    static let dbFloor: Double = -80
    /// Display floor for the level meter only. -60 fills the bar more aggressively
    /// so subtle nighttime movement is visible on screen; the detector itself still
    /// uses `dbFloor`.
    static let displayDbFloor: Double = -60

    /// Watchdog: how often the timer checks "are we still receiving buffers?".
    static let micWatchdogInterval: TimeInterval = 1.0
    /// Watchdog: if more than this many seconds pass with no audio buffer while we
    /// think we're running, declare the mic dead, notify the user, and rebuild
    /// the engine. Catches the YouTube-style hijack where iOS never sends a
    /// formal interruption notification but the tap stops delivering audio.
    static let micWatchdogTimeout: TimeInterval = 3.0
    /// Coalesce rapid route-change notifications (e.g. fast AirPods on/off) into
    /// a single engine reconfigure after the last change quiets down.
    static let routeChangeDebounce: TimeInterval = 0.4

    // MARK: Motion / snooze nudge
    /// Gyroscope rotation magnitude (rad/s) above which a device pickup or tilt
    /// counts as a snooze nudge. Using rotation rather than acceleration makes
    /// the detector robust to the alarm's own vibration buzz.
    static let snoozeRotationThreshold: Double = 1.5
    static let motionSamplingHz: Double = 50

    // MARK: Alarm tone
    static let alarmFadeDuration: TimeInterval = 60
    static let alarmStartVolume: Float = 0.01
    static let alarmEndVolume: Float = 1.0

    // MARK: Vibration
    /// Period between vibration pulses while the alarm is firing.
    static let vibrationPulseInterval: TimeInterval = 1.5

    // MARK: Screen
    /// Time over which the dim overlay fades from clear to dark while
    /// monitoring/snoozing. iOS doesn't expose per-app brightness so this
    /// is a UI-only effect, but it reads as "the app is dimming for sleep".
    static let dimFadeDuration: TimeInterval = 30
    /// Final opacity of the dim overlay (0 = invisible, 1 = pure black).
    static let dimEndOpacity: Double = 0.85
}
