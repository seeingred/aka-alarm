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
    /// Acceleration magnitude (g) above local rest that counts as a "nudge".
    static let snoozeAccelDelta: Double = 0.15
    static let motionSamplingHz: Double = 50

    // MARK: Alarm tone
    static let alarmFadeDuration: TimeInterval = 60
    static let alarmStartVolume: Float = 0.01
    static let alarmEndVolume: Float = 1.0
}
