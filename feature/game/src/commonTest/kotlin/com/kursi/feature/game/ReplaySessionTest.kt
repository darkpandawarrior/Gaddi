package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.ai.Policy
import com.kursi.engine.GameConfig
import com.kursi.engine.GameState
import com.kursi.engine.PlayerId
import com.kursi.feature.game.session.CompletedMatch
import com.kursi.feature.game.session.MatchSnapshot
import com.kursi.feature.game.session.ReplaySession
import com.kursi.feature.game.session.SnapPersona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6c §3 — a recorded match replays DETERMINISTICALLY: a [ReplaySession] reconstructed from
 * (seed + human log) reproduces the SAME sequence of [GameState]s as the original live run, and the
 * frames it yields are redacted from the HUMAN perspective (secrecy preserved during review).
 */
class ReplaySessionTest {
    private val seed = 4242L
    private val players = 4

    /** Deterministic bots keyed off the seed — identical recipe used for the original run and replay. */
    private fun botsFor(): Map<PlayerId, Policy> =
        (1 until players).associate { seat ->
            PlayerId(seat) to
                EasyPolicy(seed * 31L + seat) as Policy
        }

    private fun makeSession(): com.kursi.feature.game.session.GameSession =
        com.kursi.feature.game.session.GameSession(
            config = GameConfig.forPlayers(players),
            seed = seed,
            humanSeat = PlayerId(0),
            bots = botsFor(),
        )

    /** Drive a session to game-over with a deterministic "human", capturing the FULL state sequence. */
    private fun driveCapturingStates(
        session: com.kursi.feature.game.session.GameSession,
    ): Pair<List<GameState>, List<com.kursi.engine.Intent>> {
        val humanPolicy = EasyPolicy(99L)
        val states = ArrayList<GameState>()
        var ui = session.start()
        // Capture the state at each human-decision point, mirroring what ReplaySession snapshots.
        while (!ui.isGameOver) {
            if (ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
                states.add(session.snapshotState())
                ui = session.submitHuman(humanPolicy.decide(ui.view, ui.legalIntents))
            } else {
                break
            }
        }
        states.add(session.snapshotState()) // terminal
        return states to session.humanActionLog()
    }

    @Test
    fun replay_reproducesSameGameStateSequence() {
        // Original live run — capture the GameState at each decision point + the terminal state.
        val original = makeSession()
        val (originalStates, log) = driveCapturingStates(original)
        assertTrue(log.isNotEmpty(), "expected the human to have acted at least once")
        assertTrue(originalStates.size >= 2, "expected at least one decision frame + terminal")

        // Rebuild a ReplaySession from the same seed + the recorded human log + the SAME bot recipe.
        val snapshot = MatchSnapshot.of(seed, players, Difficulty.Medium, log)
        val replay =
            ReplaySession.build(
                snapshot = snapshot,
                humanSeats = setOf(PlayerId(0)),
                bots = botsFor(),
                winnerSeat = (original.snapshotState().phase as com.kursi.engine.Phase.GameOver).winner.raw,
            )

        // DETERMINISM: the replay's captured GameState sequence equals the original, bit-for-bit.
        assertEquals(
            originalStates,
            replay.gameStates(),
            "replay must reproduce the exact original GameState sequence",
        )
    }

    @Test
    fun replay_throughSerializedCompletedMatch_reproducesStates() {
        val original = makeSession()
        val (originalStates, log) = driveCapturingStates(original)
        val winner = (original.snapshotState().phase as com.kursi.engine.Phase.GameOver).winner.raw

        // Encode → string → decode, exactly as AppPrefs persistence does for a finished match.
        val record =
            CompletedMatch.of(
                seed = seed,
                players = players,
                difficulty = Difficulty.Medium,
                humanLog = log,
                winnerSeat = winner,
                personas = listOf(SnapPersona(seat = 0, name = "Aap", monogram = "A", seatColorArgb = 0xFF009E73L, isHuman = true)),
            )
        val decoded = CompletedMatch.decode(record.encode()) ?: error("record failed to decode")
        assertEquals(seed, decoded.seed)
        assertEquals(winner, decoded.winnerSeat)

        val replay =
            ReplaySession.build(
                snapshot = decoded.toSnapshot(),
                humanSeats = setOf(PlayerId(0)),
                bots = botsFor(),
                winnerSeat = decoded.winnerSeat,
            )
        assertEquals(originalStates, replay.gameStates())
    }

    @Test
    fun replay_stepNavigation_stopsOnHumanDecisions_andRedacts() {
        val original = makeSession()
        val (_, log) = driveCapturingStates(original)
        val winner = (original.snapshotState().phase as com.kursi.engine.Phase.GameOver).winner.raw
        val replay =
            ReplaySession.build(
                snapshot = MatchSnapshot.of(seed, players, Difficulty.Medium, log),
                humanSeats = setOf(PlayerId(0)),
                bots = botsFor(),
                winnerSeat = winner,
            )

        // There is at least one human-decision step, and the final step is the terminal (not a decision).
        assertTrue(replay.humanDecisionIndices.isNotEmpty(), "expected at least one human decision")
        assertTrue(replay.stepCount >= 1)
        assertEquals(
            replay.stepCount - 1,
            replay.humanDecisionIndices.last() + 1,
            "terminal frame should be the step right after the last human decision",
        )

        // stepTo every human-decision index yields a human-turn frame redacted for seat 0:
        // the human only ever sees their OWN hand (every other player's cards are hidden).
        for (idx in replay.humanDecisionIndices) {
            val ui = replay.stepTo(idx)
            assertTrue(ui.isHumanTurn, "human-decision frame $idx must be a human turn")
            assertEquals(0, ui.view.viewer.raw, "review frame must be from the human (seat 0) perspective")
        }

        // next()/prev() navigation is clamped and consistent.
        replay.stepTo(0)
        val first = replay.current()
        assertEquals(first, replay.prev(), "prev at start is a no-op")
        val last = replay.stepTo(replay.stepCount - 1)
        assertEquals(last, replay.next(), "next at end is a no-op")
    }

    @Test
    fun replay_isStableAcrossTwoReconstructions() {
        val original = makeSession()
        val (_, log) = driveCapturingStates(original)
        val winner = (original.snapshotState().phase as com.kursi.engine.Phase.GameOver).winner.raw
        val snap = MatchSnapshot.of(seed, players, Difficulty.Medium, log)

        val a = ReplaySession.build(snap, setOf(PlayerId(0)), botsFor(), winner)
        val b = ReplaySession.build(snap, setOf(PlayerId(0)), botsFor(), winner)
        // Same inputs → identical reconstructed state sequence (no hidden nondeterminism).
        assertEquals(a.gameStates(), b.gameStates())
    }
}
