package com.kursi.ai

import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.engine.initialState
import com.kursi.engine.redact
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [createMunshiNarrator]'s consent gate (the branch [com.kursi.shared.screen.SettingsScreen]'s
 * "Enable AI features" switch drives) is the one piece of this factory worth its own test: get it
 * wrong and the switch's own promised copy ("no on-device or cloud model call will run") becomes a
 * lie. Verified behaviorally through [MunshiNarrator.narrate]'s public surface — never through
 * [createMunshiNarrator]'s private `cloudConfig` — so this also never touches a real
 * `SecureKeyStore` (consent=false returns before that call).
 */
class MunshiNarratorFactoryTest {
    private val config = GameConfig.forPlayers(2)
    private val state = initialState(config, seed = 1L)
    private val view = redact(state, PlayerId(0))
    private val events = listOf<GameEvent>(GameEvent.TurnAdvanced(toSeat = 0, turnNumber = 1))

    @Test
    fun createMunshiNarrator_consentWithdrawn_narratesNothing_regardlessOfSelectedProvider() =
        runTest {
            val munshi = createMunshiNarrator(consentGiven = false, selectedProviderName = "ANTHROPIC")

            assertEquals(emptyList(), munshi.narrate(view, events).toList())
        }
}
