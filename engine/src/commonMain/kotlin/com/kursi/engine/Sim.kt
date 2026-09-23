package com.kursi.engine

/**
 * A player decision-maker for [SimHarness]'s own deterministic self-play fuzzer/property/golden tests.
 * It sees ONLY a [PlayerView] (never [GameState]) and the concrete legal intents — so a cheating
 * policy that reads opponents' hidden cards is structurally impossible.
 *
 * Renamed from the former engine-hosted `Policy` (ai→engine inversion, kmp-toolkit-family bots-policy
 * lane): the app-facing bot-policy abstraction now lives as a generic `Policy<View, Move>` in the
 * `com.siddharth.kmp:bots-policy` toolkit module (aliased to `com.kursi.ai.Policy` for Gaddi's
 * concrete shape). [SimPolicy] stays engine-local — it only serves [SimHarness]'s own tests, never
 * consumed by `:ai`.
 */
fun interface SimPolicy {
    fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent
}

/** Picks a uniformly-random legal intent. Deterministic given its seed — the workhorse for property/fuzz tests. */
class RandomLegalPolicy(
    seed: Long,
) : SimPolicy {
    private var rng = Rng(seed)

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents" }
        val (i, r) = rng.nextInt(legal.size)
        rng = r
        return legal[i]
    }
}

data class GameResult(
    val winner: PlayerId,
    val turns: Int,
    val steps: Int,
)

data class SimStats(
    val games: Int,
    val winsBySeat: Map<Int, Int>,
    val avgTurns: Double,
)

object SimHarness {
    /**
     * Plays one game to completion. The harness is the "coordinator": it asks [whoActsNext] for input and
     * routes the chosen intent through [applyIntent]. With [checkInv] on, every transition is invariant-checked.
     */
    fun playOut(
        config: GameConfig,
        seed: Long,
        policies: Map<PlayerId, SimPolicy>,
        maxSteps: Int = 200_000,
        checkInv: Boolean = true,
    ): GameResult {
        var state = initialState(config, seed)
        if (checkInv) checkInvariants(state)
        var steps = 0
        while (state.phase !is Phase.GameOver) {
            check(++steps <= maxSteps) { "game did not terminate within $maxSteps steps" }
            val who = whoActsNext(state) ?: break
            // Team-aware in the TEAMS variant (bots never target teammates); a no-op in free-for-all.
            val legal = teamSafeIntents(state, who)
            check(legal.isNotEmpty()) { "no legal intents for $who in ${state.phase}" }
            val intent = policies.getValue(who).decide(redact(state, who), legal)
            when (val r = applyIntent(state, intent)) {
                is ApplyOutcome.Accepted -> {
                    state = r.state
                    if (checkInv) checkInvariants(state)
                }
                is ApplyOutcome.Rejected -> error("policy returned an illegal intent: ${r.reason} [$intent]")
            }
        }
        val winner = (state.phase as Phase.GameOver).winner
        return GameResult(winner, state.turnNumber, steps)
    }

    /** Aggregate balance stats over a seed range, with one fresh policy per player per game. */
    fun playMany(
        config: GameConfig,
        seeds: LongRange,
        policyFactory: (PlayerId, Long) -> SimPolicy,
    ): SimStats {
        val winsBySeat = HashMap<Int, Int>()
        var totalTurns = 0L
        var games = 0
        for (seed in seeds) {
            val policies =
                (0 until config.seatCount).associate { seat ->
                    val pid = PlayerId(seat)
                    pid to policyFactory(pid, seed * 131 + seat)
                }
            val result = playOut(config, seed, policies)
            val seat = result.winner.raw
            winsBySeat[seat] = (winsBySeat[seat] ?: 0) + 1
            totalTurns += result.turns
            games++
        }
        return SimStats(games, winsBySeat, if (games == 0) 0.0 else totalTurns.toDouble() / games)
    }
}
