# iPhone Duo support plan

Research done 2026-10-04, before the device shipped. Next working session is
expected after launch; this page is the hand-off to that session.

## The device, in numbers

| | Outer display (closed) | Inner display (open) |
|---|---|---|
| Diagonal | 5.4" | 7.6" |
| Panel pixels | 1398 × 2034 @ 460 ppi | 1878 × 2670 @ 430 ppi |
| Logical size | **466 × 678 pt** (3x, native) | **669 × 951 pt** (3x, rendered 2007 × 2853 and downsampled) |
| Aspect | 1.455 : 1 | 1.42 : 1 |
| Size classes, portrait | compact width × regular height | regular × regular (iPad-class) |
| Size classes, landscape | compact × compact | regular × regular |
| Honours `UISupportedInterfaceOrientations` | yes | **no** — a portrait-only app is still laid out in landscape |

Both displays: ProMotion, Always-On, a Dynamic Island, 1 nit minimum
brightness. Both front cameras can face away from the user depending on the
pose. Sensors include gyroscope and accelerometer, so the snooze nudge works.
Ships with iOS 27.1 on 2026-10-23 (pre-orders 2026-10-16); announced
2026-09-09. Dimensions: 164.6 × 117.8 × 5.2 mm open, 84.1 × 117.8 × 11.3 mm
closed, 254 g.

Poses Apple names: closed, open (flat), partially folded "book", and
"tabletop" / propped on its edges (the nightstand pose for an alarm app).
When partially open the fold becomes an active *reserved region* that
divides the inner display.

## Tooling: which Xcode

- **Xcode 27.1 is the Duo release.** It carries the iOS 27.1 SDK, the Duo
  simulator and the new APIs. It has been in beta since 2026-09-18 (beta 2
  as of 2026-10-04) and needs macOS 26.6 or later. Xcode 27.2 beta is the
  regular next-cycle beta and does *not* include Duo support; Xcode 27.0
  (what built 1.2.0) has no Duo SDK either.
- **Today a beta is required.** After 2026-10-23 the final 27.1 should be on
  the App Store and the beta is unnecessary. A beta installs side by side as
  `/Applications/Xcode-beta.app`; point builds at it without changing
  `xcode-select`:

  ```sh
  export DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer
  cd ios && xcodegen generate
  xcrun simctl list devices | grep -i duo          # find the simulator's name/UDID
  xcodebuild -project AkaAlarm.xcodeproj -scheme AkaAlarm -configuration Debug \
    -destination 'platform=iOS Simulator,name=iPhone Duo' \
    -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO build
  ```

- Simulator notes from the 27.1 release notes: the first launch can take
  several minutes; **StandBy is unavailable** in the Duo simulator runtime;
  most app extensions can't run there. Device Hub (Xcode's device window)
  opens, closes, rotates and folds the simulated device, and has a resize
  mode; the Previews canvas has a "Display" override group for the
  alternative display.
- Deployment target stays iOS 26. Nothing here requires iOS 27 for other
  phones; the 27.1 SDK is a build-time requirement.

## What happens to the app we ship today

Apple's compatibility tiers, from the "Prepare your app for iPhone Duo"
Tech Talk:

| Built with | Outer display | Inner display |
|---|---|---|
| Xcode 26 or earlier | content stops left of the status bar / camera strip | "familiar size and aspect ratio" |
| Xcode 27.0 (our 1.2.0 / next release) | same | extends left of the status bar area |
| Xcode 27.1 SDK | edge to edge, bars laid out vertically beside the status bar | edge to edge |

So the current build runs, but doesn't fill the screen. Full support is a
rebuild with Xcode 27.1 **plus** the layout work below.

## Guidelines digest (the parts that apply to us)

- **Size classes, not devices.** Compact-width layout for the outer display,
  regular-width for the inner one "give you the fundamentals for every
  pose". Size views relative to their container, never to screen dimensions
  or `UIScreen.main`; on a two-display device use the scene's screen and
  `traitCollection.displayScale`.
- **Same functionality in every pose.** Don't tie features to a pose or
  display; show an extra level of hierarchy on the inner display only if it
  makes sense. Prefer small displacements over rearrangement when the device
  folds.
- **Reserved regions** (`reservedRegions(kind:options:)` in SwiftUI via
  `GeometryProxy`, `view.reservedRegions(kind:)` in UIKit): the outer camera
  (`.occlusion`, always active, expands into the Dynamic Island for Live
  Activities), the inner camera (`.occlusion`, only while the camera is
  active) and the fold (`.division`, active while partially open). System
  components move out of their way automatically; custom layouts must.
  Scrolling content may run through the fold; interactive elements must not.
- **Arrangement views** (`ArrangementView` / `UIArrangementViewController`):
  a primary/secondary container. `.split` goes side by side when wider than
  tall, stacked when taller, and respects the fold; `.overlay` layers them
  flat and separates them to either side of the fold when partially open.
  Keep navigation containers *outside* arrangement views; never nest one in
  a scroll view or list.
- **Vertical bars.** Toolbars, tab bars and navigation controls move to the
  side on the outer display and on the inner display in landscape (inner
  portrait keeps horizontal bars). Only standard bars get this for free. Give
  toolbar items both a symbol and a title; text-only items stay horizontal.
  Sheets on the outer display get vertical bars unless disabled with
  `toolbarVerticalBehavior(_:)`.
- **Safe areas are asymmetric** on the outer display (the Dynamic Island,
  status bar and any bars sit along one edge). Full-width immersive content
  that doesn't scroll is fine as long as nothing interactive sits under them.
- **Hinge angle** (`onHingeChange` / `UIHingeInteraction`, statuses closed /
  partially open / fully open) is for live effects, not layout decisions.
- **Scene accessories** can show supplementary content on the other display
  at the same time; new windows exist only on the inner display.
- App Store: iPhone Duo screenshots are optional (outer 1398 × 2034, inner
  2007 × 2853); without them the 6.5" set is shown. Upload support "later
  this year".

## What it means for aka Alarm

Audit of the current iOS code against the above, worst first.

1. **Set-alarm screen overflows in short canvases.** `SetAlarmView` stacks a
   title, two `NumberWheel`s at a fixed 400 pt, a 72 pt window label and the
   Start button. That is ~650 pt of content, which doesn't fit inner
   landscape (669 pt tall, minus bars) or outer landscape (466 pt), and the
   inner display ignores our portrait lock. Do what Android got in October
   2026: two panes (wheels left, label + Start right) when the canvas is
   wider than tall, and shorter wheels when height is tight. Drive it by size
   class / container size, not orientation. On the inner display in portrait
   the wheels' glass pills stretch to half of 669 pt; cap their width or use
   the two-column layout there too.
2. **Settings sheet detent.** `SensitivitySheet` uses
   `.presentationDetents([.height(620), .large])`; 620 pt is taller than the
   outer display's usable height. Use `.medium` / `.large` or `.fraction`.
   Our sheet has no toolbar, so vertical bars don't apply, but check that the
   capsule chip and buttons clear the side safe area.
3. **Gear button placement.** `SettingsGearButton` is an overlay at
   `.topTrailing`. On the outer display the status bar, Dynamic Island and
   camera live along that edge. Either verify the safe-area inset keeps it
   clear in every pose, or move it into a real `.toolbar` so the system
   places it (and gets the vertical layout for free). The latter is the
   guideline-conformant option.
4. **Nightstand pose.** `MonitoringView` and `AlarmView` centre the clock in
   a `VStack`. Flat on a table that's fine; propped/tent is the pose people
   will actually use overnight, and the fold then runs through the middle.
   Put the clock in the upper half and status/hint/controls in the lower half
   — an `ArrangementView` with `.split.axes(.vertical)` (clock primary,
   status secondary), or a manual layout from `reservedRegions(kind:
   .division)`. The dim overlay is `.ignoresSafeArea()` full-screen and is
   fine.
5. **Snooze gesture in the tent pose.** A gyroscope nudge on a device
   standing on its edges may knock it flat. Decide on hardware whether that
   is acceptable; a secondary snooze affordance would be a product change,
   so it is a question for the owner, not a default.
6. **StandBy risk.** The Duo enters StandBy "when set down, even when not
   charging". Our overnight model keeps the app in the foreground with the
   idle timer disabled; StandBy normally requires the lock screen, so it
   should not trigger — but if it does, the mic stops and the alarm breaks.
   The simulator cannot test this. First thing to verify on real hardware,
   in the tent pose, overnight.
7. **Closing the device overnight.** The app should simply move to the outer
   display and keep running (it resizes; the scene stays). Verify, and check
   the Monitoring view at 466 × 678 pt with the side safe area.
8. **Screen references.** `grep -rn "UIScreen.main" ios/` — should be empty.
9. **Rebuild and release.** Build the Duo release with Xcode 27.1, bump the
   version (new feature → minor), regenerate the project, mention iPhone Duo
   in the store copy. Consider Duo screenshots once App Store Connect accepts
   them.

## Test matrix

Run the app in the Duo simulator in each of these and screenshot every
screen (set alarm, settings sheet, armed/monitoring, alarming, snoozing):

| Canvas | Points | How |
|---|---|---|
| Outer, portrait | 466 × 678 | closed |
| Outer, landscape | 678 × 466 | closed, rotated |
| Inner, portrait | 669 × 951 | open |
| Inner, landscape | 951 × 669 | open, rotated |
| Inner, book | 669 × 951 with fold | partially folded, portrait |
| Inner, tabletop | 951 × 669 with fold | partially folded, landscape |

Device Hub's resize mode covers the first four without the pose controls.
Then on hardware: StandBy in the tent pose, closing the device while armed,
AirPods + alarm, snooze nudge while tented.

## Sources

- Apple, HIG: [Designing for iPhone Duo](https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo) (new page 2026-09-09)
- Apple, Technology Overview: [Preparing your app for iPhone Duo](https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo)
- Apple: [Get ready for iPhone Duo](https://developer.apple.com/iphone-duo/) (links to all six Tech Talks)
- Apple Tech Talks: [Prepare your app for iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111461/), [Design for iPhone Duo](https://developer.apple.com/videos/play/tech-talks/111466/), [Strike a pose with adaptive layouts](https://developer.apple.com/videos/play/tech-talks/111463/), [Leverage multiple displays and scenes](https://developer.apple.com/videos/play/tech-talks/111464/)
- Apple: [Xcode 27.1 beta release notes](https://developer.apple.com/documentation/xcode-release-notes/xcode-27_1-release-notes)
- Apple: [iPhone Duo tech specs](https://www.apple.com/iphone-duo/specs/), [App Store screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications/)
- Apple news: [Get ready with the latest beta releases](https://developer.apple.com/news/?id=rfb1rooi)
- Third party (point sizes cross-checked against the screenshot spec): [iPhone Duo for Developers: The 1.42 Problem and the SDK Gap](https://blakecrosley.com/blog/iphone-duo-for-developers); [What to test before October 23](https://www.highcircl.com/en/blog/iphone-duo-app-development); [MacRumors on the Xcode 27.1 beta](https://www.macrumors.com/2026/09/18/apple-releases-xcode-27-1-beta-iphone-duo-support/); [Wikipedia: iPhone Duo](https://en.wikipedia.org/wiki/IPhone_Duo)
