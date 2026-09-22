package com.kursi.shared.nav

import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GameUiState

/**
 * Converts a terminal [GameUiState] into a [MatchSummary] snapshot for ResultsScreen.
 * Called from GameRoute the moment isGameOver becomes true.
 *
 * The match params (seed, players, difficulty) are passed in separately since
 * GameUiState doesn't hold routing metadata.
 */
fun GameUiState.toMatchSummary(
    seed: Long,
    players: Int,
    difficulty: Difficulty,
): MatchSummary {
    val winnerPersona = winnerSeat?.let { s -> opponentPersonas[com.kursi.engine.PlayerId(s)] }
    val humanWon = winnerSeat == 0

    // Count event types for the recap stats
    var bluffsHeld = 0
    var bluffsCaught = 0
    val recentEventLines = recentEvents.takeLast(20).map { it.toString() }

    for (event in recentEvents) {
        val desc = event.toString()
        when {
            desc.contains("bluff", ignoreCase = true) && desc.contains("held", ignoreCase = true) -> bluffsHeld++
            desc.contains("bluff", ignoreCase = true) && desc.contains("caught", ignoreCase = true) -> bluffsCaught++
            desc.contains("BluffCaught", ignoreCase = true) -> bluffsCaught++
            desc.contains("BluffTrue", ignoreCase = true) -> bluffsHeld++
        }
    }

    // Build final standings: winner first, then everyone else by elimination order
    val standings =
        buildList {
            winnerSeat?.let { s ->
                val name = if (s == 0) "Aap" else opponentPersonas[com.kursi.engine.PlayerId(s)]?.name ?: "P$s"
                add(name)
            }
            for (s in 0 until players) {
                if (s == winnerSeat) continue
                val name = if (s == 0) "Aap" else opponentPersonas[com.kursi.engine.PlayerId(s)]?.name ?: "P$s"
                add(name)
            }
        }

    return MatchSummary(
        matchId = "", // filled by MatchSummaryStore.put()
        seed = seed,
        players = players,
        difficulty = difficulty,
        winnerSeat = winnerSeat,
        winnerName = if (humanWon) "Aap" else winnerPersona?.name,
        winnerMonogram = if (humanWon) "AAP" else winnerPersona?.monogram,
        winnerColor = winnerPersona?.seatColorArgb ?: 0xFFC99A3BL,
        humanWon = humanWon,
        turnsTotal = view.turnNumber,
        bluffsHeld = bluffsHeld,
        bluffsCaught = bluffsCaught,
        recentEvents = recentEventLines,
        finalStandings = standings,
        bestMomentPersonaId = null,
    )
}
