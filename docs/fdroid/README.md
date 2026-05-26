# F-Droid submission

aka Alarm meets all of F-Droid's free-software requirements: MIT licensed,
no proprietary dependencies, no analytics, no network code. The only
sensitive thing is the microphone permission, which is justified in the
description on the [metadata file](com.aka.alarm.yml).

## First submission

1. **Tag the current release** so F-Droid's `UpdateCheckMode: Tags` can
   track new versions. From the repo root:

   ```sh
   git tag v1.1.0 9fdee60
   git push origin v1.1.0
   ```

   For future releases, tag as `vX.Y.Z` on each release commit and push.

2. **Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata)** on GitLab.
   You'll need a free GitLab account.

3. **Clone your fork** and add the metadata file:

   ```sh
   git clone https://gitlab.com/<your-gitlab-user>/fdroiddata.git
   cd fdroiddata
   cp /Users/aka/work/aka-alarm/docs/fdroid/com.aka.alarm.yml \
      metadata/com.aka.alarm.yml
   ```

4. **(Optional) Test the build locally.** F-Droid provides a Docker image
   that runs their build pipeline. This catches issues before reviewers do:

   ```sh
   # On the fdroiddata clone:
   docker run --rm -v "$PWD:/repo" -w /repo registry.gitlab.com/fdroid/fdroidserver:buildserver \
     fdroid build --verbose com.aka.alarm
   ```

   If the build succeeds, your APK ends up in `unsigned/`. F-Droid will
   reproduce this build on their own infrastructure.

5. **Open a merge request** on
   <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new>:

   - Title: `Add com.aka.alarm`
   - Description: link to the GitHub source, mention MIT licence, summarise
     what the app does and the rationale for the microphone permission.
   - Add the `New App` label if available.

6. **Wait.** First-inclusion review typically takes 2–8 weeks. Reviewers
   will comment on the MR with feedback (anti-features, metadata fixes,
   build issues). Respond on the thread.

7. **Once merged**, F-Droid's build infrastructure compiles, signs (with
   F-Droid's key, not yours), and publishes to <https://f-droid.org/> on
   their next batch cycle (usually within a few days of merge).

## Subsequent releases

After inclusion, the workflow becomes trivial:

1. Bump `versionCode` + `versionName` in `android/app/build.gradle.kts`.
2. Commit, tag (`git tag v1.2.0 && git push origin v1.2.0`), push.
3. F-Droid's bot polls our tags, detects the new release, builds it,
   publishes automatically. No MR needed for a routine update.

If a release requires adding a new dependency or changing the build
recipe, edit `com.aka.alarm.yml` in this folder, copy to fdroiddata, MR
again.

## Anti-features to expect

F-Droid's review may flag:

- `NonFreeNet` — None of our dependencies talk to non-free network
  services. We should be clean.
- `Tracking` — None. We have no analytics.
- `NonFreeAdd` — No SDKs that depend on non-free services.
- `KnownVuln` — None known.

If they flag a permission concern about `RECORD_AUDIO` or
`FOREGROUND_SERVICE_MICROPHONE`, the response is the same as the Play
Console justification: mic is active only while an alarm is armed,
audio is processed in memory and immediately discarded, no audio
buffer is written to disk or transmitted.
