// gradle/versioning.gradle.kts — three-tier versioning shared by cmp-android, cmp-desktop, server.
// See docs/RELEASE.md for the model. Apply with `apply(from = "$rootDir/gradle/versioning.gradle.kts")`.
//
// Source files (repo root): VERSION (informational semver), MILESTONE (integer, bumped by
// scripts/bump_version.sh --milestone), BUILD_NUMBER (legacy, no longer drives versionCode).
//
// Derived (all computed, none hand-typed):
//   gaddiFingerprint = YYYY.0M.0W.<MILESTONE>.<commitCount>  e.g. 2026.07.03.4.127
//   gaddiMarketing   = YYYY.M.<MILESTONE>                    e.g. 2026.7.4  (<=3 int components,
//                      required by iOS CFBundleShortVersionString)
//   gaddiBuildCode   = VERSION_CODE_BASE + commitCount        monotonic int, < 2.1e9

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

// A shallow clone reports 1 commit, which silently produces versionCode 2 and an absurd
// FINGERPRINT. That is exactly what shipped: every Gaddi APK published to F-Droid carried
// versionCode 2, so no client could ever see an upgrade. actions/checkout defaults to
// fetch-depth 1, so any workflow that forgets fetch-depth: 0 reintroduces it.
//
// Refuse rather than guess. A build that cannot know its own version must not produce one.
fun gaddiCommitCount(): Int {
    val shallow =
        providers
            .exec { commandLine("git", "rev-parse", "--is-shallow-repository") }
            .standardOutput.asText
            .get()
            .trim() == "true"
    val count =
        providers
            .exec { commandLine("git", "rev-list", "--count", "HEAD") }
            .standardOutput.asText
            .get()
            .trim()
            .toIntOrNull() ?: 0
    require(!shallow) {
        "Shallow clone: git rev-list reports $count commits, so versionCode would be " +
            "${1 + count}. Set `fetch-depth: 0` on actions/checkout."
    }
    return count
}

fun gaddiMilestone(): Int =
    rootProject
        .file("MILESTONE")
        .takeIf { it.exists() }
        ?.readText()
        ?.trim()
        ?.toIntOrNull() ?: 1

val gaddiUtc = TimeZone.getTimeZone("UTC")
val gaddiNow = Date()
fun gaddiFmt(pattern: String): String =
    SimpleDateFormat(pattern).apply { timeZone = gaddiUtc }.format(gaddiNow)

val gaddiCommitCountVal = gaddiCommitCount()
val gaddiMilestoneVal = gaddiMilestone()
val gaddiVersionCodeBase = 1

extra["gaddiFingerprint"] =
    "${gaddiFmt("yyyy")}.${gaddiFmt("MM")}.${gaddiFmt("ww")}.$gaddiMilestoneVal.$gaddiCommitCountVal"
extra["gaddiMarketing"] = "${gaddiFmt("yyyy")}.${gaddiFmt("M")}.$gaddiMilestoneVal"
extra["gaddiBuildCode"] = gaddiVersionCodeBase + gaddiCommitCountVal

// Compose Desktop native packages (Dmg/Msi/Deb) reject the date-based MARKETING string — they need
// strict MAJOR.MINOR.BUILD with MAJOR/MINOR <= 255, BUILD <= 65535, validated at configure time.
// ponytail: MILESTONE.0.commitCount fits until MILESTONE>255 or commitCount>65535 (years away).
extra["gaddiDesktopPackageVersion"] = "$gaddiMilestoneVal.0.$gaddiCommitCountVal"
