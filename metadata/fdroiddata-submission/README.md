# Gaddi → official fdroiddata: submission notes

This directory holds a draft `com.kursi.android.yml` for the **official** F-Droid catalogue
(`gitlab.com/fdroid/fdroiddata`) — the repo browsable/searchable inside the F-Droid client itself,
as opposed to the self-hosted repo at `darkpandawarrior.github.io/fdroid`. It is not wired into any
CI here and does not affect the self-hosted metadata at `../com.kursi.android.yml`.

Everything below was checked live against F-Droid's docs and the fdroiddata repo on 2026-08-26
(sources cited per section), plus one thing verified by actually running the build locally rather
than reading about it.

## 1. Submission mechanism (confirmed)

Source: https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/ and
https://gitlab.com/fdroid/fdroiddata/-/raw/master/CONTRIBUTING.md

```bash
# One-time setup
# 1. Fork https://gitlab.com/fdroid/fdroiddata on GitLab (needs a GitLab account)
git clone git@gitlab.com:<your-gitlab-username>/fdroiddata.git
cd fdroiddata
git remote add upstream https://gitlab.com/fdroid/fdroiddata.git
git checkout -b com.kursi.android

# Add the metadata file
cp /Users/darkpandawarrior/Repos/Android/Kursi/metadata/fdroiddata-submission/com.kursi.android.yml \
   metadata/com.kursi.android.yml

git add metadata/com.kursi.android.yml
git commit -m "New App: com.kursi.android"
git push origin com.kursi.android
```

Then open a merge request from `<your-fork>:com.kursi.android` into `fdroid/fdroiddata:master` at
https://gitlab.com/fdroid/fdroiddata/-/merge_requests — fill out the MR template, commit message
`New App: com.kursi.android` per the quick-start guide. An RFP issue at gitlab.com/fdroid/rfp first
is not documented as required for a new-app MR; it's a common courtesy for apps likely to draw
debate, optional here.

**Before opening the MR**, push to the fork and let fdroiddata's own CI pipeline validate it — the
CONTRIBUTING.md doc: "you can use our CI to test your metadata easily... commit and push your
changes to your fork. The pipelines will be triggered automatically." If you have `fdroidserver`
installed locally instead:

```bash
pip install git+https://gitlab.com/fdroid/fdroidserver.git
fdroid readmeta
fdroid rewritemeta com.kursi.android
fdroid checkupdates --allow-dirty com.kursi.android
fdroid lint com.kursi.android
fdroid build -v -l com.kursi.android
```

Fix and re-run on any failure. `fdroid build` actually clones the tag/commit and runs the real
Gradle build inside F-Droid's build container — this is the closest thing to a dry run of what
their build farm will do.

## 2. Build-from-source vs prebuilt (confirmed — source, and it should)

Source build was chosen: a `Builds:` block with `subdir`, `gradle`, `gradleprops`, `output`.
F-Droid's inclusion policy states the complete build process should use a 100% FLOSS toolchain and
strongly prefers building from source over accepting prebuilt binaries — Gaddi's noGms flavor
qualifies now that it's dependency-clean (per this session's verified state: GMS/ML Kit/Firebase
class references and the AICore permission are gone; the only native lib is AndroidX's own
`libandroidx.graphics.path.so`).

No `Binaries:` / `AllowedAPKSigningKeys:` block. F-Droid uses those to verify its own from-source
build byte-for-byte against a developer-signed release, as a "reproducible build" badge — it is not
required for inclusion. **Left out deliberately, not forgotten**: `gradle/versioning.gradle.kts`
derives the fingerprint version string's `yyyy.MM.ww` (year/month/week) components from
`Date()` — the wall-clock date at the moment Gradle configures, not from the tag's commit date. Tag
`v2026.08.35.1.234` got that string when the release workflow ran on the week it was cut; if
F-Droid's builder checks out that same tag/commit weeks later (its build queue has no SLA), its
`kursiFmt("yyyy")/("MM")/("ww")` calls will read *that* day's date instead, embedding a different
`versionName` (and therefore different APK bytes) than the originally tagged release. This doesn't
block the source build or the MR — F-Droid will still build, sign with its own key, and ship it —
but it would make any future reproducible-build claim false more often than not. Fix before adding
`Binaries:`: derive those three components from `git log -1 --format=%cd --date=format:... <commit>`
of the commit being built, not from `Date()`.

## 3. Build server requirements (partially confirmed — flag one real gap)

- **Repo is public**: yes, confirmed (`github.com/darkpandawarrior/Gaddi`).
- **Tag exists**: yes — `v2026.08.35.1.234`, commit `00784a2c937cc080d3b684a02ff319a3d4f091aa`
  (`git rev-parse`, checked directly against this working tree, not assumed).
- **Submodules resolve without secrets**: `external/kmp-toolkit` and `external/kmp-build-logic` are
  both public GitHub repos (`.gitmodules`, checked directly) and `submodules: true` in the Build:
  block runs `git submodule update --init --recursive` per the Build Metadata Reference — this is a
  first-class, documented feature, not a workaround.
- **No secrets needed**: confirmed by actually running the release build locally with no
  `keystore.properties` present — `hasReleaseSigning` is false, the build still completes, and
  produces a real (unsigned) APK. Same condition F-Droid's builder will be in.
- **What I could NOT confirm from docs**: whether the build server's network egress reaches
  arbitrary git hosts (github.com) versus only a fixed allowlist of package registries. None of the
  pages fetched (Build Server Setup, Reproducible Builds, Inclusion Policy) state a network policy
  for the build execution phase explicitly. The strongest evidence available is indirect but real:
  the `submodules: true` feature is a maintained, documented part of the Build: schema whose entire
  purpose is fetching submodule content over the network during the build, and F-Droid already
  builds thousands of Gradle apps that resolve dependencies from Maven Central / Google's Maven at
  build time. Both submodules here are on the same host (github.com) the main repo is already
  cloned from, not a third-party or private host, so the risk this raises is materially lower than
  it would be for a submodule pointing somewhere F-Droid has no other reason to reach. Still:
  confirm with a real `fdroid build -v -l com.kursi.android` before relying on this — that is the
  one step in this whole process that actually proves it rather than argues for it.
- **Kotlin Multiplatform / Gradle composite builds (`includeBuild`)**: not mentioned anywhere in
  F-Droid's docs, positively or negatively. From F-Droid's side this is invisible — it just runs
  `gradle :cmp-android:assembleNoGmsRelease` and composite builds are Gradle's own mechanism, not
  something the outer build system needs to know about. No reason to expect it not to work; no doc
  confirms it either. Untested against F-Droid's actual builder as of this writing.

## 4. Metadata linter rules (confirmed where checked)

- `License:` — SPDX identifier, one license for the whole app. `GPL-3.0-or-later` matches this
  repo's own `LICENSE` file (kept in lockstep by `scripts/check-license-consistency.sh` per the
  self-hosted metadata's own comment). `ASSETS-LICENSE` (CC BY-SA 4.0, images only) is a separate
  concern the self-hosted metadata also doesn't surface — consistent, not an omission.
- `Categories:` — no fixed enum enforced by the schema itself ("both the client and the web site
  will automatically show any categories that exist in any applications" — Build Metadata
  Reference), but convention is to reuse an existing one. `Games` is already an established
  category on f-droid.org and is what the self-hosted metadata already uses — kept for consistency,
  not reinvented.
- `Summary`/`Description`/`Name` — confirmed the current guidance is to *not* set these inline; see
  the "No Name/Summary/Description" comment in the yml. Caps if you ever do set them inline: 80 /
  4000 / 50 characters respectively (Build Metadata Reference, All About Descriptions page).
- `AntiFeatures` vocabulary (confirmed list): `Ads`, `KnownVuln`, `NonFreeAdd`, `NonFreeAssets`,
  `NonFreeDep`, `NonFreeNet`, `NoSourceSince`, `NSFW`, `TetheredNet`, `Tracking`. None apply to the
  noGms flavor being submitted here — see the caveat below though.
- Marketing-language rules: no explicit prohibition found in any fetched doc page. The submitted
  description text (from `fastlane/metadata/android/en-US/full_description.txt`) already reads as
  factual feature description rather than ad copy, so this wasn't a live concern either way.

## Notes / what to watch (found while verifying, not assumed)

- **Leftover Google/Firebase resource strings, even in the "clean" build.** Unzipped the actual
  `-Pfdroid` build (`cmp-android-noGms-release.apk`, built locally 2026-08-26) and it still contains
  `firebase-annotations.properties`, `firebase-components.properties`,
  `firebase-encoders(-json).properties`, and three `res/color/common_google_signin_btn_*.xml`
  files — none referenced by any manifest component, near as checked, but present as bytes. This is
  new information, not previously recorded in this session's verified state, which is byte-verified
  against *some* build of the APK but not necessarily this exact `-Pfdroid` (unshrunk) variant.
  Root cause: `isShrinkResources = !fdroidBuild`, so F-Droid's own reproducible-build flag disables
  the resource shrinking pass that would otherwise strip these dead resources out of the normal
  Play-Store release. They come from a transitive AndroidX/Compose dependency's bundled resources,
  not from the excluded GMS/ML Kit/Firebase dependency groups themselves (those exclusions are
  confirmed working — no class files, no native libs beyond AndroidX's own). Functionally inert as
  far as checked, but worth a look before or shortly after the MR goes up, since a reviewer who
  unzips the APK (some do) will see "google" and "firebase" strings in an app claiming to be
  Google-free and may ask about it before checking that it's dead weight.
- **The self-hosted metadata's `output:` path is stale.** `../com.kursi.android.yml` (the
  self-hosted one) points at `cmp-android-noGms-release-unsigned.apk`. The actual file Gradle
  produces, verified by running the build, is `cmp-android-noGms-release.apk` — no `-unsigned`
  suffix; that suffix is added by a rename step in `.github/workflows/github-release.yml` for
  GitHub Release asset names, not by Gradle. Whatever machinery builds the self-hosted repo either
  tolerates this mismatch some other way or has the same latent bug — worth checking independently
  of this submission.
- **iOS/desktop/web/server directories in the repo are irrelevant noise for F-Droid**, not a
  problem: F-Droid only ever looks at `subdir: cmp-android` for this Build entry, so the rest of the
  monorepo (`cmp-ios`, `cmp-desktop`, `cmp-web`, `server`, `shared-protocol`) is never touched.

## Recommended order of operations

1. `fdroid lint` / `fdroid build -v -l` locally or via the fork's CI first — this is the one step
   that actually proves the submodule/composite-build/network questions above rather than arguing
   for them.
2. Decide on the leftover Firebase/Google-Sign-In resource strings — confirm they're genuinely dead
   (no manifest reference) or file a follow-up fix; either is fine, just don't let it be the first
   thing a reviewer notices unannounced.
3. Open the MR once the fork's pipeline is green.
