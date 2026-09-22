package com.kursi.feature.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Builds a real [GameUiState] the same way [GameViewModelTest] does — a fresh 2p game, seed 1. */
private fun sampleUiState(): GameUiState {
    val vm = GameViewModel()
    vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))
    return requireNotNull(vm.state.value) { "state should be non-null after NewGame" }
}

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

/** Graduation policy (spec §3, §6): FOCUS → GUIDED → ANALYST by games played + a competence signal,
 *  never past a manual choice. */
class DensityGraduationTest {
    @Test
    fun manualOverride_neverAdvances() {
        assertEquals(
            DensityLayer.FOCUS,
            evaluateDensityGraduation(
                current = DensityLayer.FOCUS,
                manuallySet = true,
                gamesPlayed = 100,
                decisions = 100,
                accuracyPct = 90,
                avgEvLostPct = 0,
            ),
        )
        assertEquals(
            DensityLayer.GUIDED,
            evaluateDensityGraduation(
                current = DensityLayer.GUIDED,
                manuallySet = true,
                gamesPlayed = 100,
                decisions = 100,
                accuracyPct = 90,
                avgEvLostPct = 0,
            ),
        )
    }

    @Test
    fun focus_staysUntilMatchThreshold() {
        assertEquals(DensityLayer.FOCUS, evaluateDensityGraduation(DensityLayer.FOCUS, manuallySet = false, gamesPlayed = 0))
        assertEquals(DensityLayer.FOCUS, evaluateDensityGraduation(DensityLayer.FOCUS, manuallySet = false, gamesPlayed = 2))
        assertEquals(DensityLayer.GUIDED, evaluateDensityGraduation(DensityLayer.FOCUS, manuallySet = false, gamesPlayed = 3))
    }

    @Test
    fun guided_staysUntilMatchThreshold() {
        assertEquals(DensityLayer.GUIDED, evaluateDensityGraduation(DensityLayer.GUIDED, manuallySet = false, gamesPlayed = 7))
        assertEquals(DensityLayer.ANALYST, evaluateDensityGraduation(DensityLayer.GUIDED, manuallySet = false, gamesPlayed = 8))
    }

    @Test
    fun analyst_isTerminal() {
        assertEquals(DensityLayer.ANALYST, evaluateDensityGraduation(DensityLayer.ANALYST, manuallySet = false, gamesPlayed = 0))
    }

    @Test
    fun competentPlay_graduatesEarlier() {
        // Below the un-competent threshold (3), but competence (enough decisions, not reckless) pulls it in.
        assertEquals(
            DensityLayer.FOCUS,
            evaluateDensityGraduation(
                DensityLayer.FOCUS,
                manuallySet = false,
                gamesPlayed = 1,
                decisions = 10,
                accuracyPct = 80,
                avgEvLostPct = 2,
            ),
        )
        assertEquals(
            DensityLayer.GUIDED,
            evaluateDensityGraduation(
                DensityLayer.FOCUS,
                manuallySet = false,
                gamesPlayed = 2,
                decisions = 10,
                accuracyPct = 80,
                avgEvLostPct = 2,
            ),
        )
    }

    @Test
    fun recklessPlay_doesNotGraduateEarlier() {
        // Enough decisions but a RECKLESS read (per core/prefs' DecisionGrade thresholds) — no fast-track.
        assertEquals(
            DensityLayer.FOCUS,
            evaluateDensityGraduation(
                DensityLayer.FOCUS,
                manuallySet = false,
                gamesPlayed = 2,
                decisions = 10,
                accuracyPct = 30,
                avgEvLostPct = 20,
            ),
        )
        assertEquals(
            DensityLayer.GUIDED,
            evaluateDensityGraduation(
                DensityLayer.FOCUS,
                manuallySet = false,
                gamesPlayed = 3,
                decisions = 10,
                accuracyPct = 30,
                avgEvLostPct = 20,
            ),
        )
    }

    @Test
    fun tooFewDecisions_doesNotCountAsCompetent() {
        // Great numbers, but under the minimum-sample floor — the un-competent threshold still applies.
        assertEquals(
            DensityLayer.FOCUS,
            evaluateDensityGraduation(
                DensityLayer.FOCUS,
                manuallySet = false,
                gamesPlayed = 2,
                decisions = 3,
                accuracyPct = 100,
                avgEvLostPct = 0,
            ),
        )
    }
}
