package com.kursi.ai

import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Strength regression guards for the GRANDMASTER tier — it must out-play EXPERT head-to-head.
 *
 * Why this exists: GRANDMASTER is "one rung above Expert". The two levers that buy that edge — a
 * deeper ISMCTS budget and the opponent-model exploitation gain — are both tuning-sensitive. A future
 * refactor could silently collapse Grandmaster to Expert-parity while every functional test stays
 * green (games complete, no crashes). These seeded sims pin Grandmaster's edge over Expert at 2p and
 * 4p so any such regression trips red.
 *
 * Note on the exploitation read: [SimHarness.playOut] never calls [GrandmasterPolicy.observe], so the
 * belief-model-driven exploitation override is INERT in these sims (no events are fed back). The edge
 * measured here therefore comes purely from the DEEPER SEARCH BUDGET — which is exactly what we want to
 * guard, because it is the part that holds unconditionally, even against an opponent we have no read on.
 * The exploitation layer is covered separately by [GrandmasterExploitTest] (it is strictly additive).
 *
 * Budgets are sized to stay within the configured 4g JVM test heap (see GaddiKmpPureConventionPlugin):
 * Grandmaster gets a strictly deeper iteration/horizon budget than the Expert opponent, both bounded by
 * the iteration cap (not the wall clock) so the result is deterministic across machines.
 */
class GrandmasterStrengthRegressionTest {
    // Expert opponent budget — the in-game-ish strength we must beat.
    private val expertBudget =
        SearchBudget(
            maxMillis = 8_000L,
            maxIterations = 600,
            rolloutHorizon = 12,
        )

    // Grandmaster budget — strictly DEEPER: more iterations and a longer rollout horizon. This is the
    // search-depth edge that must show up as a higher win rate even with no opponent read.
    private val gmBudget =
        SearchBudget(
            maxMillis = 14_000L,
            maxIterations = 2_000,
            rolloutHorizon = 22,
        )

    private fun Double.fmt3() = ((this * 1000).roundToInt().toDouble() / 1000.0).toString()

    @Test
    fun grandmaster_beats_expert_2player_floor() {
        if (!heavyAiSimsEnabled()) return // skip heavy ISMCTS sim on wasm/browser (Karma ping starvation)
        val games = 120
        val config = GameConfig.forPlayers(2)
        var gmWins = 0

        for (gameIdx in 0 until games) {
            val gmSeat = gameIdx % 2
            val expertSeat = 1 - gmSeat
            val policies: Map<PlayerId, SimPolicy> =
                mapOf(
                    PlayerId(gmSeat) to GrandmasterPolicy(seed = gameIdx.toLong() * 151L + gmSeat, budget = gmBudget),
                    PlayerId(expertSeat) to ExpertPolicy(seed = gameIdx.toLong() * 151L + expertSeat + 7700L, budget = expertBudget),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 31L + 5L, policies, checkInv = false)
            if (result.winner.raw == gmSeat) gmWins++
        }

        val winRate = gmWins.toDouble() / games
        // Heads-up fair share = 0.50; Grandmaster must clear the required 0.55 floor over Expert.
        assertTrue(
            winRate > 0.55,
            "REGRESSION: Grandmaster vs Expert (2p) win rate = ${winRate.fmt3()} ($gmWins/$games), floor 0.55",
        )
    }

    @Test
    fun grandmaster_beats_expert_4player_floor() {
        if (!heavyAiSimsEnabled()) return // skip heavy ISMCTS sim on wasm/browser (Karma ping starvation)
        // One Grandmaster (seat rotates) vs three Expert. Fair share = 0.25; the floor is a clear edge.
        val games = 140
        val config = GameConfig.forPlayers(4)
        var gmWins = 0

        for (gameIdx in 0 until games) {
            val gmSeat = gameIdx % 4
            val policies: Map<PlayerId, SimPolicy> =
                (0 until 4).associate { seat ->
                    val seed = gameIdx.toLong() * 89L + seat
                    PlayerId(seat) to
                        if (seat == gmSeat) {
                            GrandmasterPolicy(seed = seed, budget = gmBudget)
                        } else {
                            ExpertPolicy(seed = seed + 5300L, budget = expertBudget)
                        }
                }
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 47L + 3L, policies, checkInv = false)
            if (result.winner.raw == gmSeat) gmWins++
        }

        val winRate = gmWins.toDouble() / games
        // Fair share = 0.25; one deeper-searching Grandmaster among three Experts must clear a clear
        // edge over fair. The 4p free-for-all dilutes a search-depth edge (the leader gets ganged up
        // on), so the floor is a conservative 0.29 — comfortably above 0.25 fair, low enough that
        // run-to-run variance over 140 games never flakes it.
        assertTrue(
            winRate > 0.29,
            "REGRESSION: Grandmaster vs 3xExpert (4p) win rate = ${winRate.fmt3()} ($gmWins/$games), floor 0.29 (fair=0.25)",
        )
    }
}
