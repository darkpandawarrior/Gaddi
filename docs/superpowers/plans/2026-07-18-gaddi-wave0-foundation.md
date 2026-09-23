# Kursi Launch Overhaul — Wave 0 (Foundation) Implementation Plan

> **STATUS: LANDED — verified in code 2026-08-05. DO NOT RE-EXECUTE.** All 32 checkboxes below
> are unticked, but every task shipped via PR #24 (merge `86f6b039`, 2026-07-19, branch
> `launch-overhaul-integration`). Evidence on `origin/main`:
>
> - **Task 1 (DensityLayer):** `feature/game/src/commonMain/kotlin/com/kursi/feature/game/DensityLayer.kt`; `core/prefs/.../AppPrefs.kt:368` `val densityLayerFlow`; wired at `cmp-shared/.../KursiApp.kt:550-551`; `DensityLayerTest.kt` in `commonTest`.
> - **Task 2 (Beat gate):** `feature/game/.../BeatGate.kt`; `GameViewModel.kt:163` `private var beatAck`, `:430` `is GameAction.ContinueBeat -> beatAck?.complete(Unit)`; `GameAction.kt:94` `data object ContinueBeat`; `BeatGateTest.kt` in `commonTest`.
> - **Task 3 (Motion tokens):** `core/designsystem/.../KursiMotion.kt` + `KursiMotionTest.kt`.
> - **Task 4 (String externalization):** landed as **scaffold only** — `core/designsystem/src/commonMain/composeResources/values/strings.xml` has exactly one `<string>` entry and there is exactly one `stringResource()` call site repo-wide (`SettingsScreen.kt`). Finishing externalization is FRONTIER item 4 — new bounded work, not a re-run of this plan.
> - **Task 5 (Decomposition):** split out into `2026-07-18-kursi-wave0-task5-decomposition.md`, also LANDED — see that file's banner.
>
> **Gate correction 2026-08-05:** the gate is `:feature:game:jvmTest` (and `:<module>:jvmTest`
> per module). The harness copy previously prescribed `testDebugUnitTest` at 10 sites; running
> that task does not exercise these `commonTest` suites. All sites corrected to `jvmTest`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the serial foundation that freezes the cross-track contracts (density layers + beat gate) and stubs (motion tokens, string externalization) every Wave 1 parallel track builds against — additively, with zero behavior change to existing play.

**Architecture:** Extend the existing MVI seams rather than rewrite. `GameUiState.coachEnabled: Boolean` is generalized by *adding* a `DensityLayer` axis (defaulting to `ANALYST` = current full behavior, so no screen or test changes meaning). The paced bot loop in `GameViewModel.submitIntent`'s `advanceJob` gets a beat-gate: in `FOCUS`/`GUIDED` its `delay(pauseFor(...))` becomes a suspend-until-ack; in `ANALYST` (and when no layer flow is wired — tests/render harness) it stays exactly as today. Motion tokens and string externalization are scaffolded so the six parallel tracks write against them from line one.

**Tech Stack:** Kotlin Multiplatform 2.4.20-Beta1, Compose Multiplatform 1.12.0-beta02, kotlinx.coroutines 1.11.0, multiplatform-settings 1.3.0, JUnit (jvmTest). Modules touched: `feature/game`, `core/prefs`, `cmp-shared`, `core/designsystem`.

## Global Constraints

- **Additive only.** No existing test's expected behavior changes. `ANALYST` is the default `DensityLayer`; a null density-layer flow (tests, render harness) means "not gated" = today's timed pacing.
- **StateFlow + collectAsStateWithLifecycle**, no LiveData. **ImmutableList / persistentListOf** for any new list param passed to a composable.
- **Design tokens, not hardcoded dp.** New spacing/shape/motion via `core/designsystem` tokens.
- **No new dependency** for what a few lines do. `feature/game` must NOT gain a `core:prefs` dependency — the VM receives flows/callbacks (existing pattern, `GameViewModel.kt:60-107`).
- **Determinism untouched.** Nothing in this wave touches `engine/`. Replay (`seed + humanIntentLog`) is unaffected — the beat gate only paces *presentation* of already-computed bot steps.
- **Verification gate (every task):** `./gradlew :feature:game:jvmTest` (+ the module owning the change) **and** `./gradlew testAndroidHostTest` for any `commonMain` change to a library module, plus `./gradlew detekt ktlintCheck`. Commit only on green.
- **Commit style:** conventional commits, ≤72-char subject, **no AI attribution trailer** (repo hook rejects it).

---

### Task 1: DensityLayer axis (additive, back-compat)

Introduce the three-layer density model as a new field alongside `coachEnabled`, plumbed through prefs → app → ViewModel exactly like `coachEnabled` is today. Nothing gates on it yet (overlay migration is Wave 1 Track 4); this task only freezes the type + the wiring.

**Files:**
- Create: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/DensityLayer.kt`
- Modify: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameUiState.kt` (add field, ~line 108)
- Modify: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameViewModel.kt` (constructor param + init collector, near lines 60–107 and 332–348)
- Modify: `core/prefs/src/commonMain/kotlin/com/kursi/core/prefs/AppPrefs.kt` (String-backed pref + flow, following the `language` String pattern at lines ~270 & 318)
- Modify: `cmp-shared/src/commonMain/kotlin/com/kursi/shared/KursiApp.kt` (map prefs String ↔ DensityLayer, pass typed flow to the VM — mirror the `coachEnabledFlow` wiring)
- Test: `feature/game/src/commonTest/kotlin/com/kursi/feature/game/DensityLayerTest.kt`

**Interfaces:**
- Produces:
  - `enum class DensityLayer { FOCUS, GUIDED, ANALYST }` with `companion object { fun fromName(name: String?): DensityLayer }` (unknown/null → `ANALYST`).
  - `GameUiState.densityLayer: DensityLayer` (default `ANALYST`).
  - `GameViewModel(densityLayerFlow: StateFlow<DensityLayer>? = null, onDensityLayerChange: ((DensityLayer) -> Unit)? = null, …)` — new params appended (defaults keep every existing call site compiling).
  - `AppPrefs.densityLayer: DensityLayer`-shaped access exposed as `densityLayerName: String` + `densityLayerFlow: StateFlow<String>` (core/prefs stays enum-free).
- Consumes: nothing from other tasks.

- [ ] **Step 1: Write the failing test**

```kotlin
// DensityLayerTest.kt
package com.kursi.feature.game

import kotlin.test.Test
import kotlin.test.assertEquals

class DensityLayerTest {
    @Test
    fun fromName_maps_known_values() {
        assertEquals(DensityLayer.FOCUS, DensityLayer.fromName("FOCUS"))
        assertEquals(DensityLayer.GUIDED, DensityLayer.fromName("GUIDED"))
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName("ANALYST"))
    }

    @Test
    fun fromName_unknown_or_null_defaults_to_analyst() {
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName(null))
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName("garbage"))
    }

    @Test
    fun uiState_defaults_to_analyst() {
        // ANALYST default preserves today's full-instrument behavior.
        assertEquals(DensityLayer.ANALYST, sampleUiState().densityLayer)
    }
}
```

`sampleUiState()` — reuse an existing test fixture helper if the module already has one; otherwise construct a minimal `GameUiState` from an existing test's pattern (search `commonTest` for `GameUiState(` to copy the smallest existing constructor call).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:game:jvmTest --tests "com.kursi.feature.game.DensityLayerTest"`
Expected: FAIL — `DensityLayer` unresolved.

- [ ] **Step 3: Create the enum**

```kotlin
// DensityLayer.kt
package com.kursi.feature.game

/**
 * Progressive-disclosure spine (spec §3). The same game revealed at three densities:
 *  - FOCUS   — whose turn · one plain-language line · your hand · your actions. Nothing else.
 *  - GUIDED  — FOCUS + one suggestion at a time.
 *  - ANALYST — the full instrument panel (today's screen). Default; preserves existing behavior.
 *
 * Persisted as a String in AppPrefs (core/prefs stays enum-free); the app layer maps String ↔ enum.
 */
enum class DensityLayer {
    FOCUS,
    GUIDED,
    ANALYST,
    ;

    companion object {
        /** Unknown / null → ANALYST (safe default = today's full behavior). */
        fun fromName(name: String?): DensityLayer = entries.firstOrNull { it.name == name } ?: ANALYST
    }
}
```

- [ ] **Step 4: Add the field to `GameUiState`** (after `lifetimeCoins`, ~line 108):

```kotlin
    /**
     * PROGRESSIVE-DISCLOSURE layer (spec §3). Mirrors [AppPrefs.densityLayer]. Default ANALYST =
     * today's full-instrument screen. Overlays migrate to gate on this in Wave 1 Track 4; adding it
     * here changes no rendering yet.
     */
    val densityLayer: DensityLayer = DensityLayer.ANALYST,
```

- [ ] **Step 5: Add VM constructor params + init collector**

Append to the constructor (after `language`, ~line 106):

```kotlin
    /** PROGRESSIVE-DISCLOSURE layer flow (spec §3), sourced from AppPrefs by the app. Null = ANALYST. */
    private val densityLayerFlow: StateFlow<DensityLayer>? = null,
    /** Persist a density-layer change (e.g. graduation / settings). Null = in-memory only. */
    private val onDensityLayerChange: ((DensityLayer) -> Unit)? = null,
```

In `init { … }` (after the `turnSpeedFlow` collector, ~line 347) add:

```kotlin
        if (densityLayerFlow != null) {
            coroutineScope.launch {
                densityLayerFlow.collect { layer ->
                    _state.value = _state.value?.copy(densityLayer = layer)
                }
            }
        }
```

And stamp the initial layer where the initial UI is emitted in `startGame` (line 594) — change the `.copy(...)`:

```kotlin
        emitState(
            initialUi.copy(
                coachEnabled = coachEnabledFlow?.value ?: true,
                densityLayer = densityLayerFlow?.value ?: DensityLayer.ANALYST,
            ),
        )
```

- [ ] **Step 6: Add the String-backed pref to `AppPrefs`**

Add key + accessor + flow following the `language` pattern:

```kotlin
        // in companion object, with the other KEY_ constants:
        private const val KEY_DENSITY_LAYER = "density_layer"
```
```kotlin
    /** Density layer name ("FOCUS"|"GUIDED"|"ANALYST"); default ANALYST for existing installs. */
    var densityLayerName: String
        get() = settings.getString(KEY_DENSITY_LAYER, "ANALYST")
        set(v) {
            settings.putString(KEY_DENSITY_LAYER, v)
            _densityLayerFlow.value = v
        }

    private val _densityLayerFlow = MutableStateFlow(densityLayerName)
    val densityLayerFlow: StateFlow<String> = _densityLayerFlow.asStateFlow()
```

- [ ] **Step 7: Wire it in `KursiApp.kt`** (mirror the coach wiring — find where `GameViewModel(` is constructed and where `coachEnabledFlow` is derived from prefs; add a mapped typed flow):

```kotlin
        // near the existing prefs→VM flow derivations:
        val densityLayerTyped: StateFlow<DensityLayer> =
            prefs.densityLayerFlow
                .map { DensityLayer.fromName(it) }
                .stateIn(appScope, SharingStarted.Eagerly, DensityLayer.fromName(prefs.densityLayerName))
        // …pass into the GameViewModel(...) call:
        //     densityLayerFlow = densityLayerTyped,
        //     onDensityLayerChange = { prefs.densityLayerName = it.name },
```

Add the imports it needs (`kotlinx.coroutines.flow.map`, `stateIn`, `SharingStarted`, `com.kursi.feature.game.DensityLayer`). Use whatever app-scope `CoroutineScope` KursiApp already holds; if none, reuse the existing scope pattern already present for the coach flow.

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :feature:game:jvmTest --tests "com.kursi.feature.game.DensityLayerTest"`
Expected: PASS (3 tests).

- [ ] **Step 9: Full module gate**

Run: `./gradlew :feature:game:jvmTest :core:prefs:jvmTest testAndroidHostTest detekt ktlintCheck`
Expected: BUILD SUCCESSFUL. (If `cmp-shared` fails to compile, the KursiApp wiring in Step 7 is incomplete — fix the `GameViewModel(...)` call site.)

- [ ] **Step 10: Commit**

```bash
git add feature/game/src/commonMain/kotlin/com/kursi/feature/game/DensityLayer.kt \
        feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameUiState.kt \
        feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameViewModel.kt \
        feature/game/src/commonTest/kotlin/com/kursi/feature/game/DensityLayerTest.kt \
        core/prefs/src/commonMain/kotlin/com/kursi/core/prefs/AppPrefs.kt \
        cmp-shared/src/commonMain/kotlin/com/kursi/shared/KursiApp.kt
git commit -m "feat(game): add DensityLayer axis (FOCUS/GUIDED/ANALYST), ANALYST default"
```

---

### Task 2: Beat-gate state machine (the frozen concurrency contract)

Make the paced bot round pausable on meaningful beats in `FOCUS`/`GUIDED` — a suspend-until-ack — while `ANALYST` and no-flow paths keep today's timed pacing byte-for-byte. This is the contract Track 1 (pacing UI) and Track 6 (online ack-timeout) both depend on, so its types are frozen here.

**Files:**
- Create: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/BeatGate.kt` (the `BeatTier` + `PendingBeat` types + `tierFor` classifier)
- Modify: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameUiState.kt` (add `pendingBeat` field)
- Modify: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameAction.kt` (add `ContinueBeat`)
- Modify: `feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameViewModel.kt` (`awaitBeat`, `beatAck`, refactor `pauseFor` onto `tierFor`, handle `ContinueBeat`)
- Test: `feature/game/src/commonTest/kotlin/com/kursi/feature/game/BeatGateTest.kt`

**Interfaces:**
- Consumes: `DensityLayer` + `densityLayerFlow` from Task 1.
- Produces:
  - `enum class BeatTier { TRIVIAL, ROUTINE, DRAMATIC }`
  - `fun tierFor(events: List<GameEvent>): BeatTier` (the extracted classifier `pauseFor` now also calls)
  - `data class PendingBeat(val tier: BeatTier)` — non-null on `GameUiState` when the paced loop is waiting for the player's tap.
  - `GameUiState.pendingBeat: PendingBeat?` (default null).
  - `GameAction.ContinueBeat` (data object).
  - VM behavior: `awaitBeat(events)` suspends on a `CompletableDeferred` (`beatAck`) when gated; Track 6 will additionally complete `beatAck` from a server-timeout job — that hook point is documented in `awaitBeat`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// BeatGateTest.kt
package com.kursi.feature.game

import com.kursi.engine.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class BeatGateTest {
    @Test
    fun tierFor_dramatic_beats() {
        assertEquals(BeatTier.DRAMATIC, tierFor(listOf(sampleInfluenceLost())))
    }

    @Test
    fun tierFor_routine_beats() {
        assertEquals(BeatTier.ROUTINE, tierFor(listOf(sampleActionDeclared())))
    }

    @Test
    fun tierFor_trivial_when_no_notable_events() {
        assertEquals(BeatTier.TRIVIAL, tierFor(emptyList()))
    }
}
```

`sampleInfluenceLost()` / `sampleActionDeclared()` — construct the smallest real `GameEvent.InfluenceLost` / `GameEvent.ActionDeclared` per their constructors (open `engine/.../Model.kt` for exact params; copy an existing `commonTest` construction if one exists).

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :feature:game:jvmTest --tests "com.kursi.feature.game.BeatGateTest"`
Expected: FAIL — `tierFor` / `BeatTier` unresolved.

- [ ] **Step 3: Create `BeatGate.kt` (extract the classifier from `pauseFor`)**

```kotlin
// BeatGate.kt
package com.kursi.feature.game

import com.kursi.engine.GameEvent

/** Rhythm tier of a resolved beat. Drives pacing (delay length) AND gating (which beats wait for a tap). */
enum class BeatTier { TRIVIAL, ROUTINE, DRAMATIC }

/** A resolved beat the paced loop is holding on until the player taps to continue (FOCUS/GUIDED only). */
data class PendingBeat(val tier: BeatTier)

/**
 * Classify a batch of events into a [BeatTier]. Single source of truth for both the timed pacing
 * ([GameViewModel.pauseFor]) and the beat gate ([GameViewModel.awaitBeat]).
 */
fun tierFor(events: List<GameEvent>): BeatTier {
    val dramatic = events.any {
        it is GameEvent.Challenged ||
            it is GameEvent.ChallengeRevealed ||
            it is GameEvent.Blocked ||
            it is GameEvent.InfluenceLost ||
            it is GameEvent.PlayerEliminated ||
            it is GameEvent.GameEnded
    }
    if (dramatic) return BeatTier.DRAMATIC
    val routine = events.any {
        it is GameEvent.ActionDeclared ||
            it is GameEvent.ActionResolved ||
            it is GameEvent.ActionNegated ||
            it is GameEvent.Exchanged ||
            it is GameEvent.CoinsTransferred
    }
    return if (routine) BeatTier.ROUTINE else BeatTier.TRIVIAL
}
```

- [ ] **Step 4: Refactor `pauseFor` onto `tierFor` (DRY)** — replace `GameViewModel.pauseFor` body (lines 808–835) with:

```kotlin
    private fun pauseFor(events: List<GameEvent>): Long {
        val base = when (tierFor(events)) {
            BeatTier.DRAMATIC -> DRAMATIC_STEP_MS
            BeatTier.ROUTINE -> ROUTINE_STEP_MS
            BeatTier.TRIVIAL -> TRIVIAL_STEP_MS
        }
        return (base * speedMultiplier).toLong().coerceIn(400L, 6000L)
    }
```

- [ ] **Step 5: Add `pendingBeat` to `GameUiState`** (after `densityLayer` from Task 1):

```kotlin
    /**
     * BEAT GATE (spec §5). Non-null when the paced bot round has shown a meaningful beat and is
     * waiting for the player to tap to continue (FOCUS/GUIDED). Null while flowing (ANALYST/AUTO) or
     * on the human's own turn. The UI shows a "tap to continue" affordance and dispatches
     * [GameAction.ContinueBeat].
     */
    val pendingBeat: PendingBeat? = null,
```

- [ ] **Step 6: Add the `ContinueBeat` action** — in `GameAction` (after `PlayBestMove`):

```kotlin
    /** BEAT GATE — the player tapped to advance past a held beat (spec §5). */
    data object ContinueBeat : GameAction
```

Handle it in `onAction` (`GameViewModel.kt:371`):

```kotlin
            is GameAction.ContinueBeat -> beatAck?.complete(Unit)
```

- [ ] **Step 7: Add `awaitBeat` + `beatAck` and use it in the advance loop**

Add fields near `advanceJob` (~line 149):

```kotlin
    /** Completed by [GameAction.ContinueBeat] (or, in online play — Track 6 — a server-timeout job)
     *  to release a gated beat. Non-null only while the paced loop is holding on a PendingBeat. */
    private var beatAck: kotlinx.coroutines.CompletableDeferred<Unit>? = null
```

Add the method (near `pauseFor`):

```kotlin
    /**
     * Pace the gap before the NEXT bot step. In ANALYST — or when no density-layer flow is wired
     * (tests / render harness) — this is exactly today's timed [delay]. In FOCUS/GUIDED a non-trivial
     * beat instead surfaces a [PendingBeat] and SUSPENDS until [GameAction.ContinueBeat] completes
     * [beatAck]. Trivial beats always flow (no tap for a bare income tick).
     *
     * ONLINE (Track 6) will additionally complete [beatAck] from a server ack-timeout so one player
     * cannot stall the table; the suspension point here is that hook.
     */
    private suspend fun awaitBeat(events: List<GameEvent>) {
        val layer = _state.value?.densityLayer ?: DensityLayer.ANALYST
        val gated = (layer == DensityLayer.FOCUS || layer == DensityLayer.GUIDED) &&
            tierFor(events) != BeatTier.TRIVIAL
        if (!gated) {
            delay(pauseFor(events))
            return
        }
        val ack = kotlinx.coroutines.CompletableDeferred<Unit>()
        beatAck = ack
        _state.value = _state.value?.copy(pendingBeat = PendingBeat(tierFor(events)))
        try {
            ack.await()
        } finally {
            beatAck = null
            _state.value = _state.value?.copy(pendingBeat = null)
        }
    }
```

In `submitIntent`'s advance loop (line 675), replace `delay(pauseFor(lastEvents))` with `awaitBeat(lastEvents)`.

- [ ] **Step 8: Run the beat-gate tests**

Run: `./gradlew :feature:game:jvmTest --tests "com.kursi.feature.game.BeatGateTest"`
Expected: PASS (3 tests).

- [ ] **Step 9: Full gate (guards the ANALYST no-change invariant)**

Run: `./gradlew :feature:game:jvmTest testAndroidHostTest detekt ktlintCheck`
Expected: BUILD SUCCESSFUL — existing ViewModel pacing tests still pass (they run with no density-layer flow → ANALYST → timed `delay`, unchanged).

- [ ] **Step 10: Commit**

```bash
git add feature/game/src/commonMain/kotlin/com/kursi/feature/game/BeatGate.kt \
        feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameUiState.kt \
        feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameAction.kt \
        feature/game/src/commonMain/kotlin/com/kursi/feature/game/GameViewModel.kt \
        feature/game/src/commonTest/kotlin/com/kursi/feature/game/BeatGateTest.kt
git commit -m "feat(game): beat-gate state machine (PendingBeat/ContinueBeat), ANALYST unchanged"
```

---

### Task 3: Motion-token stubs

A named spring-physics motion vocabulary in the design system so Track 2 (renderer) and Track 1 (Focus Pull) reference tokens, not scattered literals (instinct: design-tokens-not-hardcoded).

**Files:**
- Create: `core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/KursiMotion.kt`
- Test: `core/designsystem/src/commonTest/kotlin/com/kursi/designsystem/KursiMotionTest.kt`

**Interfaces:**
- Produces: `object KursiMotion` with named `AnimationSpec` tokens: `snap`, `beat`, `settle`, `dramatic` (spring specs) + `focusPullMs: Int`, `dealMs: Int` duration tokens. All honor reduced-motion at call sites (the tokens themselves are just specs).

- [ ] **Step 1: Write the failing test**

```kotlin
// KursiMotionTest.kt
package com.kursi.designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

class KursiMotionTest {
    @Test
    fun durations_are_positive_and_ordered() {
        assertTrue(KursiMotion.dealMs > 0)
        assertTrue(KursiMotion.focusPullMs > 0)
    }
}
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :core:designsystem:jvmTest --tests "com.kursi.designsystem.KursiMotionTest"`
Expected: FAIL — `KursiMotion` unresolved.

- [ ] **Step 3: Create the tokens**

```kotlin
// KursiMotion.kt
package com.kursi.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Named motion vocabulary (spec §7). Tracks reference these tokens, never raw literals. Call sites
 * are responsible for collapsing to a static end-state under reducedMotion (see MomentStaticFrames).
 */
object KursiMotion {
    /** Crisp UI response (chip press, toggle). */
    fun <T> snap(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    /** A beat landing on the table (card slide, token move). */
    fun <T> settle(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)

    /** Weighty dramatic beat (stamp slam, elimination). */
    fun <T> dramatic(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Focus-pull camera push duration (ms). */
    const val focusPullMs: Int = 420

    /** Card deal/flip duration (ms). */
    const val dealMs: Int = 300
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :core:designsystem:jvmTest --tests "com.kursi.designsystem.KursiMotionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/commonMain/kotlin/com/kursi/designsystem/KursiMotion.kt \
        core/designsystem/src/commonTest/kotlin/com/kursi/designsystem/KursiMotionTest.kt
git commit -m "feat(designsystem): add KursiMotion spring/duration tokens"
```

---

### Task 4: String-externalization scaffold

Prove the CMP string-resource path end to end with one real externalized string, so every parallel track adds user-facing copy as resources (spec §13) from the start instead of hardcoding.

**Files:**
- Create/append: `core/designsystem/src/commonMain/composeResources/values/strings.xml` (the module already has a `composeResources` dir — confirmed) — OR the module that already owns app strings if one exists (search for an existing `strings.xml` under a `composeResources/values` first and append there to avoid a second string table).
- Modify: one existing hardcoded user-facing string to read from the generated `Res.string.*` accessor as the reference pattern (pick the simplest — e.g. a Settings screen label).
- Test: N/A (resource generation is compile-time; the gate is that the app modules compile and the screenshot render is unchanged).

**Interfaces:**
- Produces: a working `Res.string.<key>` accessor + one migrated call site demonstrating the pattern tracks copy.

- [ ] **Step 1: Locate the canonical string table**

Run: `find . -path '*composeResources/values/strings.xml' -not -path '*/build/*'`
If one exists under a source module, use it. If only `core/designsystem/src/commonMain/composeResources` exists without a `values/strings.xml`, create `values/strings.xml` there.

- [ ] **Step 2: Add one real string**

```xml
<!-- composeResources/values/strings.xml -->
<resources>
    <string name="settings_title">Kaagaz-Patra</string>
</resources>
```

- [ ] **Step 3: Migrate one call site to `stringResource`**

Find a hardcoded settings/title literal in `cmp-shared/src/commonMain/kotlin/com/kursi/shared/screen/SettingsScreen.kt` and replace it with `stringResource(Res.string.settings_title)` (import the generated `…generated.resources.Res` + `org.jetbrains.compose.resources.stringResource`). Match the exact generated package (build once to see it: `<module>.generated.resources.Res`).

- [ ] **Step 4: Build the affected app module**

Run: `./gradlew :cmp-shared:compileKotlinMetadata :cmp-android:assembleNoGmsDebug`
Expected: BUILD SUCCESSFUL (the generated `Res` accessor resolves).

- [ ] **Step 5: Verify the screenshot render is unchanged in meaning**

Run: `./gradlew :cmp-desktop:renderScreens`
Expected: renders succeed; the settings title now reads from resources (same text). No baseline semantics change.

- [ ] **Step 6: Commit**

```bash
git add <the strings.xml path> cmp-shared/src/commonMain/kotlin/com/kursi/shared/screen/SettingsScreen.kt
git commit -m "chore(l10n): scaffold compose string resources, migrate one call site"
```

---

## Contract freeze (end of Wave 0)

After Tasks 1–4 merge to `feat/launch-overhaul`, these are frozen for the six Wave 1 tracks:

- **`DensityLayer { FOCUS, GUIDED, ANALYST }`** + `GameUiState.densityLayer` + `densityLayerFlow`/`onDensityLayerChange` VM params. Overlays gate on this (Track 4).
- **Beat gate:** `BeatTier`, `tierFor`, `PendingBeat`, `GameUiState.pendingBeat`, `GameAction.ContinueBeat`, `awaitBeat`/`beatAck`. Track 1 builds the UI; Track 6 adds the server ack-timeout at `awaitBeat`'s suspension point.
- **`KursiMotion`** tokens. Track 1/2 reference these.
- **String-resource path** (`Res.string.*`). All tracks add copy as resources.

Each Wave 1 track then gets its own plan (written against these frozen contracts) and runs in its **own git worktree**, merged one at a time behind the verification gate.

## Self-Review

- **Spec coverage:** Wave 0 covers spec §3 (DensityLayer), §5 (beat gate), §7 motion tokens, §13 l10n scaffold — the four contract/stubs the parallel-wave timeline (§16) says Wave 0 must freeze. GameScreen decomposition (§6) is intentionally deferred to the start of Wave 1 Track 4 rather than Wave 0, because it is large, `feature/game`-local, and blocks no other track once the contracts above exist — folding it here would make the serial foundation slow, defeating its purpose. (Deviation from spec §16, which lists the decomposition in Wave 0; noted here — the executor should confirm this reordering is acceptable, or pull the decomposition in as Task 0.)
- **Placeholders:** none — every step has runnable code/commands.
- **Type consistency:** `DensityLayer.fromName`, `tierFor`, `PendingBeat(tier)`, `ContinueBeat`, `beatAck.complete(Unit)` used consistently across tasks.
- **Ambiguity:** the string-table location (Task 4 Step 1) is resolved by a `find` before writing, not assumed.
