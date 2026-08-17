import SwiftUI
import UIKit

struct MainView: View {
    @EnvironmentObject private var store: AlarmStore

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            switch store.phase {
            case .idle:
                SetAlarmView()
            case .armed, .monitoring, .inWindow:
                MonitoringView()
            default:
                EmptyView()
            }
        }
        .alert("Microphone access required",
               isPresented: $store.micPermissionDenied) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("aka Alarm needs the microphone to detect when you start stirring. Enable it in Settings → aka Alarm.")
        }
    }
}

// MARK: - Set Alarm

private struct SetAlarmView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var showSettings = false

    private let minuteOptions = [0, 15, 30, 45]

    var body: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 0)

            Text("Wake-up window")
                .font(.title2)
                .foregroundStyle(.secondary)

            HStack(spacing: 0) {
                NumberWheel(
                    selection: $store.selectedHour,
                    values: Array(0..<24),
                    rowHeight: 80,
                    fontSize: 44
                )
                .frame(maxWidth: .infinity)
                .background(alignment: .center) {
                    Color.clear
                        .glassEffect(in: .capsule)
                        .frame(height: 80)
                        .padding(.horizontal, 12)
                }

                Text(":")
                    .font(.system(size: 40, weight: .light))
                    .foregroundStyle(.secondary)

                NumberWheel(
                    selection: $store.selectedMinute,
                    values: minuteOptions,
                    rowHeight: 80,
                    fontSize: 44
                )
                .frame(maxWidth: .infinity)
                .background(alignment: .center) {
                    Color.clear
                        .glassEffect(in: .capsule)
                        .frame(height: 80)
                        .padding(.horizontal, 12)
                }
            }
            .frame(height: 400)
            .padding(.horizontal, 16)

            Text(windowLabel)
                .font(.system(size: 72, weight: .thin, design: .rounded))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.horizontal, 32)

            Spacer(minLength: 0)

            Button {
                Task { await store.startAlarm() }
            } label: {
                Text("Start")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
        }
        .padding()
        .overlay(alignment: .topTrailing) {
            SettingsGearButton { showSettings = true }
        }
        .sheet(isPresented: $showSettings) { SensitivitySheet() }
    }

    private var windowLabel: String {
        let totalStart = store.selectedHour * 60 + store.selectedMinute
        let totalEnd = totalStart + Int(Tuning.wakeWindowDuration / 60)
        let sh = (totalStart / 60) % 24, sm = totalStart % 60
        let eh = (totalEnd / 60) % 24, em = totalEnd % 60
        return String(format: "%02d:%02d – %02d:%02d", sh, sm, eh, em)
    }
}

// MARK: - Monitoring

private struct MonitoringView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var dragOffset: CGFloat = 0
    @State private var dimOpacity: Double = 0
    @State private var showSettings = false

    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Spacer(minLength: 0)

                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(context.date, format: .dateTime.hour().minute().second())
                        .font(.system(size: 64, weight: .thin, design: .rounded))
                        .monospacedDigit()
                }

                if let w = store.phase.window {
                    Text("\(w.start, format: .dateTime.hour().minute()) – \(w.end, format: .dateTime.hour().minute())")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }

                if store.phase.kind != .armed {
                    MicLevelView(
                        currentDB: store.micLevelDB,
                        baselineDB: store.baselineDB,
                        thresholdDB: store.baselineDB
                            + Tuning.spikeThresholdDB(sensitivity: store.sensitivity)
                    )
                    .frame(height: 80)
                    .padding(.horizontal, 32)
                }

                Text(statusText)
                    .font(.callout)
                    .foregroundStyle(.secondary)

                Spacer(minLength: 0)

                SlideUpHint(label: "Slide up to cancel")
            }
            .padding()
            // Gear sits *before* the dim overlay in the ZStack so it fades to
            // dark along with everything else; the overlay's hit-testing is off,
            // so the button stays tappable (tapping also resets the dim).
            .overlay(alignment: .topTrailing) {
                SettingsGearButton { showSettings = true }
            }
            .offset(y: dragOffset)

            // Dim overlay — UI-only "this app is dimming for sleep" effect since
            // iOS doesn't expose per-app screen brightness. Hit-test is disabled
            // so the slide-up DragGesture below still receives input.
            Color.black
                .opacity(dimOpacity)
                .ignoresSafeArea()
                .allowsHitTesting(false)
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture()
                .onChanged { v in
                    if v.translation.height < 0 {
                        dragOffset = v.translation.height
                    }
                }
                .onEnded { v in
                    if v.translation.height < -120 {
                        store.cancelAlarm()
                    }
                    withAnimation(.spring) { dragOffset = 0 }
                }
        )
        .simultaneousGesture(TapGesture().onEnded { resetDim() })
        .onAppear { startDimFade() }
        .sheet(isPresented: $showSettings) { SensitivitySheet() }
    }

    private var statusText: String {
        switch store.phase {
        case .armed(let start, _):
            if let baselineStart = Tuning.baselineStart(
                windowStart: start, leadMinutes: store.activationLeadMinutes
            ) {
                let time = baselineStart.formatted(.dateTime.hour().minute())
                return "Alarm armed — mic off until \(time)"
            }
            return "Alarm armed — mic off"
        case .inWindow:
            return "Listening for stirring…"
        default:
            return "Learning room baseline…"
        }
    }

    private func startDimFade() {
        dimOpacity = 0
        withAnimation(.linear(duration: Tuning.dimFadeDuration)) {
            dimOpacity = Tuning.dimEndOpacity
        }
    }

    private func resetDim() {
        withAnimation(.easeOut(duration: 0.25)) { dimOpacity = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation(.linear(duration: Tuning.dimFadeDuration)) {
                dimOpacity = Tuning.dimEndOpacity
            }
        }
    }
}

// MARK: - Settings

struct SettingsGearButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "gearshape")
                .font(.system(size: 18, weight: .regular))
                .foregroundStyle(.secondary)
                .padding(12)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Settings")
    }
}

struct SensitivitySheet: View {
    @EnvironmentObject private var store: AlarmStore

    private var micActive: Bool {
        store.phase.kind == .monitoring || store.phase.kind == .inWindow
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Sensitivity")
                .font(.headline)
            Text(String(
                format: "How easily sound above the room's baseline triggers the alarm. Trigger point: +%.1f dB over baseline.",
                Tuning.spikeThresholdDB(sensitivity: store.sensitivity)
            ))
            .font(.footnote)
            .foregroundStyle(.secondary)

            // Live calibration aid: when the mic is running, show the same level
            // bar as the monitoring screen so the trigger line can be tuned
            // against real room noise.
            if micActive {
                MicLevelView(
                    currentDB: store.micLevelDB,
                    baselineDB: store.baselineDB,
                    thresholdDB: store.baselineDB
                        + Tuning.spikeThresholdDB(sensitivity: store.sensitivity)
                )
                .frame(height: 40)
                .padding(.vertical, 8)
            }

            // Auto-saves on every change via AlarmStore.sensitivity's didSet.
            // Same 15 discrete positions as Android → 0.5 dB per step.
            Slider(value: $store.sensitivity, in: 0...1, step: 1.0 / 14.0)
            HStack {
                Text("Very low")
                Spacer()
                Text("Very high")
            }
            .font(.caption2)
            .foregroundStyle(.secondary)

            Text("Start listening")
                .font(.headline)
                .padding(.top, 12)
            Text(
                "When the microphone turns on: \(Tuning.activationLeadLabel(minutes: store.activationLeadMinutes)). "
                + "A later start saves battery overnight; the alarm always fires by the end of the window either way."
            )
            .font(.footnote)
            .foregroundStyle(.secondary)

            // Discrete positions over Tuning.activationLeadOptionsMinutes,
            // matching the Android sheet. Auto-saves via the didSet.
            Slider(
                value: Binding(
                    get: {
                        Double(
                            Tuning.activationLeadOptionsMinutes
                                .firstIndex(of: store.activationLeadMinutes) ?? 0
                        )
                    },
                    set: { raw in
                        let options = Tuning.activationLeadOptionsMinutes
                        let idx = min(max(Int(raw.rounded()), 0), options.count - 1)
                        store.activationLeadMinutes = options[idx]
                    }
                ),
                in: 0...Double(Tuning.activationLeadOptionsMinutes.count - 1),
                step: 1
            )
            HStack {
                Text("Right away")
                Spacer()
                Text("5 min before")
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .presentationDetents([.height(440)])
    }
}

// MARK: - Mic level

struct MicLevelView: View {
    let currentDB: Double
    let baselineDB: Double
    var thresholdDB: Double? = nil

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                // Track: faint glass capsule.
                Capsule()
                    .fill(.clear)
                    .glassEffect(in: .capsule)

                // Level: brighter glass capsule that grows with the live mic level.
                Capsule()
                    .fill(.white.opacity(0.35))
                    .glassEffect(in: .capsule)
                    .frame(width: max(2, geo.size.width * Self.normalize(currentDB)))
                    .animation(.linear(duration: 0.05), value: currentDB)

                // Baseline marker — kept warm so it pops against the cool glass.
                Rectangle()
                    .fill(Color.orange)
                    .frame(width: 2, height: geo.size.height + 12)
                    .offset(x: geo.size.width * Self.normalize(baselineDB), y: -6)
                    .animation(.linear(duration: 0.5), value: baselineDB)

                // Trigger marker — where a peak has to reach to fire the alarm.
                // Hidden until the baseline has climbed above the display floor.
                if let threshold = thresholdDB, baselineDB > Tuning.displayDbFloor {
                    Rectangle()
                        .fill(Color.red)
                        .frame(width: 2, height: geo.size.height + 12)
                        .offset(x: geo.size.width * Self.normalize(threshold), y: -6)
                        .animation(.linear(duration: 0.5), value: threshold)
                }
            }
        }
    }

    static func normalize(_ db: Double) -> CGFloat {
        // Display uses a tighter floor (`displayDbFloor`) than the detector so
        // subtle ambient and movement levels visibly fill the bar at night.
        let floor = Tuning.displayDbFloor
        let clamped = max(floor, min(0, db))
        return CGFloat((clamped - floor) / -floor)
    }
}

// MARK: - Slide hint

struct SlideUpHint: View {
    let label: String
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: "chevron.compact.up")
                .font(.system(size: 28, weight: .light))
                .foregroundStyle(.secondary)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.bottom, 8)
    }
}

// MARK: - Number wheel

/// SwiftUI's `Picker(.wheel)` ignores `.frame(height:)` — its embedded
/// UIPickerView always reports the same intrinsic height regardless of
/// what frame we propose. This wraps UIPickerView directly so we can
/// control row height (and therefore total height) and font size.
struct NumberWheel: UIViewRepresentable {
    @Binding var selection: Int
    let values: [Int]
    let rowHeight: CGFloat
    let fontSize: CGFloat

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UIPickerView {
        let pv = UIPickerView()
        pv.delegate = context.coordinator
        pv.dataSource = context.coordinator
        pv.backgroundColor = .clear
        if let idx = values.firstIndex(of: selection) {
            pv.selectRow(idx, inComponent: 0, animated: false)
        }
        return pv
    }

    func updateUIView(_ uiView: UIPickerView, context: Context) {
        context.coordinator.parent = self
        uiView.reloadAllComponents()
        if let idx = values.firstIndex(of: selection),
           uiView.selectedRow(inComponent: 0) != idx {
            uiView.selectRow(idx, inComponent: 0, animated: false)
        }
    }

    /// Without this, UIPickerView reports a ~320 pt intrinsic width and
    /// two side-by-side pickers blow past the iPhone screen, stretching
    /// every parent container with it.
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIPickerView, context: Context) -> CGSize? {
        CGSize(
            width: proposal.width ?? 120,
            height: proposal.height ?? (rowHeight * 5)
        )
    }

    final class Coordinator: NSObject, UIPickerViewDelegate, UIPickerViewDataSource {
        var parent: NumberWheel
        init(_ parent: NumberWheel) { self.parent = parent }

        func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }

        func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
            parent.values.count
        }

        func pickerView(_ pickerView: UIPickerView, rowHeightForComponent component: Int) -> CGFloat {
            parent.rowHeight
        }

        func pickerView(_ pickerView: UIPickerView,
                        viewForRow row: Int,
                        forComponent component: Int,
                        reusing view: UIView?) -> UIView {
            let label = (view as? UILabel) ?? UILabel()
            label.text = String(format: "%02d", parent.values[row])
            label.font = .monospacedDigitSystemFont(ofSize: parent.fontSize, weight: .regular)
            label.textAlignment = .center
            return label
        }

        func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
            parent.selection = parent.values[row]
        }
    }
}

// MARK: - Previews

#if DEBUG
#Preview("Set alarm (idle)") {
    MainView()
        .environmentObject(AlarmStore())
        .preferredColorScheme(.dark)
}

#Preview("Monitoring (pre-window)") {
    MainView()
        .environmentObject(AlarmStore.preview(phase: .monitoring(
            start: .now.addingTimeInterval(45 * 60),
            end:   .now.addingTimeInterval(75 * 60)
        )))
        .preferredColorScheme(.dark)
}

#Preview("In wake window") {
    MainView()
        .environmentObject(AlarmStore.preview(phase: .inWindow(
            start: .now.addingTimeInterval(-5 * 60),
            end:   .now.addingTimeInterval(25 * 60)
        )))
        .preferredColorScheme(.dark)
}
#endif

