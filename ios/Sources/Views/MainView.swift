import SwiftUI
import UIKit
import UniformTypeIdentifiers

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
        .readingVerticalBarEdge()
    }
}

// MARK: - Set Alarm

private struct SetAlarmView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.verticalBarEdge) private var verticalBarEdge
    @State private var showSettings = false

    private let minuteOptions = [0, 15, 30, 45]
    private let rowHeight: CGFloat = 80

    /// Below this much content height the five-row wheels (400 pt), the
    /// window label and the Start button no longer stack: iPhone Duo's outer
    /// display, any landscape canvas (the Duo's inner display ignores our
    /// portrait lock), and split-screen multitasking.
    private let stackedLayoutMinHeight: CGFloat = 700
    /// Without the Start button in the column (vertical-bar layout) five rows
    /// fit on a shorter canvas.
    private let stackedLayoutMinHeightWithoutStart: CGFloat = 600

    var body: some View {
        Group {
            if verticalBarEdge == .none {
                canvas(withStart: true)
                    .overlay(alignment: .topTrailing) {
                        SettingsGearButton { showSettings = true }
                            .gearOverlayPlacement()
                    }
            } else {
                // iPhone Duo draws a vertical bar along one edge (the outer
                // display, the inner one in landscape) and lays standard
                // toolbar items out in it. The gear and Start go there and the
                // canvas keeps only the picker. See VerticalBar.swift.
                NavigationStack {
                    verticalBarCanvas
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button {
                                    showSettings = true
                                } label: {
                                    Label("Settings", systemImage: "gearshape")
                                }
                            }
                        }
                }
            }
        }
        .sheet(isPresented: $showSettings) { SensitivitySheet() }
    }


    /// Layout next to a vertical bar: the title sits level with the camera
    /// cut-out at the top of the bar, the window label at the bottom with the
    /// Start button beside it in the bar column, and the picker takes
    /// everything in between.
    ///
    /// Start is our own button rather than a toolbar item: the system parks
    /// a bottom-bar item 24 pt above the screen edge, 10 pt below where the
    /// label can sit inside the safe area, and the item can't be moved.
    private var verticalBarCanvas: some View {
        GeometryReader { geo in
            // The cut-out is a reserved region; without one (or on the 27.0
            // SDK) fall back to a band the height of a bar item.
            let cameraMidY = cameraCenterY(in: geo) ?? BarMetrics.itemHeight / 2
            let titleBand = max(2 * cameraMidY, BarMetrics.itemHeight)
            let labelBand = BarMetrics.itemHeight
            let middle = geo.size.height - titleBand - labelBand
            let rows = middle >= 5 * rowHeight + 40 ? 5 : 3
            // The bar column is the safe-area inset on its edge.
            let leading = verticalBarEdge == .leading
            let barWidth = leading ? geo.safeAreaInsets.leading : geo.safeAreaInsets.trailing

            VStack(spacing: 0) {
                title
                    .frame(height: titleBand)
                wheels(rows: rows)
                    .frame(maxHeight: .infinity)
                windowLabelText
                    .frame(height: labelBand)
            }
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal)
            .overlay(alignment: leading ? .bottomLeading : .bottomTrailing) {
                startBarButton
                    .padding(leading ? .leading : .trailing, BarMetrics.edgeMargin)
                    .frame(width: barWidth, height: labelBand,
                           alignment: leading ? .leading : .trailing)
                    .offset(x: leading ? -barWidth : barWidth)
            }
        }
    }

    /// Start styled like the gear beside it in the bar: a plain glass circle.
    private var startBarButton: some View {
        GlassCircleButton(systemImage: "play.fill", label: "Start") {
            Task { await store.startAlarm() }
        }
    }

    private func cameraCenterY(in geo: GeometryProxy) -> CGFloat? {
        #if canImport(SwiftUICore, _version: 8.0.85)
        if #available(iOS 27.1, *) {
            return geo.reservedRegions(kind: .occlusion)
                .first(where: \.isActive)?.frame.midY
        }
        #endif
        return nil
    }

    private func canvas(withStart: Bool) -> some View {
        // Size-driven, never device-driven (Apple's guidance for iPhone Duo):
        // wider than tall goes side by side, short canvases get three-row
        // wheels. Regular iPhones in portrait are unchanged.
        GeometryReader { geo in
            let minHeight = withStart ? stackedLayoutMinHeight : stackedLayoutMinHeightWithoutStart
            let short = geo.size.height < minHeight
            let sideBySide = geo.size.width > geo.size.height
            // Side by side only the title shares the column with the wheels,
            // so five rows fit from ~520 pt of height; stacked needs ~700.
            let rows = (sideBySide ? geo.size.height >= 520 : !short) ? 5 : 3

            if sideBySide {
                HStack(spacing: 24) {
                    VStack(spacing: 16) {
                        title
                        wheels(rows: rows)
                    }
                    .frame(maxWidth: .infinity)

                    VStack(spacing: 32) {
                        windowLabelText
                        if withStart { startButton }
                    }
                    .frame(maxWidth: .infinity)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 24) {
                    Spacer(minLength: 0)
                    title
                    wheels(rows: rows)
                    windowLabelText
                    Spacer(minLength: 0)
                    if withStart { startButton }
                }
                // On a regular-width canvas (Duo's inner display) the pills and
                // the Start button would otherwise stretch across the screen.
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .padding()
    }

    private var title: some View {
        Text("Wake-up window")
            .font(.title2)
            .foregroundStyle(.secondary)
    }

    private func wheels(rows: Int) -> some View {
        HStack(spacing: 0) {
            wheel(selection: $store.selectedHour, values: Array(0..<24))

            Text(":")
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(.secondary)

            wheel(selection: $store.selectedMinute, values: minuteOptions)
        }
        .frame(height: CGFloat(rows) * rowHeight)
        .padding(.horizontal, 16)
    }

    private func wheel(selection: Binding<Int>, values: [Int]) -> some View {
        NumberWheel(
            selection: selection,
            values: values,
            rowHeight: rowHeight,
            fontSize: 44
        )
        .frame(maxWidth: .infinity)
        .background(alignment: .center) {
            Color.clear
                .glassEffect(in: .capsule)
                .frame(height: rowHeight)
                .padding(.horizontal, 12)
        }
    }

    private var windowLabelText: some View {
        Text(windowLabel)
            .font(.system(size: 72, weight: .thin, design: .rounded))
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .padding(.horizontal, 32)
    }

    private var startButton: some View {
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
    @Environment(\.verticalBarEdge) private var verticalBarEdge
    @State private var dragOffset: CGFloat = 0
    @State private var dimOpacity: Double = 0
    @State private var showSettings = false

    var body: some View {
        Group {
            if verticalBarEdge == .none {
                screen
            } else {
                // iPhone Duo: the gear joins the system's vertical bar.
                NavigationStack {
                    screen.toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                showSettings = true
                            } label: {
                                Label("Settings", systemImage: "gearshape")
                            }
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showSettings) { SensitivitySheet() }
    }

    private var screen: some View {
        ZStack {
            // On a partially open iPhone Duo (the nightstand "tent" pose) the
            // clock goes to the segment away from the hinge, readable from the
            // pillow; the level bar, status and cancel hint stay on the near
            // side. Everywhere else this is the plain stacked layout.
            FoldAwareSplit {
                VStack(spacing: 24) {
                    Spacer(minLength: 0)
                    clock
                    windowRange
                    Spacer(minLength: 0)
                }
            } secondary: {
                VStack(spacing: 24) {
                    Spacer(minLength: 0)
                    micBar
                    status
                    Spacer(minLength: 0)
                    SlideUpHint(label: "Slide up to cancel")
                }
            } flat: {
                VStack(spacing: 24) {
                    Spacer(minLength: 0)
                    clock
                    windowRange
                    micBar
                    status
                    Spacer(minLength: 0)
                    SlideUpHint(label: "Slide up to cancel")
                }
            }
            .padding()
            // Full width regardless of content: in the armed state the level bar
            // (the only greedy child) is hidden, and without this the VStack
            // shrinks to the status text's width — dragging the gear overlay's
            // "top trailing" corner toward the screen centre.
            .frame(maxWidth: .infinity)
            // Gear sits *before* the dim overlay in the ZStack so it fades to
            // dark along with everything else; the overlay's hit-testing is off,
            // so the button stays tappable (tapping also resets the dim).
            .overlay(alignment: .topTrailing) {
                if verticalBarEdge == .none {
                    SettingsGearButton { showSettings = true }
                        .gearOverlayPlacement()
                }
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
    }

    private var clock: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            Text(context.date, format: .dateTime.hour().minute().second())
                .font(.system(size: 64, weight: .thin, design: .rounded))
                .monospacedDigit()
        }
    }

    @ViewBuilder
    private var windowRange: some View {
        if let w = store.phase.window {
            Text("\(w.start, format: .dateTime.hour().minute()) – \(w.end, format: .dateTime.hour().minute())")
                .font(.title3)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var micBar: some View {
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
    }

    private var status: some View {
        Text(statusText)
            .font(.callout)
            .foregroundStyle(.secondary)
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

/// Sizes shared by the glass circle controls and the Duo's vertical bar.
enum BarMetrics {
    /// A standard bar item; the glass circles match it.
    static let itemHeight: CGFloat = 48
    /// `.glass` pads its label by this much per side.
    static let glassPadding: CGFloat = 7
    /// The system keeps its vertical-bar items this far from the screen edge
    /// (measured on the Duo simulator).
    static let edgeMargin: CGFloat = 24
}

/// A bar-item-sized glass circle around a symbol: the look of a toolbar
/// button, usable outside a toolbar.
struct GlassCircleButton: View {
    let systemImage: String
    let label: LocalizedStringKey
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(.primary)
                .frame(width: BarMetrics.itemHeight - 2 * BarMetrics.glassPadding,
                       height: BarMetrics.itemHeight - 2 * BarMetrics.glassPadding)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(label)
    }
}

struct SettingsGearButton: View {
    let action: () -> Void

    var body: some View {
        GlassCircleButton(systemImage: "gearshape", label: "Settings", action: action)
    }
}

extension View {
    /// Places the gear in the top-trailing corner. On iPhone Duo's inner
    /// display the status bar's trailing cluster is a circle on the bar
    /// margin, so a regular-width canvas uses that margin and the gear shares
    /// the circle's axis; compact phones keep the usual 16 pt.
    func gearOverlayPlacement() -> some View {
        modifier(GearOverlayPlacement())
    }
}

private struct GearOverlayPlacement: ViewModifier {
    @Environment(\.horizontalSizeClass) private var sizeClass

    func body(content: Content) -> some View {
        content
            .padding(.top, 4)
            .padding(.trailing, sizeClass == .regular ? BarMetrics.edgeMargin : 16)
    }
}

struct SensitivitySheet: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var showSoundImporter = false

    private var micActive: Bool {
        store.phase.kind == .monitoring || store.phase.kind == .inWindow
    }

    var body: some View {
        ScrollView {
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

            Text("Alarm sound")
                .font(.headline)
                .padding(.top, 12)
            // The current pick as a capsule token, mirroring Android's chip:
            // tap it to choose a file; the × on a custom sound returns to the
            // built-in tone.
            HStack(spacing: 8) {
                Image(systemName: "music.note")
                    .font(.subheadline)
                Text(store.alarmSoundName ?? Tuning.builtInToneName)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                if store.alarmSoundName != nil {
                    Button {
                        store.clearCustomAlarmSound()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.caption.weight(.bold))
                            .padding(4)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Use the built-in tone")
                }
            }
            .padding(.leading, 14)
            .padding(.trailing, store.alarmSoundName == nil ? 14 : 8)
            .padding(.vertical, 8)
            .glassEffect(in: .capsule)
            .contentShape(Capsule())
            .onTapGesture { showSoundImporter = true }

            Text("Whichever you pick fades in from silence over a minute.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            if let problem = store.alarmSoundError {
                Text(problem)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
            // iOS has no picker for the system's own alarm tones, so the
            // user's files (Files app, iCloud Drive, downloads) are the one
            // source here — same glass capsule as the Start button.
            Button {
                showSoundImporter = true
            } label: {
                Text("Audio file…")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.capsule)
            .padding(.top, 4)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .fileImporter(
            isPresented: $showSoundImporter,
            allowedContentTypes: [.audio]
        ) { result in
            if case .success(let url) = result {
                store.importAlarmSound(from: url)
            }
        }
        // A fraction, not a fixed height: 620 pt is taller than iPhone Duo's
        // outer display. Three quarters shows all three sections on a regular
        // iPhone and the sheet scrolls where it can't.
        .presentationDetents([.fraction(0.75), .large])
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

