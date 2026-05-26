# GitHub Actions release pipeline

`.github/workflows/release.yml` builds and ships an Android release every
time you push a `vX.Y.Z` tag. Output: a GitHub Release with the signed APK
and AAB attached, and release notes auto-pulled from `CHANGELOG.md`.

This is what makes the repo a **distribution channel of its own**. Obtainium
users (and similar APK side-load auto-updaters) point at
<https://github.com/seeingred/aka-alarm/releases> and get every release
automatically. F-Droid's tag watcher uses the same tags. And iOS App Store
shipments don't need to touch this pipeline — they still go through Xcode
Archive on a local Mac.

## One-time setup: repo secrets

The workflow needs four secrets to sign the build. Add them at
<https://github.com/seeingred/aka-alarm/settings/secrets/actions>:

| Secret name | Value | How to produce it |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64-encoded `aka-alarm-upload.jks` | `base64 -i android/aka-alarm-upload.jks \| pbcopy` then paste |
| `ANDROID_KEYSTORE_PASSWORD` | The `storePassword` from `android/keystore.properties` | copy from the file |
| `ANDROID_KEY_ALIAS` | `aka-alarm-upload` (or whatever your `keyAlias` is) | copy from the file |
| `ANDROID_KEY_PASSWORD` | The `keyPassword` from `android/keystore.properties` | copy from the file |

After saving all four, the workflow can build signed binaries.

## Shipping a release

```sh
# 1. Bump the versions in the source files.
$EDITOR ios/project.yml                         # MARKETING_VERSION, CURRENT_PROJECT_VERSION
$EDITOR android/app/build.gradle.kts            # versionName, versionCode (++ for every Play upload)

# 2. Add a new section at the top of CHANGELOG.md describing the release.

# 3. Commit everything.
git add -A
git commit -m "Bump to vX.Y.Z"

# 4. Tag and push.
git tag vX.Y.Z
git push origin main vX.Y.Z
```

GitHub Actions kicks off automatically when the tag lands. ~3–5 minutes
later, the release shows up at
<https://github.com/seeingred/aka-alarm/releases> with the signed APK and
AAB attached.

For Play Console and Huawei AppGallery, download the AAB / APK from the
release page and upload manually (we don't auto-publish to those stores —
that would need each one's CI credentials baked in, which isn't worth the
trouble for the cadence we ship at).

## What CHANGELOG.md should look like

The release-notes extraction in the workflow looks for headings matching
`## X.Y.Z ` (optionally followed by ` — date`). Anything between that
heading and the next `## ` block becomes the release body. Example:

```markdown
## 1.1.0 — 2026-05-27

Description of changes.

**Highlights**
- Bullet 1
- Bullet 2

## 1.0.0 — 2026-05-14

Earlier release.
```

A missing section falls back to the annotated tag message, so
`git tag -a vX.Y.Z -m "Notes here"` also works if you skip the CHANGELOG.

## What if the workflow fails

Most failures are signing issues — the secret was missing, mistyped, or
the keystore base64-encode produced extra newlines. Check the failed job's
"Decode signing keystore" step output: a healthy keystore is around 2.7 KB.
Anything zero-length means the secret wasn't actually populated.
