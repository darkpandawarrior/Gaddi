package com.kursi.ai

import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Strength regression guards for the EXPERT (ISMCTS) tier across table sizes.
 *
 * Why this exists: the ISMCTS evaluation function and rollout horizon are tuning-sensitive. A future
 * "harmless" refactor of [IsmctsSearch.staticEval], the information-asymmetry term, or
 * [effectiveRolloutHorizon] could silently weaken the bot while every functional test stays green
 * (games still complete, no crashes). These seeded sims pin Expert's edge over Hard at 2p, 4p, and 10p
 * so any such regression trips a red test instead of shipping a weaker bot.
 *
 * Thresholds are deliberately *conservative* floors — comfortably below observed strength so normal
 * run-to-run variance never flakes them, but high enough that a real regression (Expert collapsing to
 * Hard-parity or worse) is caught. At an n-player table a policy with no edge wins 1/n of the time, so
 * each floor is stated as "meaningfully above fair share".
 *
 * Budget is sized to stay within the configured 4g JVM test heap (see GaddiKmpPureConventionPlugin):
 * fewer iterations than the in-game budget, game counts kept modest, especially at 10p where each game
 * is far heavier.
 */
class ExpertStrengthRegressionTest {
    // Compact-but-real search budget. rolloutHorizon is the *base*; IsmctsSearch scales it with
    // seatCount via effectiveRolloutHorizon, so 10p rollouts still reach meaningful depth.
    private val budget =
        SearchBudget(
            maxMillis = 8_000L, // generous wall-clock so the iteration cap (not the clock) bounds search
            maxIterations = 700,
            rolloutHorizon = 12,
        )

    private fun Double.fmt3() = ((this * 1000).roundToInt().toDouble() / 1000.0).toString()

    @Test
    fun expert_beats_hard_2player_regressionFloor() {
        if (!heavyAiSimsEnabled()) return // skip heavy ISMCTS sim on wasm/browser (Karma ping starvation)
        val games = 200
        val config = GameConfig.forPlayers(2)
        var expertWins = 0

        for (gameIdx in 0 until games) {
            val expertSeat = gameIdx % 2
            val hardSeat = 1 - expertSeat
            val policies: Map<PlayerId, SimPolicy> =
                mapOf(
                    PlayerId(expertSeat) to ExpertPolicy(seed = gameIdx.toLong() * 149L + expertSeat, budget = budget),
                    PlayerId(hardSeat) to HardPolicy(seed = gameIdx.toLong() * 149L + hardSeat + 8100L),
                )
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 29L + 5L, policies, checkInv = false)
            if (result.winner.raw == expertSeat) expertWins++
        }

        val winRate = expertWins.toDouble() / games
        // Heads-up fair share = 0.50; Expert must clear a conservative 0.56 floor.
        assertTrue(
            winRate > 0.56,
            "REGRESSION: Expert vs Hard (2p) win rate = ${winRate.fmt3()} ($expertWins/$games), floor 0.56",
        )
    }

    @Test
    fun expert_beats_hard_4player_regressionFloor() {
        if (!heavyAiSimsEnabled()) return // skip heavy ISMCTS sim on wasm/browser (Karma ping starvation)
        // One Expert (seat rotates) vs three Hard. Fair share = 0.25.
        val games = 160
        val config = GameConfig.forPlayers(4)
        var expertWins = 0

        for (gameIdx in 0 until games) {
            val expertSeat = gameIdx % 4
            val policies: Map<PlayerId, SimPolicy> =
                (0 until 4).associate { seat ->
                    val seed = gameIdx.toLong() * 97L + seat
                    PlayerId(seat) to
                        if (seat == expertSeat) {
                            ExpertPolicy(seed = seed, budget = budget)
                        } else {
                            HardPolicy(seed = seed + 6200L)
                        }
                }
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 43L + 2L, policies, checkInv = false)
            if (result.winner.raw == expertSeat) expertWins++
        }

        val winRate = expertWins.toDouble() / games
        // Fair share = 0.25; Expert must clear 0.33 (a clear edge over three Hard opponents).
        assertTrue(
            winRate > 0.33,
            "REGRESSION: Expert vs 3xHard (4p) win rate = ${winRate.fmt3()} ($expertWins/$games), floor 0.33 (fair=0.25)",
        )
    }

    @Test
    fun expert_beats_hard_10player_regressionFloor() {
        if (!heavyAiSimsEnabled()) return // skip heavy ISMCTS sim on wasm/browser (Karma ping starvation)
        // One Expert (seat rotates) vs nine Hard at a big table. Fair share = 0.10.
        // This is the table size the rollout-horizon scaling targets — guard that the bot still has a
        // real edge there, not just at heads-up. Kept to a modest game count: 10p games are heavy.
        val games = 80
        val config = GameConfig.forPlayers(10)
        var expertWins = 0

        for (gameIdx in 0 until games) {
            val expertSeat = gameIdx % 10
            val policies: Map<PlayerId, SimPolicy> =
                (0 until 10).associate { seat ->
                    val seed = gameIdx.toLong() * 113L + seat
                    PlayerId(seat) to
                        if (seat == expertSeat) {
                            ExpertPolicy(seed = seed, budget = budget)
                        } else {
                            HardPolicy(seed = seed + 4400L)
                        }
                }
            val result = SimHarness.playOut(config, seed = gameIdx.toLong() * 71L + 7L, policies, checkInv = false)
            if (result.winner.raw == expertSeat) expertWins++
        }

        val winRate = expertWins.toDouble() / games
        // Fair share = 0.10; Expert must clear 0.16 — a clear edge against nine Hard opponents.
        assertTrue(
            winRate > 0.16,
            "REGRESSION: Expert vs 9xHard (10p) win rate = ${winRate.fmt3()} ($expertWins/$games), floor 0.16 (fair=0.10)",
        )
    }
}
