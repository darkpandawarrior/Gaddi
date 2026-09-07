package com.kursi.feature.game

import com.kursi.engine.Intent
import com.kursi.engine.PhaseView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for [GameViewModel.submitIntent]'s legality guard.
 *
 * History: a double-tap (or any tap landing after the turn had already advanced into a
 * reaction window) dispatched a [GameAction.Submit] the engine considered illegal. The engine
 * threw [IllegalStateException] ("Intent rejected by engine: expected Block/Pass ..."), which
 * was uncaught and crashed the whole desktop window. The VM now ignores any intent not in the
 * engine's current legal set (plus a try/catch backstop). These tests lock that in.
 *
 * Seed 1 + seat 0 deterministically gives the human the opening turn (see GameSessionTest T3).
 */
class GameViewModelTest {
    @Test
    fun illegalIntent_duringTurn_isIgnored_andDoesNotCrash() {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))

        val before = vm.state.value
        assertNotNull(before, "state should be non-null after NewGame")
        assertTrue(before.isHumanTurn, "seat 0 acts first with seed 1")
        assertTrue(before.view.phase is PhaseView.Turn, "human opens in a Turn phase")

        // Pass is a REACTION intent — illegal while it's the human's action (Turn) phase.
        val illegal = Intent.Pass(vm.humanSeat)
        assertFalse(illegal in before.legalIntents, "precondition: Pass is illegal during Turn")

        // This exact dispatch used to throw IllegalStateException and crash the app.
        vm.onAction(GameAction.Submit(illegal))

        val after = vm.state.value
        assertNotNull(after)
        // Pure no-op: the illegal intent must not have mutated game state.
        assertEquals(before.view.phase, after.view.phase, "illegal intent must not change phase")
        assertEquals(before.legalIntents, after.legalIntents, "illegal intent must not change legal set")
        assertEquals(before.isHumanTurn, after.isHumanTurn)
    }

    @Test
    fun doubleSubmitOfSameAction_secondIsIgnored_noCrash() {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 4, difficulty = Difficulty.Easy, seed = 1L))

        val s0 = vm.state.value
        assertNotNull(s0)
        assertTrue(s0.isHumanTurn, "seat 0 acts first with seed 1")

        val action = s0.legalIntents.filterIsInstance<Intent.DeclareAction>().first()

        // First tap: legal, applies.
        vm.onAction(GameAction.Submit(action))
        val s1 = vm.state.value
        assertNotNull(s1)

        // Second tap of the SAME action (the double-tap). After the first, the engine has moved
        // on; re-submitting used to crash. Reaching the assertion below at all is the regression
        // proof — no exception escaped the reducer.
        vm.onAction(GameAction.Submit(action))
        val s2 = vm.state.value
        assertNotNull(s2, "app survived the double-submit")
    }

    @Test
    fun legalIntent_stillApplies_guardDoesNotNeuterValidActions() {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))

        val before = vm.state.value
        assertNotNull(before)
        val legal = before.legalIntents.first()

        vm.onAction(GameAction.Submit(legal))

        val after = vm.state.value
        assertNotNull(after)
        // A legal intent must NOT be swallowed by the guard: the state object must have advanced.
        assertTrue(after !== before, "legal intent must produce a new state (guard must not block it)")
        assertTrue(
            after.recentEvents.size >= before.recentEvents.size,
            "a declared action should record at least as many events",
        )
    }

    /**
     * MUNSHI (Track 3, spec §8.1, §8.6) wiring check: with no on-device SDK and no BYOK key
     * available (the JVM test environment — [com.kursi.ai.provider.OnDeviceAiProvider]'s jvm actual
     * always reports unavailable, and [GameViewModel] never supplies a cloud key by default), the
     * provider matrix selection always lands on the templated floor, which never sets
     * [GameUiState.narrationText] (and never flips [GameUiState.narrationStreaming]). Locks in
     * "AI off/unavailable = byte-identical to today".
     */
    @Test
    fun narrationText_staysNull_whenNoAiProviderIsAvailable() {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))
        assertEquals(null, vm.state.value?.narrationText)
        assertEquals(false, vm.state.value?.narrationStreaming)

        val legal = requireNotNull(vm.state.value).legalIntents.first()
        vm.onAction(GameAction.Submit(legal))
        assertEquals(null, vm.state.value?.narrationText)
        assertEquals(false, vm.state.value?.narrationStreaming)
    }
}
