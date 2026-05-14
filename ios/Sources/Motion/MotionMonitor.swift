import Foundation
import CoreMotion

/// Watches accelerometer samples and reports a "snooze nudge" whenever the device's
/// instantaneous acceleration deviates from a slowly-drifting rest baseline by more
/// than `Tuning.snoozeAccelDelta` (in g). The drifting baseline keeps the detector
/// from re-firing as the phone tilts slowly on its own.
final class MotionMonitor {
    var onSnoozeNudge: (() -> Void)?

    private let manager = CMMotionManager()
    private let queue = OperationQueue()
    private var baseline: (x: Double, y: Double, z: Double)?
    private var running = false
    private var cooldownUntil: TimeInterval = 0

    func start() {
        guard !running, manager.isAccelerometerAvailable else { return }
        manager.accelerometerUpdateInterval = 1.0 / Tuning.motionSamplingHz
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInteractive
        baseline = nil
        cooldownUntil = 0
        manager.startAccelerometerUpdates(to: queue) { [weak self] data, _ in
            guard let self, let data else { return }
            self.process(data.acceleration)
        }
        running = true
    }

    func stop() {
        if !running { return }
        manager.stopAccelerometerUpdates()
        running = false
        baseline = nil
    }

    private func process(_ a: CMAcceleration) {
        let now = ProcessInfo.processInfo.systemUptime
        guard let baseline else {
            self.baseline = (a.x, a.y, a.z)
            return
        }
        let dx = a.x - baseline.x
        let dy = a.y - baseline.y
        let dz = a.z - baseline.z
        let delta = (dx * dx + dy * dy + dz * dz).squareRoot()

        if delta > Tuning.snoozeAccelDelta {
            if now >= cooldownUntil {
                cooldownUntil = now + 1.0
                self.baseline = (a.x, a.y, a.z)
                onSnoozeNudge?()
            }
        } else {
            // Slowly drift baseline so gradual tilts don't accumulate.
            let alpha = 0.02
            self.baseline = (
                baseline.x * (1 - alpha) + a.x * alpha,
                baseline.y * (1 - alpha) + a.y * alpha,
                baseline.z * (1 - alpha) + a.z * alpha
            )
        }
    }
}
