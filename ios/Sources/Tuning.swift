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
    /// How far above baseline (in dB) counts as "user started stirring".
    static let spikeThresholdDB: Double = 6.0
    /// Minimum number of baseline cells before we trust spike detection.
    static let minBaselineCells: Int = 10
    /// UI-side level meter refresh rate.
    static let levelMeterHz: Double = 30
    /// dB floor; anything below counts as silence.
    static let dbFloor: Double = -80

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
