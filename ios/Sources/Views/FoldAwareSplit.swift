import SwiftUI

/// Keeps a screen's content clear of iPhone Duo's fold.
///
/// With no fold — any other iPhone, or a Duo that is closed — `flat` is laid
/// out exactly as given, so nothing changes for anyone else. When the device
/// is partially open, the canvas is split along the fold's reserved region:
/// `primary` takes the segment farther from the user (the top in the
/// tabletop/tent pose, the leading side in the book pose) and `secondary` the
/// nearer, interactive one. That is the displacement pattern Apple
/// recommends: move only what must move, keep everything reachable.
///
/// Fully open and wider than tall, the hinge still runs through the middle
/// of the canvas (vertically, in that orientation), so the same arrangement
/// is kept around the now inactive fold: clock on the leading half, controls
/// on the trailing half. Fully open in portrait stays `flat`.
///
/// The reserved-region API arrived with the iOS 27.1 SDK, whose SwiftUICore
/// module is version 8.0.85 (the 27.0 SDK ships 8.0.84). Gating on the module
/// version keeps the project building with Xcode 27.0 — both toolchains are
/// Swift 6.4, so a compiler check can't tell them apart — and falls back to
/// `flat` there. Drop the gate once Xcode 27.1 is the everyday toolchain.
struct FoldAwareSplit<Primary: View, Secondary: View, Flat: View>: View {
    @ViewBuilder let primary: () -> Primary
    @ViewBuilder let secondary: () -> Secondary
    @ViewBuilder let flat: () -> Flat

    var body: some View {
        #if canImport(SwiftUICore, _version: 8.0.85)
        if #available(iOS 27.1, *) {
            GeometryReader { proxy in
                if let fold = arrangingFold(in: proxy) {
                    split(around: fold, in: proxy.size)
                } else {
                    flat()
                        .frame(width: proxy.size.width, height: proxy.size.height)
                }
            }
        } else {
            flat()
        }
        #else
        flat()
        #endif
    }

    #if canImport(SwiftUICore, _version: 8.0.85)
    @available(iOS 27.1, *)
    private func arrangingFold(in proxy: GeometryProxy) -> ReservedRegion? {
        let folds = proxy.reservedRegions(kind: .division, options: .includeInactive)
        if let active = folds.first(where: \.isActive) { return active }
        guard proxy.size.width > proxy.size.height else { return nil }
        return folds.first
    }

    @available(iOS 27.1, *)
    @ViewBuilder
    private func split(around fold: ReservedRegion, in size: CGSize) -> some View {
        // Grow the fold's frame by its margins: content should stay off the
        // curve, not merely off the hinge line.
        let top = fold.frame.minY - fold.margins.top
        let bottom = fold.frame.maxY + fold.margins.bottom
        let leading = fold.frame.minX - fold.margins.leading
        let trailing = fold.frame.maxX + fold.margins.trailing

        if fold.frame.width >= fold.frame.height {
            // Fold runs across the canvas: segments above and below it.
            VStack(spacing: 0) {
                primary()
                    .frame(width: size.width, height: max(0, top))
                Color.clear
                    .frame(height: max(0, bottom - top))
                secondary()
                    .frame(width: size.width, height: max(0, size.height - bottom))
            }
        } else {
            // Fold runs down the canvas: segments beside it.
            HStack(spacing: 0) {
                primary()
                    .frame(width: max(0, leading), height: size.height)
                Color.clear
                    .frame(width: max(0, trailing - leading))
                secondary()
                    .frame(width: max(0, size.width - trailing), height: size.height)
            }
        }
    }
    #endif
}
