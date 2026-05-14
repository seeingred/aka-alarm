import SwiftUI

@main
struct AkaAlarmApp: App {
    @StateObject private var store = AlarmStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var store: AlarmStore

    var body: some View {
        ZStack {
            switch store.phase.kind {
            case .alarming, .snoozing:
                AlarmView()
                    .transition(.move(edge: .bottom))
            default:
                MainView()
            }
        }
        .animation(.easeInOut(duration: 0.25), value: store.phase.kind)
    }
}
