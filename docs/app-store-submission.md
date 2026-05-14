# App Store submission cheat sheet

Reference for paste-ready text when filling out App Store Connect for aka Alarm 1.0.

---

## Listing metadata (App Store Connect → App Information / Version)

**Name** (≤30 chars)
```
aka Alarm
```

**Subtitle** (≤30 chars)
```
A gentler way to wake up
```

**Promotional text** (≤170 chars, editable without resubmission)
```
Pick a 30-minute wake window. aka Alarm listens for the moment you start stirring and wakes you with a gentle fade-in — instead of jolting you out of bed at a fixed time.
```

**Description** (≤4000 chars)
```
aka Alarm wakes you at the right moment in your chosen window — not at an arbitrary fixed time.

Pick a wake-up window (any time, plus 30 minutes). Tap Start. aka Alarm listens to the ambient sound of your room and builds a rolling baseline of how quiet it really is. When the window opens, the first time you turn over, sigh, or shift the duvet, the alarm fires. If nothing happens, it fires at the end of the window so you never oversleep.

• Gentle wake-up — the alarm tone fades from 1 % to 100 % over a minute, so you come out of sleep gradually instead of being jolted.
• Smart snooze — lightly move the phone to snooze. The snooze duration is randomised between 60 seconds and 15 minutes, clamped to whatever's left of your window.
• On-device only — audio never leaves your phone. Nothing is recorded, stored, or transmitted. No accounts, no servers, no analytics.
• Native iOS 26 design — Liquid Glass throughout, no custom skinning.

How it works in one paragraph: the app continuously samples microphone input to compute the ambient sound level (in dBFS) and maintains a five-minute rolling baseline. Within the wake window, a level rise above the baseline by a configurable threshold triggers the alarm. The microphone is active only while an alarm is set. Audio samples are processed in memory and immediately discarded.

aka Alarm is open source under the MIT licence.
```

**Keywords** (≤100 chars, comma-separated, no spaces around commas)
```
alarm,wake,sleep,gentle,smart,sunrise,microphone,bedtime,snooze,sound,window
```

**Support URL**
```
https://akaseeingred.github.io/aka-alarm/
```

**Privacy policy URL**
```
https://akaseeingred.github.io/aka-alarm/privacy.html
```

**Marketing URL** (optional, same as support)
```
https://akaseeingred.github.io/aka-alarm/
```

> Replace `akaseeingred` with your actual GitHub username if different.

---

## Privacy nutrition labels (App Store Connect → App Privacy)

Answer "We do not collect any data from this app". This is true — the app does not transmit anything off-device.

The microphone *is* used but the data is not collected per Apple's definition (which means linked to a user identifier or transmitted off-device).

---

## Age rating

Answer "None" for every question. Result: **4+**.

---

## Export compliance

Already handled — `ITSAppUsesNonExemptEncryption: false` is in Info.plist via project.yml. Apple won't ask the encryption question for this build.

---

## App Review information → Notes

```
aka Alarm is a smart alarm clock. The user picks a 30-minute wake window, and the app fires the alarm at the moment within that window when the user starts stirring (detected via microphone sound-level rise above a learned room baseline), or at the end of the window if no movement is detected.

The `audio` background mode (UIBackgroundModes) and microphone permission (NSMicrophoneUsageDescription) are required because the microphone must remain active throughout the night while the alarm is armed — otherwise the spike-detection feature cannot work and the user would be woken at an arbitrary time like any other alarm app.

Microphone samples are processed in memory (real-time RMS → dBFS conversion) and immediately discarded. No audio buffer is written to disk, persisted in memory, or transmitted off-device. There are no network requests of any kind.

To test:
1. Open the app, leave the default time, tap Start. The level meter responds to ambient sound.
2. Within ~10 seconds the app has enough baseline samples to arm spike detection. Yell or clap and the alarm fires (loud audio + vibration buzz).
3. To snooze: gently tilt or pick up the phone. The alarm pauses and shows a random countdown (60 s to 15 min).
4. Slide up anywhere on screen to dismiss back to the set-alarm screen.

No login, account, or external service is needed.
```

---

## Demo account / sign-in

```
Not applicable. The app has no login.
```

---

## Build upload

- Configuration: Release.
- Archive: Xcode → Product → Archive.
- Distribute: Organizer → Distribute App → App Store Connect → Upload.
- After upload, give it ~10–20 min for Apple's processing, then it appears under Builds in App Store Connect.
- Pick the build for the 1.0 release version. Submit.

---

## Screenshots (required)

App Store Connect needs screenshots for at least one device size. Easiest path:

1. Boot the **iPhone 17 Pro Max** simulator (6.9").
2. Run aka Alarm. ⌘S saves a screenshot to Desktop.
3. Capture: set screen (with picker), monitoring screen, alarm-firing screen, snoozing screen.
4. Upload all four in App Store Connect → App Store → 1.0 → 6.9" Display.

Screenshots also recommended for 6.5" (iPhone 11 Pro Max / 14 Plus). If you don't want to take those, App Store Connect will use the 6.9" set for all sizes — slightly less polished but accepted.
