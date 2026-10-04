import SwiftUI

@main
struct AkaAlarmApp: App {
    @StateObject private var store = AlarmStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .modifier(DebugCanvasOverride())
        }
    }
}

#if DEBUG
/// Debug-only. Launch with `-canvas 951x669` to render the whole app in a
/// canvas of that logical size, scaled to fit the simulator's screen. Lets us
/// check iPhone Duo's canvases (466×678 outer, 669×951 inner, and their
/// landscape twins) on any simulator without Device Hub. It exercises only
/// our layout — no fold, no vertical bars, no asymmetric safe area — so it
/// complements the Duo simulator rather than replacing it. Inert without the
/// argument, and compiled out of Release.
private struct DebugCanvasOverride: ViewModifier {
    private static let size: CGSize? = {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-canvas"), i + 1 < args.count else { return nil }
        let parts = args[i + 1].lowercased().split(separator: "x").compactMap { Double($0) }
        guard parts.count == 2, parts[0] > 0, parts[1] > 0 else { return nil }
        return CGSize(width: parts[0], height: parts[1])
    }()

    func body(content: Content) -> some View {
        if let size = Self.size {
            GeometryReader { geo in
                let scale = min(geo.size.width / size.width, geo.size.height / size.height, 1)
                content
                    .frame(width: size.width, height: size.height)
                    .clipped()
                    .scaleEffect(scale)
                    .frame(width: geo.size.width, height: geo.size.height)
            }
            .ignoresSafeArea()
            .background(Color.black.ignoresSafeArea())
        } else {
            content
        }
    }
}
#else
private struct DebugCanvasOverride: ViewModifier {
    func body(content: Content) -> some View { content }
}
#endif

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
