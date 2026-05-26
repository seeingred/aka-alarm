# Changelog

## 1.1.0 — unreleased

Background-listening reliability + screen behaviour. iOS only (Android already
benefited from these patterns via its ForegroundService design).

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
