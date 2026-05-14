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
            case .monitoring, .inWindow:
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
        .onAppear { store.resetSelectedToCurrentWindow() }
        .task { store.resetSelectedToCurrentWindow() }
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

    var body: some View {
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

            MicLevelView(currentDB: store.micLevelDB, baselineDB: store.baselineDB)
                .frame(height: 80)
                .padding(.horizontal, 32)

            Text(store.phase.kind == .inWindow ? "Listening for stirring…" : "Learning room baseline…")
                .font(.callout)
                .foregroundStyle(.secondary)

            Spacer(minLength: 0)

            SlideUpHint(label: "Slide up to cancel")
        }
        .padding()
        .offset(y: dragOffset)
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
    }
}

// MARK: - Mic level

struct MicLevelView: View {
    let currentDB: Double
    let baselineDB: Double

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
            }
        }
    }

    static func normalize(_ db: Double) -> CGFloat {
        let clamped = max(Tuning.dbFloor, min(0, db))
        return CGFloat((clamped - Tuning.dbFloor) / -Tuning.dbFloor)
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

