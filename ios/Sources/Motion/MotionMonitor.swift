import Foundation
import CoreMotion

/// Watches device-motion gyroscope samples and reports a "snooze nudge" whenever
/// the rotation-rate magnitude exceeds `Tuning.snoozeRotationThreshold`. Picking up
/// or tilting the phone produces clear rotation; vibration buzz produces almost
/// none, so this detector ignores the alarm's own haptics by construction.
final class MotionMonitor {
    var onSnoozeNudge: (() -> Void)?

    private let manager = CMMotionManager()
    private let queue = OperationQueue()
    private var running = false
    private var cooldownUntil: TimeInterval = 0

    func start() {
        guard !running, manager.isDeviceMotionAvailable else { return }
        manager.deviceMotionUpdateInterval = 1.0 / Tuning.motionSamplingHz
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInteractive
        cooldownUntil = 0
        manager.startDeviceMotionUpdates(to: queue) { [weak self] data, _ in
            guard let self, let data else { return }
            self.process(data.rotationRate)
        }
        running = true
    }

    func stop() {
        if !running { return }
        manager.stopDeviceMotionUpdates()
        running = false
    }

    private func process(_ r: CMRotationRate) {
        let now = ProcessInfo.processInfo.systemUptime
        let magnitude = (r.x * r.x + r.y * r.y + r.z * r.z).squareRoot()
        if magnitude > Tuning.snoozeRotationThreshold, now >= cooldownUntil {
            cooldownUntil = now + 1.0
            onSnoozeNudge?()
        }
    }
}
