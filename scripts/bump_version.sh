#!/usr/bin/env bash
# Single-source version bump for Gaddi.
#
# Three-tier model (see docs/RELEASE.md):
#   FINGERPRINT = YYYY.0M.0W.<MILESTONE>.<commitCount>  (tag, release title, BuildConfig, debug suffix)
#   MARKETING   = YYYY.M.<MILESTONE>                    (Android versionName, iOS CFBundleShortVersionString)
#   BUILDCODE   = 1 + commitCount                       (Android versionCode, iOS CFBundleVersion)
# commitCount is `git rev-list --count HEAD` — always live, never hand-typed. MILESTONE is the only
# file this script increments; VERSION/BUILD_NUMBER are kept for changelog/legacy reference only.
#
# Usage: scripts/bump_version.sh --major|--minor|--patch|--milestone|--commit
#   --major/--minor/--patch  bump the informational VERSION file (semver, human-facing changelog only)
#   --milestone               MILESTONE += 1 (this is what actually moves MARKETING/FINGERPRINT)
#   --commit (or no args)     recompute + print derived values, no file writes
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$ROOT/VERSION"
BUILD_NUMBER_FILE="$ROOT/BUILD_NUMBER"
MILESTONE_FILE="$ROOT/MILESTONE"

[ -f "$VERSION_FILE" ]      || echo "1.0.0" > "$VERSION_FILE"
[ -f "$BUILD_NUMBER_FILE" ] || echo "0"     > "$BUILD_NUMBER_FILE"
[ -f "$MILESTONE_FILE" ]    || echo "1"     > "$MILESTONE_FILE"

print_derived() {
  local commit_count year month0 week0 monthN milestone fingerprint marketing buildcode
  commit_count="$(git -C "$ROOT" rev-list --count HEAD)"
  year="$(date -u +%Y)"; month0="$(date -u +%m)"; week0="$(date -u +%V)"
  monthN="$(date -u +%-m 2>/dev/null || date -u +%m | sed 's/^0//')"
  milestone="$(tr -d '[:space:]' < "$MILESTONE_FILE")"
  fingerprint="${year}.${month0}.${week0}.${milestone}.${commit_count}"
  marketing="${year}.${monthN}.${milestone}"
  buildcode=$((1 + commit_count))
  echo "FINGERPRINT: $fingerprint"
  echo "MARKETING:   $marketing"
  echo "BUILDCODE:   $buildcode"
}

case "${1:-}" in
  --major|--minor|--patch)
    version="$(tr -d '[:space:]' < "$VERSION_FILE")"
    IFS='.' read -r major minor patch <<< "$version"
    case "$1" in
      --major) major=$((major + 1)); minor=0; patch=0 ;;
      --minor) minor=$((minor + 1)); patch=0 ;;
      --patch) patch=$((patch + 1)) ;;
    esac
    new_version="${major}.${minor}.${patch}"
    echo "$new_version" > "$VERSION_FILE"
    echo "VERSION bumped: $version → $new_version (informational only; MARKETING is date+MILESTONE based)"
    echo "Commit with: git commit -am \"chore: bump VERSION to $new_version\""
    ;;
  --milestone)
    milestone="$(tr -d '[:space:]' < "$MILESTONE_FILE")"
    new_milestone=$((milestone + 1))
    echo "$new_milestone" > "$MILESTONE_FILE"
    echo "MILESTONE bumped: $milestone → $new_milestone"
    print_derived
    echo "Commit with: git commit -am \"chore: bump milestone to $new_milestone\""
    ;;
  --commit|"")
    print_derived
    ;;
  *)
    echo "usage: $(basename "$0") --major|--minor|--patch|--milestone|--commit" >&2
    exit 1
    ;;
esac
