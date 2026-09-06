package com.kursi.feature.game.overlays

import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GameAction
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.GameViewModel
import com.kursi.feature.game.KursiVoice
import com.kursi.feature.game.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Locks in the MUNSHI upgrade-in-place rule (spec §8.1, §8.6): [displayHeadlineFor] must report
 * [BeatLine.Templated] whenever [GameUiState.narrationText] hasn't landed (null or blank — the
 * AI-off / no-provider floor), and [BeatLine.Ai] — carrying [GameUiState.narrationStreaming]
 * honestly — the moment a Munshi line has.
 */
class BeatHeadlineTest {
    private val voice = KursiVoice(Language.HINGLISH)

    private fun freshState(): GameUiState {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))
        return requireNotNull(vm.state.value)
    }

    @Test
    fun displayHeadlineFor_isTemplated_whenNarrationTextIsNull() {
        val state = freshState()

        val line = displayHeadlineFor(state.recentEvents, state, voice)

        assertIs<BeatLine.Templated>(line)
        assertEquals(headlineFor(state.recentEvents, state, voice), line.text)
    }

    @Test
    fun displayHeadlineFor_isTemplated_whenNarrationTextIsBlank() {
        val state = freshState().copy(narrationText = "   ")

        val line = displayHeadlineFor(state.recentEvents, state, voice)

        assertIs<BeatLine.Templated>(line)
        assertEquals(headlineFor(state.recentEvents, state, voice), line.text)
    }

    @Test
    fun displayHeadlineFor_isAiStreaming_whenNarrationTextPresentAndNotYetSettled() {
        val state = freshState().copy(narrationText = "Bahenji stamped the Neta's seal", narrationStreaming = true)

        val line = displayHeadlineFor(state.recentEvents, state, voice)

        assertIs<BeatLine.Ai>(line)
        assertEquals("Bahenji stamped the Neta's seal", line.text)
        assertTrue(line.streaming)
    }

    @Test
    fun displayHeadlineFor_isAiSettled_whenNarrationStreamingIsFalse() {
        val state = freshState().copy(narrationText = "Bahenji stamped the Neta's seal.", narrationStreaming = false)

        val line = displayHeadlineFor(state.recentEvents, state, voice)

        assertIs<BeatLine.Ai>(line)
        assertEquals("Bahenji stamped the Neta's seal.", line.text)
        assertFalse(line.streaming)
    }
}
