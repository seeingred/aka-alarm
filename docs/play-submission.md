# Google Play submission cheat sheet

Reference for paste-ready text when filling out the Google Play Console for
aka Alarm 1.0. Mirrors the App Store cheat sheet so the two listings stay
consistent.

---

## Prerequisites

1. **Google Play Console developer account** — one-time $25 fee at
   <https://play.google.com/console>. Use the same Google account you'll
   pay licence fees from.
2. **Upload keystore** — generated locally; you keep the `.jks`. Steps live
   in [`../android/keystore.properties.example`](../android/keystore.properties.example).
3. **Privacy policy URL** — same one as iOS: `https://seeingred.github.io/aka-alarm/privacy.html`.

## Building the AAB

```sh
cd android
./gradlew clean bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab` (≈10–20 MB).

This is the file you upload to Play Console. Each new release: bump
`versionCode` in `app/build.gradle.kts` (integer, must increase) and
`versionName` (the user-visible string).

---

## Play Console — Create app

**App name** (≤30 chars)
```
aka Alarm
```

**Default language**: English (United States)

**App or game**: App
**Free or paid**: Free
**Declarations**: tick all three (Developer Program Policies, US export laws).

---

## Store listing → Main store listing

**Short description** (≤80 chars)
```
A gentler wake-up. Wakes you when you start stirring, not at a fixed time.
```

**Full description** (≤4000 chars)
```
aka Alarm wakes you at the right moment in your chosen window — not at an arbitrary fixed time.

Pick a wake-up window (any time, plus 30 minutes). Tap Start. aka Alarm listens to the ambient sound of your room and builds a rolling baseline of how quiet it really is. When the window opens, the first time you turn over, sigh, or shift the duvet, the alarm fires. If nothing happens, it fires at the end of the window so you never oversleep.

• Gentle wake-up — the alarm tone fades from 1 % to 100 % over a minute, so you come out of sleep gradually instead of being jolted.
• Smart snooze — lightly move the phone to snooze. The snooze duration is randomised between 60 seconds and 15 minutes, clamped to whatever's left of your window.
• On-device only — audio never leaves your phone. Nothing is recorded, stored, or transmitted. No accounts, no servers, no analytics.
• Built for modern Android — Material 3 with dynamic colours and dark-mode friendly.

How it works in one paragraph: the app continuously samples microphone input through a foreground service to compute the ambient sound level (in dBFS) and maintains a five-minute rolling baseline. Within the wake window, a level rise above the baseline by a configurable threshold triggers the alarm. The microphone is active only while an alarm is set. Audio samples are processed in memory and immediately discarded.

aka Alarm is open source under the MIT licence.
```

**App icon** (512×512 PNG)
- Use the same artwork as the iOS icon. Convert the SVG with:
  ```sh
  rsvg-convert -w 512 -h 512 app-icon.svg -o play-icon-512.png
  ```

**Feature graphic** (1024×500 PNG, required)
- Banner shown at the top of the listing. Simple is fine — solid gradient with the icon centered, or just a screenshot.

**Phone screenshots** (2 minimum, 8 max; 1080×1920 or similar 9:16)
- Set screen, monitoring, alarm-firing, snoozing. Take from your Nothing Phone via `adb exec-out screencap -p > screenshot.png` while the app is on-screen.

**App category**: Lifestyle (or Tools)
**Tags**: Wake up, Alarm, Sleep
**Email**: your contact email (matches the privacy policy)
**Website**: <https://seeingred.github.io/aka-alarm/>
**Privacy policy**: <https://seeingred.github.io/aka-alarm/privacy.html>

---

## Production track → Create new release

1. Sign up for **Play App Signing** when prompted (recommended; Google
   manages the production signing key, you only sign uploads with the
   upload key from your keystore).
2. **Upload** `app-release.aab`.
3. **Release name**: `1.0.0 (1)` (Play Console auto-suggests).
4. **Release notes** (paste from [`../CHANGELOG.md`](../CHANGELOG.md) 1.0.0 entry).

---

## App content (required questionnaires)

### Privacy policy
- URL: `https://seeingred.github.io/aka-alarm/privacy.html`

### App access
- All functionality is available without restrictions → no login.

### Ads
- Does your app contain ads? **No**.

### Content rating
- Start the questionnaire, answer "No" to every category (Violence, Sexuality,
  Profanity, Drugs/Alcohol/Tobacco, Gambling, etc.). Result will be **Everyone**.

### Target audience and content
- Target age groups: **18+** (alarm app, no kids-specific content). Or "Adult"
  if you want to avoid the COPPA / Kids policies entirely.

### News app
- No.

### COVID-19 contact tracing
- No.

### Data safety (this is the big one — Play's privacy nutrition labels)

Answer:
- **Does your app collect or share any of the required user data types?** No
- **Is all of the user data collected by your app encrypted in transit?** Yes (irrelevant since we collect nothing, but Play wants an answer)
- **Do you provide a way for users to request that their data be deleted?** Yes

The microphone is *accessed* but not *collected* — Google's definition of
"collected" means transmitted off-device or persisted in a way linked to a
user identifier. We do neither.

### Government apps
- No.

### Financial features
- No.

### Health
- No (this is a sleep aid, not a medical/health device).

### Permissions
- `RECORD_AUDIO`: needed for the wake-stirring detection (explained on screen
  with system prompt).
- `POST_NOTIFICATIONS`: needed for the foreground-service notification.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`: continuous mic
  monitoring while alarm is armed.
- `VIBRATE`, `WAKE_LOCK`: alarm haptics and keeping the screen alive at fire-time.

### Sensitive permissions justification

Google will ask you to justify `RECORD_AUDIO` and the foreground-service
microphone type. Paste this into the **Microphone usage** field:

```
aka Alarm uses the microphone to detect when the user starts stirring within their chosen 30-minute wake window. The app maintains a rolling 5-minute baseline of ambient sound level (in dBFS) and triggers the alarm when the current level rises above that baseline by a configurable threshold. The microphone is only active while an alarm is armed (i.e., between the user tapping Start and the alarm being dismissed). Microphone samples are processed in memory to compute a sound level and immediately discarded; no audio buffer is written to disk, persisted in memory, or transmitted off-device. There are no network requests of any kind.
```

---

## Pricing and distribution

- **Countries**: All (or pick).
- **Pricing**: Free.

---

## Submit

Once every section in the left sidebar shows a green check, hit **Send for
review** on the production release page. Review takes anywhere from a few
hours to a few days for a first submission. You'll get an email when it's
approved or rejected.

---

## Updating later

Each new release:
1. Bump `versionCode` (integer, must increase) and `versionName` in
   `app/build.gradle.kts`.
2. Add a new section at the top of [`../CHANGELOG.md`](../CHANGELOG.md).
3. `./gradlew bundleRelease`.
4. Play Console → Production → Create new release → upload the new AAB →
   paste release notes → roll out.
