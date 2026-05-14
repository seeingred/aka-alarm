import SwiftUI

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
                Picker("Hour", selection: $store.selectedHour) {
                    ForEach(0..<24, id: \.self) { h in
                        Text(String(format: "%02d", h)).tag(h)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)

                Text(":")
                    .font(.system(size: 40, weight: .light))
                    .foregroundStyle(.secondary)

                Picker("Minute", selection: $store.selectedMinute) {
                    ForEach(minuteOptions, id: \.self) { m in
                        Text(String(format: "%02d", m)).tag(m)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)
            }
            .frame(height: 200)

            Text(windowLabel)
                .font(.headline)
                .foregroundStyle(.secondary)

            Spacer(minLength: 0)

            Button {
                Task { await store.startAlarm() }
            } label: {
                Text("Start")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.horizontal)
            .padding(.bottom, 12)
        }
        .padding()
        .onAppear { store.resetSelectedToCurrentWindow() }
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
                Capsule()
                    .fill(Color.secondary.opacity(0.15))

                Capsule()
                    .fill(Color.accentColor)
                    .frame(width: max(2, geo.size.width * Self.normalize(currentDB)))
                    .animation(.linear(duration: 0.05), value: currentDB)

                // baseline marker
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
