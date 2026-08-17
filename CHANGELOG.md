# Changelog

## Unreleased

**Configurable mic activation window (iOS + Android)** — built on
[PR #3](https://github.com/seeingred/aka-alarm/pull/3) by @nullptroma, who
contributed the deferred-mic architecture (Armed phase + Doze-proof exact
alarm + unit tests).

- New Armed phase: after tapping Start the mic stays **off** — the app just
  holds its foreground notification — until the configurable activation lead
  before the wake window. Saves battery and keeps the mic dark overnight.
- New "Start listening" setting in the gear sheet: Right away → 8 h → 4 h →
  2 h → 1 h (default) → 30 min → 15 min → 5 min before the window. Persisted,
  and applies live: changing it while armed re-computes the phase on the spot
  (mic starts/stops immediately and the scheduled wakeup is re-armed).
- Android wakes from the Armed phase via `AlarmManager.setExactAndAllowWhileIdle`
  (`USE_EXACT_ALARM` on 13+, `SCHEDULE_EXACT_ALARM` on 12), with stale-alarm
  validation when re-arming. On top of the PR:
  - Guarded against `SecurityException` when the exact-alarm permission is
    revoked (possible on Android 12) — falls back to inexact allow-while-idle
    delivery.
  - Added an in-process fallback timer and an on-foreground catch-up check so
    a single dropped OEM alarm can't leave the app armed forever with no
    wake-up — the alarm always fires by the end of the window.
- iOS mirrors the same phase machine with in-process timers plus a
  foreground catch-up (iOS keeps the screen on while armed, so timers tick).
- Notification and status line show the plan: "Alarm armed — mic off until
  HH:MM".

**iOS mic reliability (found during on-device testing of the above)**

- **Bluetooth is now output-only.** The audio session used `.allowBluetoothHFP`,
  so connected AirPods became the session *input* — and their low-bandwidth
  HFP mic delivers no usable buffers in `.measurement` mode, leaving the app
  completely deaf until the AirPods happened to switch to another device.
  Session options now use `.allowBluetoothA2DP` (alarm audio can still play
  through AirPods when worn) and the input is pinned to the built-in mic via
  `setPreferredInput` — the phone on the nightstand is the sensor, always.
  Also removes the ~3 s HFP-negotiation freeze when tapping Start.
- The mic watchdog now retries the engine rebuild on every tick while stuck
  (previously one attempt on the edge into stuckness — if that single rebuild
  landed while the route was still settling, the mic stayed dead), with a
  liveness grace period after each attempt so the retry loop can't tear the
  engine down faster than it can deliver its first buffer.
- Fixed the settings gear drifting toward the screen centre on the armed
  screen (the hidden level bar let the container shrink to the status text's
  width; it's now pinned full-width).

## 1.1.4 — 2026-08-08

Feature release — adjustable microphone sensitivity (iOS + Android).

**Store "What's new" copy (paste-ready):**

> aka Alarm 1.1.4 — tune how sensitive the wake-up listener is.
>
> • New sensitivity setting: tap the gear in the top-right corner and drag the slider from Very low to Very high. Saves automatically and applies instantly, even while an alarm is armed.
> • The mic level bar now shows a red trigger line, so you can see exactly how loud a sound must be to wake you — and calibrate it live from the settings sheet.
> • Fixed time labels wrapping and overlapping on devices with large font sizes.

**Adjustable sensitivity (iOS + Android)**

- New settings gear in the top-right corner opens a sensitivity sheet with a
  15-step slider from Very low to Very high. It maps to the spike threshold:
  Very low = peak must exceed baseline by 8 dB, Very high = 1 dB, default
  midpoint = the historical 4.5 dB (0.5 dB per step). Motivated by real-world
  hardware variance — e.g. a Nothing Phone on a bedside cabinet whose mic
  never registered stirring at the fixed 4.5 dB threshold.
- The value saves automatically on every change (SharedPreferences /
  UserDefaults), persists across sessions, and applies live to a running
  monitor — no restart needed.
- The mic level bar now also shows a red trigger marker at
  baseline + threshold (hidden until the baseline rises above the display
  floor), and the sensitivity sheet embeds the same live bar while
  monitoring so the trigger point can be calibrated against real room noise.

**Fixes**

- Android: time labels (wake-window range on the set-alarm screen, clock on
  the monitoring screen) no longer wrap and overlap under large system font
  scales — new `AutoShrinkText` shrinks the text to fit one line, mirroring
  the `minimumScaleFactor` treatment the iOS side already had.
- Android: the sensitivity slider uses 15 discrete steps rather than a
  continuous track; a continuous Material 3 `Slider` pixel-snaps its value and
  fires a spurious `onValueChange` on first composition, silently overwriting
  the stored default.

## 1.1.3 — 2026-06-26

Android-only patch — no user-visible changes.

- Lowered `minSdk` from 34 (Android 14) to 31 (Android 12) in `android/app/build.gradle.kts`. Roughly doubles the addressable Android device base. All APIs used by the app are available on API 31; `POST_NOTIFICATIONS` was already runtime-gated, and `FOREGROUND_SERVICE_MICROPHONE` is harmless on devices below API 34 (annotated with `tools:targetApi="34"` in the manifest to silence the lint warning). Notably restores installability on Huawei AppGallery, whose test fleet includes Android 12 EMUI 13 devices.
- Updated privacy policy (`docs/privacy.html`) to explicitly name the developer (Aleksandr Alekseev) alongside the app name, per Huawei AppGallery review rule 7.1.

## 1.1.2 — 2026-05-27

Android-only patch — no user-visible changes.

- Added `dependenciesInfo { includeInApk = false; includeInBundle = false }` to `app/build.gradle.kts`. AGP injects an encrypted "Dependency metadata" signing block by default that only Google can decrypt; F-Droid's APK scanner rejects builds containing it as opaque. Disabling keeps the build fully transparent and unblocks F-Droid inclusion.

## 1.1.1 — 2026-05-27

Android-only patch — no user-visible changes.

- Removed the `org.gradle.toolchains.foojay-resolver-convention` plugin from `android/settings.gradle.kts`. F-Droid's build scanner flags it as a non-free network dependency (it downloads JDKs from foojay.io). The project pins `JavaVersion.VERSION_17` and doesn't need toolchain auto-resolution; both local dev and F-Droid CI provide JDK 17 directly.
- Added Fastlane metadata structure at `fastlane/metadata/android/en-US/` (title, short + full description, 512 icon) so F-Droid clients, IzzyOnDroid, and other Fastlane-aware indexers pull the listing copy from the canonical source instead of duplicating it in store metadata.
- Wired up `.github/workflows/release.yml` so a `vX.Y.Z` tag push automatically builds the signed APK + AAB and creates a GitHub Release. Obtainium users can now auto-update directly from the Releases page.

## 1.1.0 — 2026-05-27

Background-listening reliability + screen behaviour + spike-detection sensitivity.
iOS-only resilience fixes; sensitivity tuning applies to both iOS and Android.

**App Store "What's new" copy (paste-ready):**

> aka Alarm 1.1 — wakes you up more reliably.
>
> • Fixes the bug where connecting AirPods (or any Bluetooth headset) silently killed the microphone listener.
> • Handles phone calls / Siri / other audio apps interrupting the session, with a notification telling you the alarm needs to be resumed.
> • Keeps the screen on while an alarm is armed, with a gentle dim-down so the room stays dark — tap anywhere to wake the screen.

**Implementation highlights**

- `MicMonitor` now observes `AVAudioSession.routeChangeNotification` and rebuilds the engine + tap with the new input format whenever the route swaps. Fixes the AirPods-kills-mic regression reported in production.
- `MicMonitor` also observes `AVAudioSession.interruptionNotification`. On `.began` (phone call, Siri, exclusive audio app), AlarmStore posts a local "aka Alarm was paused" notification. On `.ended .shouldResume`, the engine reactivates the session and rebuilds the tap automatically.
- A silent `AVAudioPlayerNode` looping a zero buffer is attached to the engine's main mixer alongside the input tap. Keeps the audio session counted as "actively producing audio" during the brief gap when recording is reinitialising after a route change — the trick Sleep Cycle uses for overnight reliability.
- `UIApplication.shared.isIdleTimerDisabled` is toggled on whenever a non-idle phase is active, so the screen never auto-locks mid-alarm.
- Monitoring and snoozing screens fade a black overlay from 0 → 0.85 opacity over 30 s. Tap anywhere to instantly reset the fade. Alarming phase stays at full brightness.
- App lifecycle observer (`didBecomeActiveNotification`): if the OS suspended us and we just returned to foreground with an armed alarm, the mic engine is restarted automatically.

**Spike detection (iOS + Android, identical algorithm)**

- Detection now compares the *peak* dB moment within each 1-second cell against the rolling baseline, instead of comparing the cell mean. Short stirring sounds (200-500 ms sheet rustle, brief sigh, duvet shift) that previously got averaged into invisibility now trigger reliably. Baseline still uses the cell mean so single loud events can't pull the threshold up.
- Spike threshold lowered from 6.0 dB to 4.5 dB above baseline. Combined with peak detection this is materially more sensitive without becoming a false-positive disaster.
- New `displayDbFloor = -60` (vs detection floor at -80) means the live mic level bar fills more aggressively at night — subtle ambient and movement actually show up visually instead of barely moving from the left edge.

**Mic-resilience round 2 (iOS)**

- **Watchdog timer** in MicMonitor checks every second whether buffers are still arriving. If 3 s pass with no buffer while we think we're running, we declare the engine dead, fire the same "audio paused" local notification, and rebuild. Buffers resuming clears the notification. Catches the YouTube / Spotify / browser-video hijack case where iOS routes audio to the other app silently without sending a formal `interruptionNotification`. Previously the engine *thought* it was running but the tap had dried up.
- **Debounced route-change handler.** Rapid AirPods on/off used to overlap multiple tear-down/rebuild cycles and wedge the engine. The route handler now coalesces clusters of changes into a single rebuild after 400 ms of quiet.

**Persistence (iOS + Android)**

- Last-confirmed alarm time (`selectedHour` + `selectedMinute`) is saved to UserDefaults / SharedPreferences when you tap Start, and restored on next launch. The set-alarm screen no longer auto-snaps to "now" on appearance — your previous selection sticks.

## 1.0.0 — 2026-05-14

First public release. iOS via App Store, Android via Google Play.

**Store "What's new" copy (≤4000 chars, paste-ready for both stores):**

> aka Alarm 1.0 — wake up gently.
>
> Pick a 30-minute window. The app listens for the moment you start stirring and wakes you with a soft fade-in, instead of jolting you out of bed at a fixed time. If nothing happens, it fires at the end of the window so you never oversleep.
>
> Snooze by lightly moving the phone. Slide up to dismiss. On-device only — nothing leaves your phone.

**Highlights (shared)**

- Wake-up window with smart spike detection on the microphone.
- 60-second volume fade-in for the alarm tone.
- Motion-driven snooze (random 60 s–15 min, clamped to remaining window).
- 100 % on-device: no network, no analytics, no accounts.

**iOS-specific**

- Native iOS 26 Liquid Glass UI throughout — pickers, button, mic-level bar.
- App icon with dark and tinted variants for iOS 18+ home-screen modes.

**Android-specific**

- Native Kotlin + Jetpack Compose, Material 3 with dynamic colours.
- ForegroundService keeps the mic alive overnight with a persistent
  "aka Alarm is listening…" notification — much more reliable than the
  best-effort background-audio path on iOS.
- Min SDK 34 (Android 14).
