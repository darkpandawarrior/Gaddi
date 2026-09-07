package com.kursi.ai

import com.kursi.ai.advisor.MoveAdvisor
import com.kursi.ai.persona.PersonaRoster
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.engine.initialState
import com.kursi.engine.legalIntents
import com.siddharth.kmp.ai.testing.FakeOnDeviceLlm
import com.siddharth.kmp.botspolicy.SearchBudget
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// ponytail: maxMillis generous on purpose (not the production QUICK_BUDGET's 200ms) — a
// wall-clock cap makes ISMCTS's outcome depend on how many iterations complete before the
// deadline, which varies with real machine load; maxIterations alone (deterministic, count-based)
// is what should actually terminate the search here, so the test's independently-computed
// `ranked` and the engine's own internal search (built with this same budget) agree every run.
private val TEST_BUDGET = SearchBudget(maxMillis = 60_000L, maxIterations = 800, rolloutHorizon = 8)

/**
 * [AiBotDecisionEngine.decide] was never called from anywhere in the app (see the lane brief) and
 * had zero tests, unlike every other bot-policy tier. These pin the one behavior the "voiced" tier's
 * whole promise rests on: it plays exactly like the ISMCTS floor (the [MoveAdvisor] recommendation)
 * whenever a model is unavailable, times out, or answers with something unusable — never a random
 * or illegal move — and only ever departs from that floor when a model gives back a valid label for
 * a DIFFERENT legal candidate.
 *
 * Wraps [FakeOnDeviceLlm] (the toolkit's scriptable on-device double) in a tiny local [AiProvider]
 * adapter — the same shape production's `OnDeviceAiProvider` actuals use — since
 * [AiBotDecisionEngine.decide] takes the `llm-chat` seam ([AiProvider]), not `:ai`'s `OnDeviceLlm`
 * directly.
 */
class AiBotDecisionEngineTest {
    private val seed = 42L
    private val config = GameConfig.forPlayers(3)
    private val state = initialState(config, seed)
    private val botId = PlayerId(0)
    private val legal = legalIntents(state, botId)
    private val persona = PersonaRoster.ALL.first()

    /** Same construction [AiBotDecisionEngine] uses internally, so a test can compute the expected floor
     *  and a non-floor alternative without re-implementing the search. */
    private val ranked = MoveAdvisor(seed, TEST_BUDGET).advise(state, botId, legal)
    private val expectedFallback = ranked.first { it.recommended }.intent

    private class FakeProvider(
        private val llm: FakeOnDeviceLlm,
    ) : AiProvider {
        override val id = "fake"
        override val displayName = "fake"

        override suspend fun isAvailable(): Boolean = llm.isAvailable()

        override suspend fun complete(
            messages: List<AiMessage>,
            config: AiConfig,
        ): AiResult<String> = llm.generate(messages.joinToString("\n") { it.content })
    }

    private suspend fun decide(llm: FakeOnDeviceLlm): com.kursi.engine.Intent {
        val engine = AiBotDecisionEngine(seed, budget = TEST_BUDGET)
        return engine.decide(state, botId, persona, arc = null, provider = FakeProvider(llm))
    }

    @Test
    fun decide_unavailableProvider_neverCallsIt_fallsBackToTheIsmctsFloor() =
        runTest {
            val llm = FakeOnDeviceLlm(available = false)
            llm.enqueueSuccess("this must never be read")

            assertEquals(expectedFallback, decide(llm))
        }

    @Test
    fun decide_availableProviderFails_fallsBackToTheIsmctsFloor() =
        runTest {
            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueFailure(AiFailure.Network)

            assertEquals(expectedFallback, decide(llm))
        }

    @Test
    fun decide_availableProviderAnswersGarbage_fallsBackToTheIsmctsFloor() =
        runTest {
            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueSuccess("not a real action label")

            assertEquals(expectedFallback, decide(llm))
        }

    @Test
    fun decide_availableProviderAnswersAValidDifferentLabel_usesIt() =
        runTest {
            // A candidate distinct from the recommended one, ranked #2+ — proves decide() actually
            // reads the model's answer rather than always landing on the ISMCTS top pick regardless.
            val alternative = ranked.first { !it.recommended }
            val label = GameContextSerializer.intentLabel(alternative.intent)

            val llm = FakeOnDeviceLlm(available = true)
            llm.enqueueSuccess("  $label  \n") // whitespace as a real reply might have it

            val result = decide(llm)

            assertEquals(alternative.intent, result)
            assertNotEquals(expectedFallback, result)
        }
}
