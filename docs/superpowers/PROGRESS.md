# Gaddi Launch Overhaul — Progress / Resumption Note

**Branch:** `feat/launch-overhaul` (all work here; not yet merged to `main`).
**Spec:** [specs/2026-07-18-gaddi-launch-overhaul-design.md](specs/2026-07-18-gaddi-launch-overhaul-design.md) (rev 3). **Plans:** `plans/2026-07-18-*`.
**Last certified:** full hardened gate green (`:feature:game:jvmTest :core:designsystem:jvmTest :core:prefs:jvmTest :ai:jvmTest testAndroidHostTest detekt ktlintCheck :cmp-desktop:renderScreens :cmp-android:compileNoGmsDebugKotlin`), replay tests pass, ANALYST screenshots byte-unchanged where expected.

## Done ✅

**Wave 0 (foundation, certified):**
- `DensityLayer` FOCUS/GUIDED/ANALYST axis (ANALYST default) — prefs → app → ViewModel.
- Beat-gate state machine — `BeatTier`/`PendingBeat`/`tierFor`, `awaitBeat`, `GameAction.ContinueBeat`.
- `GaddiMotion` spring/duration tokens; compose string-resource scaffold.
- `GameScreen.kt` decomposition 5,522→1,021 lines (`board/ docks/ overlays/ coach/ status/ GamePhase.kt`); `GameViewModel` auto-play → `session/AutoPlayer.kt`.

**Wave 1 (all core tracks landed + visually verified via renderScreens):**
- **FOCUS mode real** — surfaces gate on `densityLayer`; clean board in FOCUS, coach in GUIDED, ANALYST byte-identical. `overlays/BeatHeadline.kt` + `overlays/BeatGatePrompt.kt` (tap-to-continue, a11y + reduced-motion).
- **Guided funnel** — interactive mechanic-at-a-time tutorial + `DensityLayer` graduation policy + first-run routing (Boot→Primer→funnel→FOCUS, prefs `seenFunnel`).
- **Art pipeline** — `core/designsystem` composeResources asset track + `art/GaddiArt.kt` `resolveArt()` fallback to Canvas glyphs (inert until slot added to `readySlots` + real file dropped). QA gate `docs/art/STYLE_LOCK.md`.
- **Online** — `server/` `BeatAckGate` bounded timeout (4s) so online matches don't stall; `ClientMessage.ContinueBeat` in `shared-protocol`. 5 deterministic virtual-time tests.
- **Intelligence** — `ai/MunshiNarrator.kt` + provider matrix (`TemplatedAiProvider` floor + BYOK/on-device tiers); narration upgrades the headline in place (templated-first, §8.6 guardrails: never in intent log / never mutates state / redacted prompts). Replay-deterministic.
- **Renderer** — real Rozha/Marcellus/DM Mono wired (root-cause: `GaddiType` was serving system-fallback to 500+ call sites); reduced-motion-aware spring juice on press/card/coin transitions; subtle warm key-light + vignette + real elevation shadows on felt.

**Visual QA (renderScreens, no emulator available in env):** `4p_focus` (clean FOCUS board), `4p_coach_action` (full ANALYST panel), `tutorial_coup` (funnel) all confirmed — real fonts + felt depth + Munshi headline present, density contrast correct, no regressions.

## Next ⏳

1. **On-device run** — no emulator installed here (only AVD defs). Install SDK emulator package + boot AVD (or connect a device), then build/install/drive to feel the **spring juice + gestures + haptics** (the one thing static renders can't verify — Renderer flagged the `Modifier.inspectable` press-spring + `CoinPill` bump).
2. **AGSL/Skia shader layer (§7.2)** — the deferred bleeding-edge graphics track. Spike per-target FIRST (AGSL on AGP-alpha, Skiko `RuntimeEffect`); verify on physical device (see `cmp-xcode26-sim-metal` caveat). Refs in `docs/resources.md` (ShaderX/Cloudy/Shady).
3. **Intelligence follow-ons** — natural-language coach + post-match debrief (§8.2), living-persona Darbar banter (§8.3), on-device SDK wiring (AICore/Gemini Nano, Apple Foundation Models — currently return null→templated).
4. **Online client-side beat wiring** — `OnlineGameAdapter.ContinueBeat` still inert; wire it to send `ClientMessage.ContinueBeat` + surface `pendingBeat` for online play so a human tap short-circuits the 4s server timeout.
5. **A11y sweep** (§9) · **platform polish** (§10, incl. real device iOS per `cmp-xcode26-sim-metal`) · **docs/screenshots/GIF regen + README rewrite** (§11, spec §12 phases 6–8).

## Orchestration gotchas learned

- **Hardened gate must compile the shells** — `:cmp-android:compileNoGmsDebugKotlin` catches DI/wiring/import breakage module tests miss.
- **Don't run two gradle builds on the same checkout concurrently** — a manual gate started while an orphaned agent gate was finishing collided on `ai/build/test-results/*.bin` (`NoSuchFileException`) — a false-negative. Wait for gradle idle, clean `build/test-results`, re-run exclusively.
- **Agent that dies mid-gate may leave work UNCOMMITTED but staged/present** — check `git status` before assuming loss; verify (clean exclusive gate) then commit with explicit paths.
- **`renderScreens` writes to gitignored `cmp-desktop/build/shots/`, NOT tracked `docs/screenshots/`** — visual changes never show in `git status docs/screenshots/`; view `build/shots/*.png` directly for visual QA; CI refreshes tracked baselines on push.
- **Worktree isolation branched from pre-Wave-0 base** — merge by taking new files explicitly (`git checkout <branch> -- <paths>`), not a blind `git merge`. Prefer serial-in-main for `feature/game`-touching tracks.
- Commits: explicit paths only (never `git add -A` — pre-existing WIP in `cmp-android/build.gradle.kts`, `gradle/libs.versions.toml`, `cmp-android/src/test/` stays unstaged); **no AI-attribution trailer** (pre-bash hook rejects it).
