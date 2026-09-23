# Kursi Launch Overhaul — Design Spec (rev 3)

**Date:** 2026-07-18 (rev 3 — resources folded in, licensing policy, free code-first design tooling)
**Status:** Approved direction; Wave 0 in execution
**Scope:** Everything for launch — comprehension, pacing, graphics, AI, accessibility, platform polish, online play, localization, store distribution, docs — across all four client targets (Android, iOS, desktop/JVM, web/Wasm) + server.
**Companion doc:** [docs/resources.md](../../resources.md) — the license-vetted external resources (shaders, juice technique, fonts, audio, references) referenced throughout.

---

## 1. Problem

Player feedback on the current build: *too hard to follow, difficult to crack, fast paced, too much info on screen, no direction, graphics not good enough.*

Root-cause analysis (verified in code):

- `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameScreen.kt` (5,522 lines) renders **simultaneously**: opponent role claims, suspicion pips, coach REAL/BLUFF badges, P(it-flies) odds pills, recommended-move stars, dossier chits, a 30-event teleprinter log, Darbar chat, a status spine, and a recap rail. It shows an expert's instrument panel to a first-time player.
- The bot round **auto-advances** (`GameViewModel.kt` `advanceJob`, delay tiers 1600/2400/4000 ms × `speedMultiplier`), so the board mutates on its own between the player's turns — the "fast paced / can't follow it" feel.
- Visuals are competent flat Canvas vector work with no depth, lighting, or tactility — "not good enough graphics" is the absence of materiality and juice, not the absence of bitmaps.
- Nothing on screen tells the player *what just happened and why it matters* in plain language.

None of this is a bug. It is density + self-advancing pacing + flat presentation. The fix is architectural, not cosmetic.

## 2. North star

> **Kursi should feel like operating a piece of confiscated government machinery to get away with a lie.**

Reference points: **Balatro** (tactility/juice on a humble card game), **Reigns** (diegetic, near-zero-chrome decisions), **Inscryption** (the interface is a physical table), **Blood on the Clocktower** digital (making a social read legible). Modern = Balatro's juice × Reigns' restraint × a material, lamplit world.

A bluffing game's job is to dramatize **hidden information and the social read**. The current UI shows a spreadsheet of suspicion; the new UI makes you *read people*.

## 3. Organizing spine: three density layers

One concept applied everywhere. Same game, revealed in three layers. Generalizes the existing `GameUiState.coachEnabled` boolean into:

```kotlin
enum class DensityLayer { FOCUS, GUIDED, ANALYST }
```

| Layer | Default for | On the board |
|---|---|---|
| **FOCUS** | First-timers; new global default | Whose turn · what just happened (one Munshi line, §8.1) · your hand · your legal actions. No pips, badges, odds, log, recap rail. |
| **GUIDED** | Mid-journey players | FOCUS + one plain-language suggestion at a time ("They claimed Neta — challenge if you think they're bluffing"). |
| **ANALYST** | Opt-in / graduated | Today's full instrument panel: suspicion pips, REAL/BLUFF badges, odds pills, dossier chits, teleprinter log, recap rail. Nothing deleted — it moves up a layer. |

Graduation FOCUS → GUIDED → ANALYST happens by playing (match count + `DecisionQuality` signals) or manually in Settings at any time. Every overlay composable gates on `DensityLayer`. The layer is persisted in `core/prefs`.

## 4. Three signature interactions

### 4.1 The Focus Pull (virtual camera)
The table gains depth and a virtual camera that pushes toward whoever is acting and racks the rest of the table into soft blur/scale falloff. The camera **is** the direction — it narrates whose beat it is. Implemented with `graphicsLayer` (scale, translation, blur) driven by the beat system (§5); anchored via the existing `TableAnchorRegistry`. Solves "fast", "too much on screen", and "no direction" in one move.

### 4.2 The Read (behavioral tells)
Suspicion stops being pips (in FOCUS/GUIDED) and becomes **performance**: opponent portraits behave — held breath, too-fast claim, over-confident lean — driven off the existing AI `BeliefModel` and persona traits. ANALYST keeps the numbers as backing. Tells are probabilistic and persona-dependent (a good liar has fewer tells) so they inform without solving the game.

### 4.3 The Tactile Hand
The player's two cards are physical objects: **hold-to-peek** (cards stay face-down until pressed, like palming real cards), spring-physics deal/flip, felt parallax on device tilt (gyro where available). Diegetic UI: dossier, ledger, and cards are things on a table, not stacked panels.

## 5. Workstream B — Pacing

- **Beat gate:** in FOCUS/GUIDED, the bot round pauses on each *meaningful* beat (claim, challenge, block, reveal, influence loss) and waits for tap-to-continue. Trivial beats (income tick) flow through. Built on the existing `pauseFor` tier classification — a tier maps to "wait for ack" instead of a timed delay.
- **Speed settings:** SLOW/NORMAL/FAST become `AUTO` speeds (today's behavior, ANALYST default). Tap-to-continue is the newcomer default. Desktop: Space = continue.
- **Telegraphing:** every beat renders as "X is about to act" → action → outcome, synced with the Focus Pull, so nothing resolves before the player registers it.

## 6. Workstream A — Comprehension architecture

- **`GameScreen.kt` decomposition** (prerequisite for everything): split into `board/`, `docks/`, `overlays/`, `coach/`, `camera/` packages inside `feature/game`. Run `cielara query` on `GameScreen.kt` and `GameViewModel.kt` before cutting. `@Immutable` / `ImmutableList` stability pass during the split; recomposition counts checked with the compose-performance-audit skill before/after.
- **"What's happening" headline:** single calm sentence of the current beat (Munshi-written when AI available, templated otherwise), replacing the recap rail in FOCUS.
- **Guided funnel:** rework `TutorialScreen` into a mechanic-at-a-time interactive sequence (claim → challenge → block → coup → exchange), each beat playable against a scripted bot, feeding into the first real match. First-run routes: Boot → Primer → funnel → first match in FOCUS.
- **Progressive bottom sheets** replace stacked overlays (Gazette, dossier, settings-in-game) on phone; side panels on ≥840dp.

## 7. Workstream C — Graphics: layered material renderer

Not flat Canvas, not plain bitmaps — a compositor:

1. **Canvas base** — existing felt/guilloché/emblem drawing, deepened (lighting gradient, vignette, warm key light: the lamplit-desk look).
2. **AGSL / Skia runtime shader layer** — film grain, felt weave, brass specular that sweeps with device tilt, bloom on hero beats. `expect/actual` shader host per platform (Android AGSL / Skiko `RuntimeEffect` elsewhere); graceful no-shader fallback path (web/older devices) that must look intentional, not broken. **Reference/absorb** (docs/resources.md): [ShaderX](https://github.com/Debanshu777/ShaderX) for the exact AGSL+Skia expect/actual split, [Cloudy](https://github.com/skydoves/Cloudy) (Apache-2.0, candidate dependency) for GPU-blur-with-CPU-fallback powering the Focus-Pull bokeh and the no-shader path, [Shady](https://github.com/drinkthestars/shady) for grain/texture passes.
3. **Particle layer** — coin trails, stamp-ink spray, dust motes; capped counts, object-pooled.
4. **Asset layer** — new `composeResources` art track: 10 persona portraits, 6 role card faces, hero-moment art (KURSI crest, tipped chair). **AI-generated art is the launch art** (§9): produced in-style ("License Raj Deco → Sarkari Noir"), passed through a style-lock QA gate (palette, line weight, CVD pattern channels embedded in card frames) before acceptance. Commissioned art remains a post-launch drop-in option. WebP with per-density variants; total asset budget ≤ 8 MB per target.
5. **Moment overlays** — existing `MomentHost`/`ActionMomentOverlay` system, extended with the new beats; every new moment gets a `StaticMomentFrame` reduced-motion end-state.

Plus: full wiring of the bundled Rozha One / Marcellus / DM Mono (removing the Serif/Monospace fallbacks in `KursiType`), a spring-physics motion-token set in `core/designsystem` (durations/damping named, not scattered), a **distinct haptic signature per action class** (Android `HapticFeedback`/Vibrator, iOS Core Haptics via expect/actual; silent no-op on desktop/web), and a **CC0 sound layer** (stamp-slam / coin / card-deal beats) sourced per docs/resources.md ([Freesound](https://opengameart.org/content/cc0-sound-effects) CC0 / [Kenney](https://kenney.itch.io/kenney-game-assets) / [Pixabay](https://pixabay.com/sound-effects/search/cc0/)), gated by the existing `soundEnabled` pref. The juice design bible is the [Balatro feedback breakdown](https://blakecrosley.com/guides/design/balatro) — the layered stack (animation → particles → screen → audio → haptics) applied to every beat.

## 8. Workstream G — Intelligence & AI

Kursi already ships ISMCTS (`ai/IsmctsSearch.kt`), `BeliefModel`, 10 personas, `MoveAdvisor`, and a provider-agnostic LLM layer (`OnDeviceAiProvider` expect/actual + cloud `AiProvider` BYOK). This workstream fires that loaded gun.

### 8.1 The Munshi (AI narrator)
A diegetic court-scribe turns engine events into in-character plain language: *"Bahenji stamped the Neta's seal for tax — bold; she's been silent three turns."* Replaces the log in FOCUS. Prompted with the redacted `PlayerView` + recent events only (never hidden cards).

### 8.2 Natural-language coach & debrief
The GUIDED "why?" affordance and post-match "How did I lose?" route to the LLM, **grounded on the actual ISMCTS eval and belief state** — the model explains numbers the engine computed; it cannot invent board state. Post-match debrief uses `MatchDecisionTally` to name the 2–3 pivotal decisions.

### 8.3 Living personas (Darbar table-talk)
Bots banter, taunt, and react in-character; a bluffing bot talks differently from an honest one — table-talk is a tell channel feeding The Read (§4.2). Routed through the existing `SocialDirector` seam.

### 8.4 Adaptive difficulty & dynamic tutorial
`DecisionQuality` feeds bot aggression and tutorial pacing. Pure engine-side heuristics (no LLM required).

### 8.5 Provider matrix — ready for every user type

| Tier | Provider | User |
|---|---|---|
| 1 | **On-device LLM** via `OnDeviceAiProvider` (Android AICore/Gemini Nano; iOS Apple Foundation Models; desktop local runtime if present) | Privacy-first / offline users — zero setup |
| 2 | **BYOK cloud** via `AiProvider` (Anthropic, OpenAI, Gemini endpoints; key stored per-platform secure storage — Keystore / Keychain; never in prefs plaintext) | Power users wanting best quality |
| 3 | **Templated strings** (deterministic, localizable) | Everyone else / AI-off / web-no-provider — the game is 100% playable and legible with AI fully off |

Selection: auto-detect tier 1 → fall back to tier 3; tier 2 is explicit opt-in in Settings. Every AI surface individually toggleable.

### 8.6 Hard guardrails
- The deterministic engine remains the **sole source of truth**. AI output never enters `humanIntentLog`, never mutates `GameState`, never gates a legal action.
- Byte-for-byte replay (`seed + humanIntentLog`) is untouched; AI text is regenerated or omitted on replay, never persisted into the replay record.
- All prompts are built from **redacted** views (`Engine.redact`) — the LLM never sees hidden cards, so it cannot leak them.
- Latency rule: AI responses render when ready and never block a beat; the templated string renders first and upgrades in place.

## 9. Art policy (revised)

AI-generated art is **launch art**, not placeholder. Acceptance pipeline: generate in-style → style-lock QA gate (brand palette adherence, line-weight consistency, role CVD `RoleFramePattern` embedded in every card frame, contrast check) → asset track. Rejected pieces regenerate; nothing ships unreviewed. Commissioned replacement is a post-launch option, not a launch dependency.

### 9.1 Licensing & credits policy (governs every external asset/code)

The full vetted list is [docs/resources.md](../../resources.md). Enforced rules:

- **Ship only** CC0, MIT, Apache-2.0, OFL. AI-generated art (own work) and original Canvas/vector are unrestricted.
- **CC-BY** is allowed only with an entry in a new in-app **Credits** screen (Settings → Credits), listing asset, author, source, license. Adding a CC-BY asset without its credit line is a review rejection.
- **GPL / any copyleft** art or code is **quarantined** — never bundled (would infect this proprietary game). Study-only.
- **Fonts:** OFL fonts may be embedded freely (the license forbids selling the font itself, not shipping it in an app). Devanagari (Tiro Devanagari Hindi, Hind) + the existing Latin trio are OFL.
- **Per-clip verification:** Freesound/OGA mix licenses per item — each absorbed asset records its license in a `THIRD_PARTY.md` manifest at the asset track root; CI fails the build if an asset lacks a manifest entry.
- **Code references** (ShaderX, Cloudy, Shady, treason): copying any snippet requires the source repo's license be permissive and recorded; otherwise they are learning references only.

## 10. Workstream D — Accessibility

Executed with the android-accessibility + adaptive skills; applies to all new and existing surfaces:

- Semantics/`contentDescription` on every meaningful element; merged semantics per opponent plate ("Bahenji, 2 influence, 4 coins, claims Neta").
- Logical traversal order (turn owner → board → your hand → actions); TalkBack + VoiceOver passes on device.
- Touch targets ≥ 48dp; contrast audit against the Deco palette; dynamic type / font-scale support to 200%.
- CVD: keep the Okabe-Ito role palette + `RoleFramePattern` non-color channels; verify in the new asset art too.
- Reduced motion: every new moment/camera/parallax/shader effect honors `reducedMotion` via `StaticMomentFrame` equivalents; the Focus Pull degrades to a highlight ring, not a camera move.
- Beat gate is itself an a11y feature: nothing time-critical, no information that scrolls away.

## 11. Workstream E — Platform-specific enhancements

- **Android** (`cmp-android`): edge-to-edge, predictive back, haptic signatures, adaptive layouts (phone/foldable/tablet — multi-pane ≥840dp via the adaptive skill), ChromeOS keyboard support, gyro parallax.
- **iOS** (`cmp-ios`/`iosApp`): safe-area handling, swipe-back interop, Core Haptics, ProMotion-friendly animation, CoreMotion parallax. `CADisableMinimumFrameDurationOnPhone` already present — keep. **Runtime verification on a physical device only** (CMP/Skiko crashes on Xcode-26 simulators — known upstream issue; do not chase it).
- **Desktop** (`cmp-desktop`): keyboard shortcuts (Space = continue, 1–8 = actions, L = log, Esc = menu), hover states/tooltips on all interactive elements, min-window-size, proper resize behavior.
- **Web** (`cmp-web`): responsive breakpoints, keyboard nav + focus-visible, no-shader fallback verified, load-size budget for the asset track.

## 12. Workstream H — Online play launch polish

The Ktor authoritative server and `OnlineHubScreen` exist; they get launch-quality treatment:

- Online table adopts the same density layers, beat gate (per-client ack with server timeout so one player can't stall the table), Focus Pull, and renderer as solo play — one `GameScreen`, no fork.
- Lobby/hub UX pass: join/host flows aligned with the new design system, connection-state surfacing (reconnect banner, seat-held grace), spectator polish.
- Server: no protocol redesign; only what the shared UI needs (beat-ack timing metadata) plus a load sanity pass. AI features remain client-side and off by default in online play except the Munshi (redacted-view narration is safe per §8.6).

## 13. Workstream I — Localization

- Externalize all user-facing strings to CMP string resources (currently hardcoded); templated AI tier-3 strings included.
- Launch locales: **en**, **hi**, and the game's native **Hinglish voice** as its own locale variant (the brand's register — "Khokha", "Gaddi" — is already Hinglish; en is the accessibility floor, hi the reach play).
- Devanagari type for `hi` (OFL, free commercial, per docs/resources.md): [Tiro Devanagari Hindi](https://fonts.google.com/specimen/Tiro+Devanagari+Hindi) (display — Murty Classical Library origin, on-theme) + [Hind](https://fonts.google.com/specimen/Hind) (UI body). Bundled alongside the existing Latin trio in `core/designsystem` composeResources.
- LLM prompts (Munshi/coach) carry the locale so AI output matches the player's language; tier-3 templates are the translation source of truth.
- Localization QA via the screenshot harness: fixture pass per locale to catch truncation/overflow.

## 14. Workstream J — Launch & distribution

- **Store listings:** Play Store + App Store metadata, feature graphics, and store screenshots generated from the screenshot pipeline (per-locale via §13); web build gets a landing page shell; desktop distributed via GitHub releases.
- **Release automation:** tag-triggered release flow via the versioned-release skill (changelog, artifacts, scrub gate).
- **Monetization: free at launch, no IAP/ads.** This is the single flagged product decision in this spec — mechanics (if ever) are a post-launch ruling for Siddharth; nothing in this plan builds toward or against any particular model.

## 15. Workstream F — Screenshots, GIFs, READMEs

- Extend `buildFixtures()` in `cmp-desktop/src/jvmMain/kotlin/com/kursi/desktop/Screenshots.kt`: FOCUS/GUIDED/ANALYST variants, guided-funnel beats, beat-gate states, Munshi narration, new art, online flows, per-locale fixtures, phone/tablet/desktop widths, reduced-motion frames.
- Regenerate all baselines (`:cmp-desktop:renderScreens` → `docs/screenshots/`), restitch flow GIFs (`scripts/make_flow_gifs.sh` — add flows: `focus_mode`, `graduation`, `tap_to_continue`, `munshi`, `the_read`, `online`); CI `screenshots.yml` continues to auto-commit.
- Capture **real per-platform** screenshots/recordings (Android device/emulator, iOS physical device, desktop, web) for a platform matrix section; store-listing assets derive from the same captures (§14).
- Rewrite `README.md` + module READMEs around the three-layer story with GIFs inline (beautify-github-readme skill); update `docs/brand/BRAND.md` with the Sarkari Noir evolution + motion/haptic tokens.

## 16. Timeline — parallel wave model (fastest path)

The serial 8-phase ladder is replaced by **4 waves**. Inside a wave, tracks run in parallel — each track in its **own git worktree** (parallel agents must never share a checkout), merged behind a per-track verification gate. Multi-agent Workflow orchestration throughout: Sonnet volume execution, Opus orchestration, ensemble+judge for visual direction picks, generator–critic for a11y and art QA.

**Design tooling is free and code-first** (no paid Figma — Dev Mode is paid-only, unavailable on the Starter plan): the design system is built directly in `core/designsystem` (the source of truth), mockups come from the `:cmp-desktop:renderScreens` harness + Compose `@Preview`, and concept mockups from agent Artifact tooling. Figma stays read-only/optional. Each track that absorbs an external resource (docs/resources.md) runs a **license-verification step first** — CC0/MIT/Apache/OFL only into the ship; CC-BY → Credits screen; GPL quarantined.

**Wave 0 — Foundation (serial; everything depends on it).**
`GameScreen.kt`/`GameViewModel.kt` decomposition + `DensityLayer` scaffolding + beat-gate state machine + string externalization scaffolding (so parallel tracks write externalized strings from day one) + motion-token stubs. Cielara dependency mapping first. This wave is deliberately small and lands fast — it is the merge base for every track.

**Wave 1 — Parallel build (6 tracks, independent seams):**

| Track | Content | Primary modules |
|---|---|---|
| 1 | Pacing + telegraphing + Focus Pull camera (§5, §4.1) | `feature/game` |
| 2 | Renderer: shaders, particles, lighting, fonts, motion tokens, Tactile Hand (§7, §4.3) | `core/designsystem`, `feature/game/board` |
| 3 | Intelligence: provider matrix, Munshi, coach/debrief, personas, The Read data feed (§8, §4.2) | `ai`, `core/prefs` |
| 4 | Guided funnel + graduation + progressive sheets (§6) | `cmp-shared/screen`, `feature/game` |
| 5 | Art production: generation → style-lock QA → asset track (§9) | `core/designsystem` resources |
| 6 | Online polish: lobby UX, beat-ack protocol metadata, reconnect surfacing (§12) | `server`, `cmp-shared`, `shared-protocol` |

Cross-track contracts are frozen in Wave 0 (DensityLayer API, beat/ack state machine, motion tokens, asset naming) so tracks don't block each other. Tracks 1+2 share `feature/game` — their file boundaries (camera/ vs board/) are part of the Wave 0 split precisely so they don't collide.

**Wave 2 — Integration & hardening (4 platform lanes + 2 sweeps in parallel):**
Android / iOS / desktop / web platform-polish lanes (§11) each on its own target, plus the a11y sweep (§10) and localization fill (§13) running across the merged result. Every target **run on real runtime** (iOS on physical device), not just compiled.

**Wave 3 — Launch (parallel where possible):**
Fixtures + baselines + GIFs + READMEs + brand doc (§15) ∥ store listings + landing shell (§14) → release automation + tag (§14). Docs capture the finished state, so this wave is last by nature, but its internal jobs run concurrently off the same capture set.

**Estimated shape** (agent-executed, calendar time dominated by verification + Siddharth's review gates, not typing): Wave 0 ≈ 1–2 days · Wave 1 ≈ 3–5 days (parallel) · Wave 2 ≈ 2–3 days · Wave 3 ≈ 1–2 days → **~7–12 working days** end to end, vs ~4+ weeks serial. The critical path is Wave 0 → Track 1/2 merge → platform lanes → captures; everything else hangs off it with slack.

**Gates (unchanged, per track/lane):** `assembleDebug` + unit tests + host-test aggregate (`testAndroidHostTest`) + detekt/ktlint + replay determinism tests + screenshot render. ralph-loop drives pass/fail regen gates; merges are serialized (one track merges at a time — git operations never run concurrently on a shared checkout).

## 17. Risks & mitigations

- **Bleeding-edge toolchain** (Kotlin 2.4.20-Beta1, CMP 1.12.0-beta02, AGP alpha): AGSL/RuntimeEffect and gyro APIs spiked at the start of Track 2 before the renderer design is committed.
- **Parallel-merge conflicts**: mitigated by Wave 0 contract freeze, per-track worktrees, file-boundary ownership, serialized merges. Track 1/2 boundary inside `feature/game` is the highest-risk seam — reviewed at Wave 0.
- **AI latency/cost**: never blocks gameplay (§8.6); templated tier is the floor everywhere.
- **Web performance**: shader layer and particles capability-gated; web defaults to the no-shader path until profiled.
- **Determinism**: replay tests run in every gate; any diff touching `engine/` from a non-engine track is rejected by design.
- **Online beat-gate abuse** (player never taps): server-side ack timeout auto-continues; timeout value tuned in Track 6.
- **Timeline honesty**: the 7–12-day shape assumes review gates turn around same-day; Siddharth's review latency is the real critical path.

## 18. Out of scope (true remainder)

- Monetization **mechanics** (IAP/ads/pricing) — flagged one-way-door decision, §14; launch is free.
- Commissioned art replacement — post-launch drop-in option, §9.
- New game modes / rule variants beyond what exists.
