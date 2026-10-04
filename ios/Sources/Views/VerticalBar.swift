import SwiftUI
import UIKit

/// Where the system draws its vertical bar, if anywhere.
///
/// iPhone Duo lays the status bar, toolbars and navigation controls out in a
/// column along one edge of the outer display (and of the inner display in
/// landscape). Standard toolbar items are placed in that column for free, so
/// the screens use it for the gear and Start instead of their own overlay and
/// button. Every other canvas reports `.none` and keeps the regular layout.
enum VerticalBarEdge: Equatable {
    case none
    case leading
    case trailing
}

private struct VerticalBarEdgeKey: EnvironmentKey {
    static let defaultValue = VerticalBarEdge.none
}

extension EnvironmentValues {
    var verticalBarEdge: VerticalBarEdge {
        get { self[VerticalBarEdgeKey.self] }
        set { self[VerticalBarEdgeKey.self] = newValue }
    }
}

extension View {
    /// Publishes the system's vertical-bar edge to `verticalBarEdge` in the
    /// environment of this view, tracking pose and rotation changes.
    func readingVerticalBarEdge() -> some View {
        modifier(VerticalBarEdgeReader())
    }
}

private struct VerticalBarEdgeReader: ViewModifier {
    @State private var edge = VerticalBarEdge.none

    func body(content: Content) -> some View {
        content
            .environment(\.verticalBarEdge, edge)
            .background {
                VerticalBarEdgeProbe { edge = $0 }
                    .frame(width: 0, height: 0)
                    .allowsHitTesting(false)
            }
    }
}

// UIKit exposes the edge as a trait (`UITraitCollection.verticalBarEdge`,
// iOS 27.1); SwiftUI has no environment value for it, so a zero-size UIKit
// view reads the trait and re-reads it whenever the traits that feed it
// change. Same SDK gate as FoldAwareSplit: the trait doesn't exist in the
// 27.0 SDK, and both toolchains are Swift 6.4.
#if canImport(SwiftUICore, _version: 8.0.85)
private struct VerticalBarEdgeProbe: UIViewRepresentable {
    let onChange: (VerticalBarEdge) -> Void

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.onChange = onChange
        return view
    }

    func updateUIView(_ uiView: ProbeView, context: Context) {
        uiView.onChange = onChange
    }

    final class ProbeView: UIView {
        var onChange: ((VerticalBarEdge) -> Void)?
        private var lastReported: VerticalBarEdge?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            if #available(iOS 27.1, *) {
                registerForTraitChanges(UITraitCollection.systemTraitsAffectingVerticalBarEdge) {
                    (view: ProbeView, _: UITraitCollection) in
                    view.report()
                }
            }
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            report()
        }

        private func report() {
            guard window != nil else { return }
            var edge = VerticalBarEdge.none
            if #available(iOS 27.1, *) {
                switch traitCollection.verticalBarEdge {
                case .leading: edge = .leading
                case .trailing: edge = .trailing
                default: edge = .none
                }
            }
            guard edge != lastReported else { return }
            lastReported = edge
            // Hop off the trait-change callback before touching SwiftUI state.
            DispatchQueue.main.async { [weak self] in self?.onChange?(edge) }
        }
    }
}
#else
private struct VerticalBarEdgeProbe: View {
    let onChange: (VerticalBarEdge) -> Void
    var body: some View { Color.clear }
}
#endif
