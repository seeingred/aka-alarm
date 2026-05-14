# aka Alarm

An iOS alarm app that wakes you within a 30-minute window by listening for the
moment you start stirring, instead of jolting you out of bed at an arbitrary
moment.

> Status: iOS scaffold only. Android to follow.

## How it works

1. Pick a wake-up time. The wake window runs from that time to 30 minutes later.
2. Once you tap **Start**, the app listens to ambient sound and builds a rolling
   baseline of how quiet your room actually is.
3. When the wake window opens, the app keeps listening. The first time the
   ambient level rises meaningfully above baseline (you turn over, sigh, shift
   the duvet), the alarm fires.
4. If nothing happens, the alarm fires at the end of the window so you don't
   oversleep.
5. The alarm tone fades in from 1 % to 100 % over a minute so you wake up
   gradually rather than panicked.
6. Snooze by **lightly moving the phone** — no fumbling for a button. Each
   snooze picks a random duration between 60 s and 15 min (clamped so it
   never extends past the window). When the remaining window is too short
   to snooze, the alarm stops snoozing.
7. **Slide up** anywhere on the screen to dismiss.

## Requirements

- macOS with Xcode 26 or newer.
- An iOS 26+ device (or simulator runtime — see below).
- [xcodegen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`

## Build & run

```sh
cd ios
cp Local.xcconfig.example Local.xcconfig    # first time only
# edit Local.xcconfig and set DEVELOPMENT_TEAM = <your team id>
xcodegen generate                            # produces ios/AkaAlarm.xcodeproj from project.yml
open AkaAlarm.xcodeproj                       # then ⌘R on a physical device
```

> The Xcode project and `ios/Resources/Info.plist` are *generated* from
> `ios/project.yml` — they are gitignored. Re-run `xcodegen generate`
> whenever you change `project.yml`.

### Setting your development team once

`ios/Local.xcconfig` is a per-developer file that holds your `DEVELOPMENT_TEAM`.
It's gitignored, so your team ID never lands in the repo, but `project.yml`
references it so the team survives every regeneration of the Xcode project.
You only need to fill it in once. Find your team ID at
**Xcode → Settings → Accounts → (your Apple ID) → Team**.

### Running on a device

Microphone monitoring **does not work on the simulator** in any meaningful
way — the simulator's mic is a virtual passthrough of your Mac's mic, and
background audio behaves differently. Use a real device.

The bundle ID is `com.aka.alarm` (change it in `project.yml` if you want).

### Running on the simulator

If `xcodebuild` complains that *"iOS 26.5 is not installed"*, open Xcode →
Settings → Components and download the iOS 26 platform support / simulator
runtime, then retry.

## Project layout

```
ios/
  project.yml                 # xcodegen spec (source of truth)
  Sources/
    AkaAlarmApp.swift         # @main and root view switch
    Tuning.swift              # all tunable constants
    Models/AlarmStore.swift   # alarm state machine
    Audio/MicMonitor.swift    # AVAudioEngine + rolling baseline + spike detection
    Audio/AlarmPlayer.swift   # procedural beep tone with volume fade-up
    Motion/MotionMonitor.swift# CoreMotion-based snooze nudge detection
    Views/MainView.swift      # set-alarm + monitoring screens
    Views/AlarmView.swift     # alarm + snoozing screen
  Resources/
    Assets.xcassets           # accent color + app icon placeholder
```

## Tuning

Behaviour knobs (spike threshold, snooze sensitivity, baseline window length,
fade duration, etc.) live in [Tuning.swift](ios/Sources/Tuning.swift). Edit
and rebuild.

## iOS background caveat

The app uses the `audio` background mode to keep the microphone live while
backgrounded, but iOS may still suspend the app in low-power scenarios.
For the most reliable experience, keep the phone plugged in overnight with
the app open on-screen (auto-lock will dim the display but won't kill the
process).

The alarm tone plays through the app's own audio engine, not via local
notifications — that means it only rings while the process is running.
This is a deliberate v1 trade-off (no Apple "Critical Alerts" entitlement
yet).

## License

MIT. See [LICENSE](LICENSE).
