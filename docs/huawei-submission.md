# Huawei AppGallery submission cheat sheet

Mirrors `docs/play-submission.md` and `docs/amazon-submission.md` for
Huawei's AppGallery Connect (AGC) console. First-app review takes 1-3
business days typically.

## Prerequisites

1. **Verified Huawei Developer account** — done.
2. **Signed release APK** — Huawei takes APK, not AAB. Grab the latest
   from <https://github.com/seeingred/aka-alarm/releases> (v1.1.2 →
   `aka-alarm-v1.1.2.apk`) or rebuild with
   `cd android && ./gradlew assembleRelease`.
3. **No HMS Core integration needed.** We don't use Huawei Mobile
   Services and that's fine — the AppGallery review *may* recommend
   adding HMS analytics/push, but it's not required and we're not
   adding non-free services to please them.

## Console flow

1. Sign in at <https://developer.huawei.com/consumer/en/console>.
2. **AppGallery Connect** (top right tile) → **My apps** → **New**.
3. Pick:
   - **App**: App
   - **Device**: Mobile phone
   - **App category**: Tool (Huawei doesn't have a Time/Alarm category)
   - **Default language**: English
   - **App Bundle ID / Package name**: `com.aka.alarm`
4. Click **OK** → you're inside the app's AGC dashboard.

## Distribution scope (critical choice)

In **App information** → **Country/Region**, pick:
- ✅ **Overseas markets** — every region outside mainland China
  (Europe, US, SEA, etc.). This is what you want.
- ❌ **Chinese mainland** — requires Chinese ICP filing and additional
  cyber-security review. Skip.

If the console doesn't show separate toggles and only asks "where are
you distributing?", pick **Global except Chinese mainland**.

## App information

**App name** (≤64 chars)
```
aka Alarm
```

**Short introduction** (≤80 chars)
```
Wakes you within a 30-min window when you start stirring
```

**Introduction** (≤4000 chars)
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

**App tags / keywords** (5 max)
```
alarm, wake, sleep, gentle, smart
```

**Category**: **Tools** (primary). Some Huawei consoles allow a
secondary — pick **Lifestyle** if so.

**Languages**: English. Add Russian/German/etc. localisations later if
you want broader visibility.

**Support email**: `akaseeingred@gmail.com`
**App website**: `https://seeingred.github.io/aka-alarm/`
**Privacy policy URL** (required):
`https://seeingred.github.io/aka-alarm/privacy.html`

## Images and screenshots

| Asset | Size | File |
| --- | --- | --- |
| App icon | 216×216 PNG (Huawei resizes) | downscale `play-icon-512.png` |
| Feature graphic | 1080×460 PNG | `play-feature-graphic.png` (1024×500 — Huawei accepts) |
| Phone screenshots | 1080×1920 or higher, ≥3 | reuse Play / Amazon screenshots |

The 216 icon:
```sh
rsvg-convert -w 216 -h 216 /Users/aka/work/aka-alarm/app-icon.svg \
  -o /Users/aka/work/aka-alarm/huawei-icon-216.png
```

## Compliance and certificates

Huawei is more documentation-heavy than Google. Possible asks:

- **Copyright statement** — they sometimes ask for "proof of copyright
  ownership" for the app. Since we're MIT-licensed by the same legal
  entity as the developer (you), the answer is:
  ```
  This application is original work authored by Aleksandr Alekseev
  and released under the MIT licence. The full source code is publicly
  available at https://github.com/seeingred/aka-alarm. As both the
  copyright holder and the AppGallery submitter, no additional
  authorisation is required.
  ```
- **Software copyright certificate (SCRC)** — required only for
  mainland China distribution. Skip if you went Overseas-only.
- **Privacy statement check** — Huawei's automated check scans the
  Privacy URL for specific clauses (data collected, retention, sharing,
  contact). Our `docs/privacy.html` already addresses all of these.

## Permissions justification

After APK upload Huawei lists permissions. They will scrutinise:
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.POST_NOTIFICATIONS`

Paste this into the **Permission justification** field:

```
The microphone is required by the core wake-stirring detection feature. While an alarm is armed, the app maintains a rolling 5-minute baseline of room ambient sound level and triggers the alarm when the level rises above that baseline by a configurable threshold (i.e. when the user starts stirring). The microphone is only active between the user tapping Start and dismissing the alarm. Samples are processed in memory to compute a dBFS sound level and immediately discarded — no audio buffer is written to disk, persisted, or transmitted off-device. There are no network requests of any kind. The foreground service with microphone type is required on Android 14+ to keep the alarm reliable overnight; the persistent notification ("aka Alarm is listening…") is shown the entire time so users always know when the microphone is in use.
```

## APK upload

- Console → **Release** (left sidebar) → **App release** → **Add release**.
- Upload `aka-alarm-v1.1.2.apk`.
- **Update description** / **What's new**: paste the 1.1.2 entry from
  [CHANGELOG.md](../CHANGELOG.md). For first submission Huawei may show
  this as "App description" instead.

## Age rating

Huawei has their own IARC integration. Answer **No** to every category
(violence, sex, drugs, gambling, etc.). Result: **All Ages / 3+**.

## Submit

Once every section has a green check on the left sidebar, click
**Submit for review** at the top right.

- First-app review: **1–3 business days** typically. Sometimes faster.
- Updates: usually within a day.

You'll get email notifications from `noreply@hicloud.com` or similar at
each status change. Don't filter those to spam.

## Updating later

Each new release:
1. Push a `vX.Y.Z` tag — GitHub Actions builds the APK and publishes
   it on the Releases page.
2. AGC → My apps → aka Alarm → **Add release** → upload the new APK.
3. Paste release notes.
4. Submit.

Huawei doesn't auto-pull from GitHub Releases the way IzzyOnDroid does
(when it works), so this is a manual upload per release. Same shape as
Play and Amazon.
