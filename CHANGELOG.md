# Changelog

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
