# Wave 0 · Task 5 — GameScreen / GameViewModel Decomposition

> **STATUS: LANDED — verified in code 2026-08-05. DO NOT RE-EXECUTE.** All 8 checkboxes below
> are unticked and the "5,522-line GameScreen.kt" premise is stale — the split shipped.
> Evidence on `origin/main`:
>
> - `GameScreen.kt` is now **1,131 lines** (`git show origin/main:feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameScreen.kt | wc -l`).
> - Every Phase-B subpackage exists under `com.kursi.feature.game`: `board/`, `docks/`, `overlays/`, `coach/`, `status/` — plus `narrative/` and `session/` beyond what this plan named.
> - Phase C landed: `session/AutoPlayer.kt`.
> - `overlays/BeatGatePrompt.kt:56` `internal fun ContinueBeatPrompt`, called from `GameScreen.kt:364-365`.
> - The detekt baseline re-fingerprinted exactly as the "DISCOVERED CONSTRAINT" below predicted: `feature/game/detekt-baseline.xml` now carries **86** `<ID>` entries.
>
> Provenance: this plan originated in the repo (`docs/superpowers/plans/`); copied to
> `AgentHarness/plans/kursi/` on 2026-08-05 so both plan homes carry it.

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or executing-plans. Steps are `- [ ]`. Behaviour-preserving refactor — **no functional change**; the gate is "still compiles, all existing tests + screenshots unchanged."

**Goal:** Split the 5,522-line `GameScreen.kt` (43 `@Composable`, ~65 top-level `private` decls) into responsibility-scoped files, and relieve `GameViewModel` from detekt's function ceiling — so the remaining Wave 0 contract tasks (beat gate, motion, l10n) don't trip `TooManyFunctions`/complexity, and the screen becomes comprehensible + recomposition-tunable.

**Why first:** `GameViewModel` is already at detekt's 20-function ceiling (proven: Task 1's helper had to go top-level; Task 2's `awaitBeat` would trip it). Decomposition is the real fix, not threshold-raising.

## Global Constraints

- **Behaviour-preserving.** Zero logic change. Same package (`com.kursi.feature.game` and subpackages), same public API (`GameScreen`, `deriveGamePhase`, `recommendedTarget` stay callable from their current sites).
- **The `private` trap:** Kotlin top-level `private` = **file-scoped**. Moving a `private` decl to a new file breaks every cross-file reference. Strategy handles this explicitly (Step group A).
- **Verification gate (every commit):** `./gradlew :feature:game:jvmTest testAndroidHostTest detekt ktlintCheck` **and** `:cmp-desktop:renderScreens` (screenshots must render identically — this is the behaviour-preservation proof) **and** the replay-determinism tests (`ReplaySessionTest`, `ReplayAnnotationTest` run under jvmTest). Green before every commit. **Run the gate in the FOREGROUND to completion and confirm `BUILD SUCCESSFUL` — do not background it.** `ktlintCheck` is repo-wide; it catches import ordering in the new files.
- Conventional commits, ≤72-char subject, **no AI attribution** (hook rejects it). Stage by explicit paths, never `git add -A`.

---

## Strategy: promote-then-split (makes the risky part a separate, verifiable step)

**Phase A — promote visibility (one commit, no file moves).** In `GameScreen.kt`, change every top-level `private fun` / `private val` / `private const val` **AND `private` types** (`private data class`, `private sealed interface`, etc. — e.g. `LogTurnGroup`, `LogFlatRow`) to `internal`. An `internal` function that returns/takes a `private-in-file` type is a hard compile error, so types MUST be promoted alongside their functions. Rule: **promote all top-level `private` → `internal` in this file (funs, vals, consts, AND classes/interfaces).**

> **DISCOVERED CONSTRAINT (must be resolved before executing):** `feature/game/detekt-baseline.xml` baselines **91 findings, 21 of them CyclomaticComplexMethod in this file** (pre-existing, deliberately-suppressed complexity — `OpponentChipItem`=61, `CompactActionChip`=37, etc.). Detekt matches baseline entries by function signature + file path, so **any** visibility change or file move re-fingerprints them and resurfaces the findings → build fails. There is no way to split this file without **regenerating the detekt baseline** (`./gradlew :feature:game:detektBaseline`) afterward. This re-suppresses the SAME pre-existing debt at its new location — it is baseline *maintenance across a pure refactor*, not new-debt suppression (the refactor changes zero complexity). Every Phase A/B commit that changes signatures/locations is followed by a baseline regen + a `git diff` review confirming only file-path/signature fingerprints changed (no NEW rule types appear). **This requires sign-off (no-weakening-checks instinct).**

**Phase B — split into files (small commits, one responsibility group each).** Move the now-`internal` decls into new files under `com.kursi.feature.game.game/` subpackages, **same top package root** so no call site outside the file changes. After each group moves, run the gate (compile + tests + renderScreens). Group boundaries (from the current line map):

| New file | Decls (by current line anchors) | Responsibility |
|---|---|---|
| `board/FeltTable.kt` | `FeltTableSurface` (989), `FeltCenterTokens` (1651), `draw*` felt/emblem/pedestal helpers | table surface + Canvas draws |
| `board/OpponentArc.kt` | `OpponentArc` (1195), `OpponentChipItem` (1268), suspicion/claim chip helpers | opponent plates |
| `board/YourHand.kt` | `YourHandPanel` (2703) + card helpers | player hand |
| `docks/ActionDocks.kt` | `PickActionDock` (2957), `PickTargetDock` (3504), `ConfirmDock` (3563), `IdleDock` | action-selection docks |
| `docks/ReactionDocks.kt` | `ReactionDock` (3659), `LoseInfluenceDock` (3931), `ExchangeDock`, `InvestigatePeekDock`, `GameOverDock` | reaction/resolution docks |
| `overlays/Darbar.kt` | `DarbarPanel` (505), whisper/chit overlays | chat + coach chits |
| `overlays/Gazette.kt` | `NiyamGazette`, `SwearingInPrimer`, `HandoffGuard` | rules/onboarding/handoff |
| `overlays/GameLog.kt` | log drawer + terminal shell (2149) | Roznamcha log |
| `coach/CoachInsights.kt` | `recommendedTarget` (1619, already `internal`), `deriveSuspicion`, advice-badge helpers | coach annotations |
| `status/StatusSpine.kt` | `StatusSpineBar` (2605), `RecapRail` (2488) | status + recap |
| `GameScreen.kt` (remains) | `GameScreen` (145), `deriveGamePhase` (89), `GamePhase` types, `DesktopLayout` (811), `PhoneLayout` (1985) | entry + phase + layout orchestration |

**Phase C — GameViewModel relief (one commit).** Extract the paced-advance/auto-mode/spectator machinery from `GameViewModel` into a collaborator to drop below the 20-function ceiling. Minimal viable extraction: move `maybeAutoResolve`, `maybeAutoSpectate`, `playBestMove` bodies (and their `autoJob`/`spectateJob` fields) into a new `session/AutoPlayer.kt` class that the VM delegates to. This removes ≥3 member functions → VM under ceiling → **unblocks Task 2's `awaitBeat`.** Gate + commit. (If a smaller cut suffices — e.g. just moving `feedEventsToExperts` + `pauseFor` to top-level file funcs — prefer the smallest that clears the ceiling; verify with `:feature:game:detekt`.)

---

### Steps

- [ ] **A1** — cielara/grep the internal call graph is optional; the blunt-safe move is promote-all. In `GameScreen.kt`, replace top-level `^private fun ` → `internal fun `, `^private val ` → `internal val `, `^private const val ` → `internal const val `. Leave nested/local `private` untouched. (Do NOT touch `private` on class members — only top-level file decls.)
- [ ] **A2** — Gate: `./gradlew :feature:game:jvmTest testAndroidHostTest detekt ktlintCheck` → BUILD SUCCESSFUL. (Promotion alone may trip a detekt "UnusedPrivateMember"→now-internal rule; if a decl is genuinely unused, that's a pre-existing finding — leave `private` on it instead of promoting.)
- [ ] **A3** — `:cmp-desktop:renderScreens` → renders succeed, no screenshot semantic change.
- [ ] **A4** — Commit: `refactor(game): promote GameScreen top-level decls to internal (pre-split)`.
- [ ] **B1..Bn** — For each file group above (start with the largest/most-independent, e.g. `board/FeltTable.kt`): create the new file with `package com.kursi.feature.game.board` (or matching subpackage), move the decls, add imports. The moved decls are `internal` so callers in `GameScreen.kt` resolve via an `import com.kursi.feature.game.board.*`. After EACH group: run the full gate + renderScreens, then commit `refactor(game): extract <group> from GameScreen`.
- [ ] **C1** — Extract `AutoPlayer` (or minimal top-level funcs) from `GameViewModel`; delegate. Gate incl. `:feature:game:detekt` must show GameViewModel under 20 functions. Replay-determinism tests green.
- [ ] **C2** — Commit: `refactor(game): extract auto-play from GameViewModel (relieve function ceiling)`.

## Risks

- **Screenshot drift** = a real behaviour change slipped in. `renderScreens` after every group is the tripwire. If a baseline changes, STOP and diff — a decomposition must produce byte-identical renders.
- **Subpackage vs same-package:** moving to a subpackage (`.board`) requires an `import` in `GameScreen.kt`; moving to same package needs none. Subpackages are cleaner for the file tree — accept the imports (ktlintCheck orders them).
- **detekt UnusedPrivateMember on promote:** if promotion surfaces an unused decl, it was dead code — leave it `private` (keeps it file-local) or delete if provably unused (separate commit).
- **Do NOT one-shot this.** Each B-group is its own compile+test+render+commit cycle so a break is localized to one small move.
