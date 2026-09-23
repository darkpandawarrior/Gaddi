# Release runbook

## Versioning model (three-tier)

Source files at repo root:

| File | Meaning | Who bumps it |
|---|---|---|
| `MILESTONE` | integer, the only thing a human bumps to move the version forward | `scripts/bump_version.sh --milestone` |
| `VERSION` | informational semver, changelog/legacy reference only — does **not** drive any build number | `scripts/bump_version.sh --major\|--minor\|--patch` |
| `BUILD_NUMBER` | legacy, unused by any build target now (kept so old tooling doesn't break) | not touched |

Everything else is computed at build time from `MILESTONE` + `git rev-list --count HEAD` — never
hand-typed:

| Value | Format | Used for |
|---|---|---|
| **FINGERPRINT** | `YYYY.0M.0W.<MILESTONE>.<commitCount>` (e.g. `2026.07.29.1.126`) | git tag (`v<FINGERPRINT>`), GitHub release title, Android `BuildConfig.FINGERPRINT`, Android debug `versionNameSuffix`, server `/version`, iOS build-info |
| **MARKETING** | `YYYY.M.<MILESTONE>` (e.g. `2026.7.1`, ≤3 integer components — iOS `CFBundleShortVersionString` hard limit) | Android release `versionName`, iOS `CFBundleShortVersionString`, desktop `packageVersion`/`dmgPackageVersion`/`msiPackageVersion`/`debPackageVersion` |
| **BUILDCODE** | `1 + commitCount`, monotonic int | Android `versionCode`, iOS `CFBundleVersion` |

Implementation:
- `gradle/versioning.gradle.kts` — shared script plugin, applied by `cmp-android`, `cmp-desktop`,
  `server` (`apply(from = "$rootDir/gradle/versioning.gradle.kts")`), exposing `gaddiFingerprint` /
  `gaddiMarketing` / `gaddiBuildCode` as `extra` properties.
- `cmp-android/build.gradle.kts` — `versionCode = BUILDCODE`, `versionName = MARKETING` (release) /
  `MARKETING-FINGERPRINT` (debug suffix), `BuildConfig.FINGERPRINT`.
- `cmp-desktop/build.gradle.kts` — all `packageVersion` variants = MARKETING.
- `server/build.gradle.kts` — generates `com.kursi.server.BuildInfo.FINGERPRINT`, served at `GET
  /version`.
- `iosApp/` — `project.pbxproj`'s checked-in `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` are a
  local-build fallback only. CI stamps the real values before an archive via
  `scripts/gen_ios_version.sh` (same MILESTONE + commitCount inputs as the Gradle side).

## Cutting a release

1. `scripts/bump_version.sh --milestone` locally, or trigger **Release** workflow
   (`workflow_dispatch`) with `version_bump: milestone` — either way this opens a PR bumping
   `MILESTONE` (never pushes to `main` directly, so a future required-check ruleset won't block it).
2. Merge that PR.
3. Tag the merge commit: `git tag v$(bash scripts/bump_version.sh --commit | grep FINGERPRINT | cut -d' ' -f2) && git push --tags`
   (or just compute FINGERPRINT by hand from the table above).
4. Pushing the `v*` tag triggers **GitHub Release** (`.github/workflows/github-release.yml`): builds
   Android APKs (gms/noGms × debug/release), fails if no release APK was produced, creates/updates
   the GitHub Release with a commit-log changelog, and attaches the APKs. It also runs a
   best-effort, non-blocking iOS unsigned-build check.
5. The GitHub Release publish event fan-outs to `desktop-release.yml` (packages Dmg/Msi/Deb across a
   per-OS matrix and uploads them to the same release).
6. Store distribution (Play/Amazon/Aptoide/Huawei/Indus/Samsung/F-Droid) and TestFlight/App Store are
   separate, manual `workflow_dispatch` runs of `release.yml` and the per-store `*-deploy.yml`
   workflows — not triggered automatically by a tag, so a bad tag never force-ships to a store.

## Secrets (owner-only — workflows are correct-but-dormant until these are added)

All Android deploy workflows (`release.yml`, `github-release.yml`, `amazon-appstore-deploy.yml`,
`aptoide-deploy.yml`, `huawei-appgallery-deploy.yml`, `indus-deploy.yml`,
`samsung-galaxy-store-deploy.yml`, `publish-fdroid.yml`) use the **same canonical keystore secret
names** — GitHub → Settings → Secrets and variables → Actions:

| Secret | Contents |
|---|---|
| `ANDROID_KEYSTORE_B64` | base64 of the release keystore (`base64 -i gaddi-release.keystore \| pbcopy`) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias (`kursi` unless you generated a different one) |
| `ANDROID_KEY_PASSWORD` | key password |

Per-store secrets (only needed for the store you're actually shipping to):

| Store | Secrets |
|---|---|
| Play Store (`release.yml`) | `PLAYSTORE_CREDS_B64` (base64 of the service-account JSON) |
| Amazon Appstore | `AMAZON_APPSTORE_CLIENT_ID`, `AMAZON_APPSTORE_CLIENT_SECRET`, `AMAZON_APPSTORE_APP_ID` |
| Aptoide | `APTOIDE_API_KEY` |
| Huawei AppGallery | `HUAWEI_CLIENT_ID`, `HUAWEI_CLIENT_KEY`, `HUAWEI_APP_ID` |
| Indus Appstore | `INDUS_API_KEY`, `INDUS_APP_ID` |
| Samsung Galaxy Store | `SAMSUNG_ACCESS_TOKEN`, `SAMSUNG_SERVICE_ACCOUNT_ID`, `SAMSUNG_CONTENT_ID` |
| iOS TestFlight/App Store (`release.yml`) | `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_CONTENT` |
| Server (Fly.io) (`server-deploy.yml`) | `FLY_API_TOKEN` |

Optional:

| Variable/Secret | Purpose |
|---|---|
| `RELEASE_BUILD_CMD` (repo **variable**, not secret) | overrides the Gradle task list `github-release.yml` builds — default is `assembleGmsDebug assembleGmsRelease assembleNoGmsDebug assembleNoGmsRelease` for `:cmp-android` |
| `RELEASE_SCRUBLIST_B64` | base64 of a newline-separated grep pattern list; if set, release notes are checked against it and refused if they match |

Until `ANDROID_KEYSTORE_B64` etc. are set, release builds fall back to **debug signing** (see
`cmp-android/build.gradle.kts`'s `hasReleaseSigning` check) — release APKs still build and attach to
GitHub Releases, just unsigned/debug-signed, which is fine for sideloading but not for store
submission.
