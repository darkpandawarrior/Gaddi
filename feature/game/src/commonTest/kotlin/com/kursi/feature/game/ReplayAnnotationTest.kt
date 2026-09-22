package com.kursi.feature.game

import com.kursi.ai.EasyPolicy
import com.kursi.ai.Policy
import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.feature.game.session.MatchSnapshot
import com.kursi.feature.game.session.ReplayAnnotation
import com.kursi.feature.game.session.ReplaySession
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M6c §2 — every HUMAN-decision frame of a replay carries a fair advisor annotation: what was played
 * vs the recommended best, the EV gap, the verdict, and a bilingual voiced belief read. The terminal
 * frame carries none. Annotations are deterministic across reconstructions.
 */
class ReplayAnnotationTest {
    private val seed = 4242L
    private val players = 4

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

    private fun driveLog(): Pair<List<com.kursi.engine.Intent>, Int> {
        val session = makeSession()
        val human = EasyPolicy(99L)
        var ui = session.start()
        while (!ui.isGameOver && ui.isHumanTurn && ui.legalIntents.isNotEmpty()) {
            ui = session.submitHuman(human.decide(ui.view, ui.legalIntents))
        }
        val winner = (session.snapshotState().phase as com.kursi.engine.Phase.GameOver).winner.raw
        return session.humanActionLog() to winner
    }

    @Test
    fun everyHumanDecisionFrame_carriesAnAnnotation_terminalDoesNot() {
        val (log, winner) = driveLog()
        assertTrue(log.isNotEmpty(), "expected at least one human move")
        val replay =
            ReplaySession.build(
                snapshot = MatchSnapshot.of(seed, players, Difficulty.Medium, log),
                humanSeats = setOf(PlayerId(0)),
                bots = botsFor(),
                winnerSeat = winner,
            )

        assertTrue(replay.humanDecisionIndices.isNotEmpty(), "expected human decisions to annotate")

        var graded = 0
        for (idx in replay.humanDecisionIndices) {
            val a = replay.annotationAt(idx)
            // A genuine ≥2-option decision yields a non-null annotation; a forced single-legal move
            // (no real choice) may yield null. At least one real decision must be graded.
            if (a != null) {
                graded++
                assertTrue(a.playedLabel.isNotBlank(), "played label must be present")
                assertTrue(a.bestLabel.isNotBlank(), "best label must be present")
                assertTrue(a.beliefHinglish.isNotBlank(), "Hinglish belief read must be present")
                assertTrue(a.beliefEnglish.isNotBlank(), "English belief read must be present")
                assertTrue(a.evGapPct in 0..100, "EV gap must be a sane percent")
                // matchedBest <=> verdict SHARP and a zero gap.
                if (a.matchedBest) {
                    assertTrue(a.evGapPct == 0, "a best move should have no EV gap")
                    assertTrue(a.verdict == ReplayAnnotation.Verdict.SHARP)
                }
            }
        }
        assertTrue(graded > 0, "at least one real (≥2-option) decision should be annotated")

        // The terminal frame is a result, not a decision — no annotation.
        val terminal = replay.stepCount - 1
        assertTrue(replay.annotationAt(terminal) == null, "terminal frame carries no annotation")
    }

    @Test
    fun annotations_areDeterministicAcrossReconstructions() {
        val (log, winner) = driveLog()
        val snap = MatchSnapshot.of(seed, players, Difficulty.Medium, log)
        val a = ReplaySession.build(snap, setOf(PlayerId(0)), botsFor(), winner)
        val b = ReplaySession.build(snap, setOf(PlayerId(0)), botsFor(), winner)

        // The first human-decision annotation is identical across two independent reconstructions.
        val firstIdx = a.humanDecisionIndices.first { a.annotationAt(it) != null }
        val ann = assertNotNull(a.annotationAt(firstIdx))
        val ann2 = assertNotNull(b.annotationAt(firstIdx))
        assertTrue(ann == ann2, "same inputs must yield identical annotations")
    }
}
