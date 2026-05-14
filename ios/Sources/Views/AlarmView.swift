import SwiftUI

struct AlarmView: View {
    @EnvironmentObject private var store: AlarmStore
    @State private var dragOffset: CGFloat = 0

    var body: some View {
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
