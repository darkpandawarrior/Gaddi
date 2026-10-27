# Decisions log

Settled choices for this repo, one line each, so a new session does not re-open them. Read before proposing an architectural change, and append a line when a decision is made.

- 2026-07-17: Version numbers are computed at build time from MILESTONE plus the git commit count, and only MILESTONE is bumped by hand. Why: hand-typed build numbers drift; VERSION is informational and BUILD_NUMBER is kept only so old tooling does not break. (source: cde2969, docs/RELEASE.md)
- 2026-09-08: The AI search driver uses a startCoroutine-based synchronous driver, not runBlocking. Why: runBlocking has no wasmJs implementation, and the search only suspends through no-op ensureActive checks, so the driver reproduces the old behavior on every target without a platform split. (source: 77bf7e4)
- 2026-09-22: Dead function parameters are deleted, and small detekt categories are cleared in code, instead of being added to the baseline. Why: the unused values were computed at call sites for nothing, and in the shared design-system module that dead surface reaches every consumer. (source: 577b662, ccf0aa1)
- 2026-09-22: The baseline push in CI uses a hard reference to the secret with no `|| github.token` fallback. Why: a fallback only engages on an unset secret, while a wrong value is truthy and wins, and a GITHUB_TOKEN push would silently rebuild the deadlock that #148 removed. (source: 6306340)
- 2026-09-23: The app is renamed from Kursi to Gaddi, but packages and Gradle modules keep the `kursi` name. Why: the rename covers product-facing names, docs and artifacts; the code identifiers were left alone. (source: the project guidelines, 5f3af4a, 07ee1ab)
- 2026-10-09: Gaddi is a standalone design system and does not consume the kmp-toolkit tokens. Why: DESIGN.md defines its own Sarkari Noir direction with locked role hues, so the toolkit tokens are not used. (source: 07ee1ab, DESIGN.md)
