import SwiftUI

struct AlarmView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var dragOffset: CGFloat = 0
    @State private var dimOpacity: Double = 0

    var body: some View {
        ZStack {
            VStack(spacing: 24) {
                Spacer(minLength: 0)

                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(context.date, format: .dateTime.hour().minute().second())
                        .font(.system(size: 72, weight: .thin, design: .rounded))
                        .monospacedDigit()
                }

                content

                Spacer(minLength: 0)

                SlideUpHint(label: "Slide up to dismiss")
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(.systemBackground))
            .offset(y: dragOffset)

            // Dim overlay only while snoozing — the alarming phase should be bright.
            // Dim resets to 0 (instantly via onChange) whenever we leave the snoozing
            // phase, so a fresh re-snooze starts the fade from zero again.
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
        .onChange(of: store.phase.kind, initial: true) { _, kind in
            updateDim(for: kind)
        }
    }

    private func updateDim(for kind: AlarmPhase.Kind) {
        switch kind {
        case .snoozing:
            dimOpacity = 0
            withAnimation(.linear(duration: Tuning.dimFadeDuration)) {
                dimOpacity = Tuning.dimEndOpacity
            }
        default:
            withAnimation(.easeOut(duration: 0.25)) { dimOpacity = 0 }
        }
    }

    private func resetDim() {
        guard store.phase.kind == .snoozing else { return }
        withAnimation(.easeOut(duration: 0.25)) { dimOpacity = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            guard store.phase.kind == .snoozing else { return }
            withAnimation(.linear(duration: Tuning.dimFadeDuration)) {
                dimOpacity = Tuning.dimEndOpacity
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch store.phase {
        case .alarming:
            Text("Move the phone to snooze")
                .font(.callout)
                .foregroundStyle(.secondary)
        case .snoozing(let until, _):
            VStack(spacing: 8) {
                Text("Snoozing")
                    .font(.title2)
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let remaining = max(0, Int(until.timeIntervalSince(context.date)))
                    Text(format(remaining))
                        .font(.system(.title3, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
        default:
            EmptyView()
        }
    }

    private func format(_ seconds: Int) -> String {
        String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}

#if DEBUG
#Preview("Alarming") {
    AlarmView()
        .environmentObject(AlarmStore.preview(phase: .alarming(
            windowEnd: .now.addingTimeInterval(15 * 60)
        )))
        .preferredColorScheme(.dark)
}

#Preview("Snoozing") {
    AlarmView()
        .environmentObject(AlarmStore.preview(phase: .snoozing(
            until: .now.addingTimeInterval(3 * 60),
            windowEnd: .now.addingTimeInterval(15 * 60)
        )))
        .preferredColorScheme(.dark)
}
#endif
