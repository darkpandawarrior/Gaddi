# IzzyOnDroid submission — Gaddi

Ready to paste into a new issue at **https://codeberg.org/IzzyOnDroid/repodata/issues/new**
using the **"App Inclusion Request"** template (`.forgejo/issue_template/app-inclusion-request.yaml`,
verified live on the `main` branch 2026-08-26). That page renders the fields below as a form —
this file gives the answer for each field in the same order, so paste per-field into the form
rather than pasting this whole file as one issue body.

Issue title: `[AppRequest] Gaddi: Bluffing Card Game`

Labels the template auto-applies: `app-request`, `needs/apk-scan` — no action needed, Forgejo sets
these from the template.

---

## Guidelines checkboxes

- [x] I am the developer of the app.
- [x] The app complies with the App Inclusion Policy (https://izzyondroid.org/docs/general/AppInclusionPolicy/).
- [x] The app is not already listed in the repo or issue tracker. *(checked by searching
      https://apt.izzysoft.de/fdroid/ and the Codeberg issue tracker on 2026-08-26 — no match)*
- [x] The Fastlane folder is available in the app's repo, at
      `fastlane/metadata/android/en-US/` — `title.txt`, `short_description.txt` (59 chars),
      `full_description.txt` (2,800 chars), `images/featureGraphic.png`, and 5 phone screenshots
      are present. **Gap:** no `images/icon.png` yet — strongly recommended but not mandatory per
      the Fastlane doc; add before or shortly after filing if time allows, otherwise their scanner
      pulls the icon straight from the APK.

## App meta-data

**Link to the source code**
`https://github.com/darkpandawarrior/Gaddi` — verified public 2026-08-26
(`"private": false, "visibility": "public"` via the GitHub API).

**Link to app in another app store**
None yet. Not on Google Play, Apple App Store, or any other store at time of filing — those are
planned for next month. Leave this field blank rather than pointing at the self-hosted F-Droid
repo (that's the repo this app is *leaving*, not a second storefront).

**License used**
`GPL-3.0-or-later`, with a GPLv3 §7 additional permission allowing distribution through Apple's
App Store and other stores with binary-compatible-license restrictions (see `LICENSE`,
"ADDITIONAL PERMISSION UNDER GNU GPL VERSION 3 SECTION 7"). Bundled creative assets (art, audio)
are separately licensed CC BY-SA 4.0 per `ASSETS-LICENSE` — worth a one-line mention in Further
Notices so the scanner/reviewer doesn't flag a license mismatch on non-code assets.

**Categories**
`Games`

**Summary**
A bluffing card game set in a satirical India corporate-political underworld — claim any role,
call bluffs, survive to hold the Gaddi. Fully offline, 10 AI opponents, zero ads or accounts.

**Description**
```
Gaddi ke liye kuch bhi karega.

Bluffing card game set in a satirical India corporate-political underworld. Claim any role on
your turn, whether you hold it or not. If someone calls your bluff and they're right, you lose
influence. If they're wrong, they do. Last player standing wins the Gaddi.

Five roles, each a deadpan archetype: Netaji Vachan (claims FDI, blocks Foreign Aid), Bhai Teja
(collects Supari, blocks assassination), Babu Filewala (levies Tax, blocks Vasooli), Jugaadu
Chhotu (Vasooli/steal), Vakil Loophole (blocks Supari).

10 AI bots, each bluffs differently. Modes: vs AI (1 human vs up to 9 bots), GAUNTLET (5-rung
escalating ladder), Team Khel (faction play), DARBAR (bots chat, conspire, form alliances and
hold grudges — you can manipulate them), TAMASHA (AI plays all seats, watch it unfold).

Interactive tutorial, career stats and bot head-to-head records, deterministic-replay auto-resume,
full TalkBack semantics, colour-vision-deficiency-safe role colours. Plays entirely offline — no
account, no ads, no server required for single-player modes.

Written in Kotlin Multiplatform and Compose Multiplatform (Android, iOS, desktop, web), Ktor
server backing the optional online modes. This is the noGms build: no Google Play Services, no
Firebase, no ML Kit, no proprietary blobs of any kind. The on-device AI opponent shipped in the
Play Store build is intentionally left out of this flavor for that reason — this build's
opponents run on the built-in rule-based engine instead. Source: GPL-3.0-or-later.

All characters and events are fictional. Satire only.
```

## Technical instructions

**Build instructions**
```
git clone https://github.com/darkpandawarrior/Gaddi.git
cd Gaddi
./gradlew :cmp-android:assembleNoGmsRelease -Pfdroid
```
Output: `cmp-android/build/outputs/apk/noGms/release/cmp-android-noGms-release-unsigned.apk`.
The `-Pfdroid` flag excludes `com.google.mediapipe:tasks-genai` and the entire
`com.google.mlkit` / `com.google.android.gms` dependency groups from the build — this is what
makes the noGms flavor actually GMS-free rather than merely GMS-optional. Gradle subproject is
`cmp-android`; root `settings.gradle.kts` wires the rest.

## AI Tools Usage

*Fill honestly before submitting — draft below reflects how this repo's own CLAUDE.md and CI
scripts (e.g. `scripts/check-license-consistency.sh`) describe the actual workflow, but only
Siddharth can attest to the real assistance level and it should be reviewed, not pasted blind.*

**Assistance Level:** Substantial — used throughout development for code generation, architecture
decisions, debugging, and documentation, under continuous human direction and review.

**AI Tool(s):** Claude Code (Anthropic)

**What did the tools help with:** Architecture and module structure across the Kotlin
Multiplatform / Compose Multiplatform codebase, feature implementation, CI/CD pipeline authoring,
debugging, and documentation (fastlane copy, licensing/versioning docs).

**AI Accountability**
- [x] The human developer(s) reviewed and edited all AI-generated outputs.
- [x] The human developer(s) ran manual tests and manually verified all changes.

## Further Notices

Proactively declaring anti-features so the scanner result and this statement can be compared
directly, rather than asking the reviewer to take the claim on faith:

**Expected anti-feature tags: none.** The `-Pfdroid` noGms build strips the entire
`com.google.mlkit` and `com.google.android.gms` package groups plus `com.google.mediapipe:tasks-genai`
before packaging. Verified by unzipping the actual release artifact served from the self-hosted
repo (`https://darkpandawarrior.github.io/fdroid/repo`, package `com.kursi.android`) on
2026-08-26: exactly two native libraries, both AndroidX (`libandroidx.graphics.path.so`, ~10 KB
each), zero bundled proprietary jar/aar files, and no `com/google/android/gms` or
`com/google/mlkit` smali paths — so an Apktool/`scanapk.php` pass against this APK has nothing
from those groups to match against a library-signature database in the first place. Removing
those groups also dropped the AICore `BIND_SERVICE` permission that the GMS-flavor build declares.

**Size:** 16.5 MB (17,272,339 bytes exactly, per the release asset and the self-hosted repo's own
`index-v2.json`) — comfortably under the ~30 MB soft ceiling in the Inclusion Policy, so no
retention-window concern.

**Signing:** release APK signed with the developer's own key,
SHA-256 `e3cd9ed25baaa6db5501621a2a7399edc0878022f9b64b5d95446db0348dd19c`. Latest tag:
`v2026.08.35.1.234`, GitHub Release asset name `Kursi-v2026.08.35.1.234.apk`
(SHA-256 sidecar: `Kursi-v2026.08.35.1.234.apk.sha256`), release URL:
`https://github.com/darkpandawarrior/Gaddi/releases/tag/v2026.08.35.1.234`. Tags follow
`v<YYYY>.<0M>.<0W>.<MILESTONE>.<commitCount>` — for automated update tracking on your end, a
`UpdateCheckMode: Tags ^v[0-9]{4}\.[0-9]{2}\.[0-9]{2}\.[0-9]+\.[0-9]+$` /
`AutoUpdateMode: Version` pairing (same as this project's own F-Droid metadata) should pick up
every future tagged release without manual intervention.

**Non-code assets:** bundled art/audio are CC BY-SA 4.0 under `ASSETS-LICENSE`, distinct from the
GPL-3.0-or-later code license — noting this upfront so it isn't read as a licensing inconsistency
during review.
