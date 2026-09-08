package com.kursi.ai

import com.kursi.engine.GameConfig
import com.kursi.engine.Rng
import com.kursi.engine.initialState
import com.kursi.engine.redact
import com.kursi.engine.teamSafeIntents
import com.kursi.engine.whoActsNext
import com.siddharth.kmp.botspolicy.SearchBudget
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression for the runBlocking -> startCoroutine driver swap in [IsmctsSearch.chooseIntent]:
 * that path must keep returning a legal intent, synchronously, on every target (including wasmJs,
 * which has no [kotlinx.coroutines.runBlocking]).
 */
class IsmctsSearchBlockingTest {
    @Test
    fun chooseIntent_returnsLegalIntent_fromOpeningPosition() {
        val config = GameConfig.forPlayers(2)
        val state = initialState(config, seed = 1L)
        val who = whoActsNext(state) ?: error("no one to act at game start")
        val legal = teamSafeIntents(state, who)
        val view = redact(state, who)

        val search =
            IsmctsSearch(
                determinizer = Determinizer(BeliefModel()),
                budget = SearchBudget(maxMillis = 200L, maxIterations = 50, rolloutHorizon = 4),
                rolloutSeed = 42L,
            )
        val chosen = search.chooseIntent(view, legal, BotMemory(), Rng(7L))

        assertTrue(chosen in legal, "chooseIntent returned an illegal intent: $chosen not in $legal")
    }
}
