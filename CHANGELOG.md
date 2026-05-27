# Changelog

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
