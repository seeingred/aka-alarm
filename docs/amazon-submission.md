# Amazon Appstore submission cheat sheet

Mirrors `docs/play-submission.md` for the Amazon developer console.
Submission cadence: ~1–3 business days for first review, hours-to-1-day
for subsequent updates.

## Prerequisites

1. **Amazon Developer account** — done.
2. **Signed release APK** at
   `android/app/build/outputs/apk/release/app-release.apk`. Rebuild with
   `cd android && ./gradlew assembleRelease`. Amazon takes APK, not AAB.
3. Same keystore as Play (we sign locally; Amazon doesn't have its own
   app-signing equivalent of Play App Signing).

## Console flow

1. Sign in at <https://developer.amazon.com/apps-and-games>.
2. **Add a New App** → **Android** → Free → set the default language to
   English (US).

## App information

**App title** (≤50 chars)
```
aka Alarm
```

**Short description** (≤80 chars)
```
Wakes you within a 30-min window when you start stirring
```

**Long description** (≤4000 chars)
```
aka Alarm wakes you at the right moment within a 30-minute window you choose, instead of jolting you out of bed at a fixed time.

How it works: while an alarm is armed the app uses the microphone to measure the ambient sound level of your room. It maintains a rolling 5-minute baseline of "how quiet it really is" and watches for the level to rise above that baseline by a configurable threshold. When that happens — you turn over, sigh, shift the duvet — the alarm fires. If nothing happens, the alarm fires at the end of the window so you never oversleep.

Privacy: the microphone is active only while an alarm is set, and audio is processed in memory then immediately discarded. No audio buffer is ever written to disk, persisted, or transmitted off your device. There is no network code in this app at all — no analytics, no crash reporting, no third-party SDKs.

Features:
• Wake-up window with smart spike detection
• 60-second gradual volume fade-in for the alarm tone
• Motion-driven snooze (lightly move the phone) with random duration clamped to the remaining window
• Slide up anywhere to dismiss
• Foreground service keeps the alarm reliable overnight
• Material 3 UI, dark + light mode

aka Alarm is open source under the MIT licence: https://github.com/seeingred/aka-alarm
```

**Product features** (5 bullets, ≤100 chars each)
```
Wakes you the moment you start stirring in a 30-minute window
60-second gradual fade-in alarm tone, no jarring noise
Lightly move the phone to snooze; randomised duration
On-device only — no analytics, no network, no accounts
Native Material 3 UI with dark and light mode
```

**Keywords** (comma-separated)
```
alarm,wake,sleep,gentle,smart,morning,bedtime,window,snooze
```

**Category**
Primary: **Lifestyle** (Amazon doesn't have a "Time" category)
Secondary: **Productivity** (optional)

**Support contact**
- Email: `akaseeingred@gmail.com`
- URL: `https://seeingred.github.io/aka-alarm/`

## Availability and pricing

- **Devices**: Amazon Fire phones (deprecated, no-op), Fire tablets, **and Non-Amazon Devices** (this is the important toggle — enables distribution to any Android device with the Amazon Appstore installed). Tick all that apply.
- **Markets**: All available (or just US/UK if you want a smaller initial rollout).
- **Pricing**: Free.
- **Purchasing in app**: No.

## Content rating

Amazon uses their own questionnaire. Answer **No** to every category
(violence, sex, drugs, gambling, etc.). Result: **All Ages / Everyone**.

## Images and screenshots

| Asset | Size | File |
| --- | --- | --- |
| Small icon | 114×114 px PNG | downscale `play-icon-512.png` |
| Large icon | 512×512 px PNG | `play-icon-512.png` (existing) |
| Phone screenshots | 1024×600 or higher, 3–10 | use the same shots as Play |
| Tablet screenshots | optional, skip | — |
| Feature graphic | 1024×500 PNG | `play-feature-graphic.png` (existing) |

To make the 114×114 small icon:
```sh
rsvg-convert -w 114 -h 114 /Users/aka/work/aka-alarm/app-icon.svg \
  -o /Users/aka/work/aka-alarm/amazon-icon-114.png
```

## Binary file

- Upload **`android/app/build/outputs/apk/release/app-release.apk`**.
- Version: 1.1.0 (Amazon reads this from the APK manifest).
- Release notes: paste the 1.1.0 "What's New" copy from
  [CHANGELOG.md](../CHANGELOG.md).

## Permissions and compatibility

After upload, Amazon shows the auto-detected list of permissions:
- `android.permission.RECORD_AUDIO`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.VIBRATE`
- `android.permission.WAKE_LOCK`

Amazon may ask for a justification on `RECORD_AUDIO` and the
foreground-service-microphone type. Paste:

```
The microphone is required by the core wake-stirring detection feature. While an alarm is armed, the app maintains a rolling 5-minute baseline of room ambient sound level and triggers the alarm when the level rises above that baseline by a configurable threshold (i.e. when the user starts stirring). The microphone is only active between the user tapping Start and dismissing the alarm. Samples are processed in memory to compute a dBFS sound level and immediately discarded — no audio buffer is written to disk, persisted, or transmitted off-device. There are no network requests of any kind.
```

## Compatibility filter

Amazon has its own "compatibility" toggle for Fire OS / non-Amazon
Android. We don't link against any Amazon-specific SDKs and our min SDK
is 34 (Android 14), so the app will simply not appear on Fire devices
that ship with older Android. That's fine — we're targeting general
Android phones via the "Non-Amazon Devices" tick.

## Submit

When every section has a green check on the dashboard, click **Submit
App** at the top. Median first-app review: ~2–3 business days. Updates:
hours to 1 day.

## Updating later

Each new release:
1. Build a fresh signed APK (`cd android && ./gradlew assembleRelease`).
2. Amazon developer console → My Apps → aka Alarm → **Upload a New APK**.
3. Paste release notes, hit Submit.

Amazon doesn't auto-pull from GitHub Releases the way IzzyOnDroid does,
so this is one of the manual steps every release. Same situation as
Google Play.
