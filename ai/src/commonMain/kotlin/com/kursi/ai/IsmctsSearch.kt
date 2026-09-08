package com.kursi.ai

import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.Ismcts
import com.siddharth.kmp.botspolicy.SearchBudget
import com.siddharth.kmp.botspolicy.SearchNode
import kotlinx.coroutines.runBlocking
import kotlin.time.TimeSource

/**
 * Advice budget: smaller than the bot game budget; targets ~200-400 ms on a human's turn.
 * [SearchBudget.rolloutHorizon] is the *2-player* base horizon — see [effectiveRolloutHorizon] for how
 * it's scaled up per seat count before being handed to [Ismcts.search].
 */
val ADVICE_BUDGET = SearchBudget(maxMillis = 350L, maxIterations = 3000, rolloutHorizon = 10)

/**
 * Effective rollout horizon for a table of [seatCount] players.
 *
 * A "round" (everyone acting once) is roughly `seatCount` action-turns, and each action can fan out
 * into several reaction sub-plies (challenge/block windows, influence-loss choices). We scale the base
 * (2-player) horizon by seat count over 2, so the rollout still reaches a comparable *number of
 * rounds* deep regardless of table size, instead of dying inside the first round at 10p.
 *
 *   2p -> base (12)        4p -> 2x base (24)        10p -> 5x base (60)
 *
 * Capped to keep big-table rollouts bounded (cost per ply is higher at 10p).
 */
fun effectiveRolloutHorizon(
    base: Int,
    seatCount: Int,
): Int {
    val scaled = base * seatCount / 2
    return scaled.coerceIn(base, base * 6)
}

/** Per-move value returned by [IsmctsSearch.evaluate]. */
data class MoveValue(
    val intent: Intent,
    /** Normalised win-probability estimate in [0, 1]. */
    val winProb: Double,
    /** Fraction of root visits this move received (visit share). */
    val visitShare: Double,
)

class IsmctsSearch(
    private val determinizer: Determinizer,
    private val budget: SearchBudget,
    rolloutSeed: Long,
    /**
     * Opponent-model EXPLOITATION gain (GRANDMASTER tier). 1.0 = Nash-leaning Expert behaviour: the
     * leaf evaluation reacts to inferred opponent style only as much as Expert does. >1.0 makes the
     * static evaluation deviate *harder* from Nash toward exploiting the read — an above-neutral
     * attacker's material looms larger, a trigger-happy table discounts our concealment more — so the
     * search actively bends its move choice toward punishing the tendencies the belief model has
     * inferred. Deterministic: it only rescales fixed coefficients, no extra randomness.
     */
    private val exploitGain: Double = 1.0,
) {
    // Dedicated RNG stream for rollout-policy seeding. The generic Ismcts shell calls the injected
    // rollout-policy FACTORY once per rollout ply; we hand it a fresh HardPolicy(seed) each time,
    // seeded from this stream — reproducing the pre-inversion per-ply reseeding EXACTLY. This is
    // load-bearing for bot strength: a single shared rollout policy dropped Grandmaster 4p to 0.20
    // (below its 0.29 floor). Deterministic given rolloutSeed.
    private var rolloutRng = Rng(rolloutSeed)

    fun chooseIntent(
        view: PlayerView,
        legal: List<Intent>,
        memory: BotMemory,
        rng: Rng,
    ): Intent {
        if (legal.size == 1) return legal.single()
        // ponytail: bot decisions go through the non-suspend Policy interface (ExpertPolicy/
        // GrandmasterPolicy), so this stays synchronous — runBlocking below reproduces the old
        // uncancellable behavior exactly. Only the human decision-coach path (evaluate) needs
        // mid-search cancellation; see runSearchSuspend.
        val root = runBlocking { runSearchSuspend(view, legal, memory, rng) }

        if (root.children.isEmpty() || root.children.values.all { it.visits == 0 }) {
            return legal.first()
        }

        // Robust child: most visited
        val best =
            root.children.entries
                .filter { it.value.visits > 0 }
                .maxByOrNull { it.value.visits }
                ?.key
                ?: return legal.first()
        return legal.firstOrNull { it == best } ?: legal.first()
    }

    /**
     * Evaluate all root-legal moves and return their value estimates.
     * Uses [adviceBudget] (default: [ADVICE_BUDGET]) — smaller than the bot game budget
     * so advice is responsive on a human's turn (~200-400 ms).
     *
     * Returns one [MoveValue] per element of [legal], in the same order, with normalised
     * win-probability and visit share. Never throws; falls back to uniform 0.5 on error.
     *
     * `suspend`: cancelling the calling coroutine (the human moves on before the ~200-400ms
     * search finishes) stops the search at the next iteration boundary instead of burning the
     * whole budget — see [runSearchSuspend] / [Ismcts.search].
     */
    suspend fun evaluate(
        view: PlayerView,
        legal: List<Intent>,
        memory: BotMemory,
        rng: Rng,
        adviceBudget: SearchBudget = ADVICE_BUDGET,
    ): List<MoveValue> {
        if (legal.isEmpty()) return emptyList()
        if (legal.size == 1) return listOf(MoveValue(legal.single(), winProb = 0.5, visitShare = 1.0))

        val root = runSearchSuspend(view, legal, memory, rng, overrideBudget = adviceBudget)

        val totalVisits =
            root.children.values
                .sumOf { it.visits }
                .coerceAtLeast(1)

        return legal.map { intent ->
            val node = root.children[intent]
            if (node == null || node.visits == 0) {
                MoveValue(intent, winProb = 0.0, visitShare = 0.0)
            } else {
                MoveValue(
                    intent = intent,
                    winProb = node.meanReward().coerceIn(0.0, 1.0),
                    visitShare = node.visits.toDouble() / totalVisits,
                )
            }
        }
    }

    /**
     * Runs ISMCTS (via the generic [Ismcts] shell) and returns the root node with all children
     * populated. `suspend`: delegates straight to [Ismcts.search], so cancelling the calling
     * coroutine stops the loop at the next iteration boundary (see [Ismcts.search]'s own kdoc)
     * instead of only between whole calls, as [chooseIntent]'s `runBlocking` wrapper still does.
     */
    private suspend fun runSearchSuspend(
        view: PlayerView,
        legal: List<Intent>,
        memory: BotMemory,
        rng: Rng,
        overrideBudget: SearchBudget = budget,
    ): SearchNode<Intent> {
        // Snapshot opponent style once per search: aggression + challengeRate drive the leaf
        // evaluation (staticEval) so the *style* the belief model infers actually changes move choice,
        // not just the role posterior used for determinization. Captured here (not per-iterate) because
        // memory is immutable for the duration of a single decision.
        val style = StyleContext.from(view, memory, exploitGain)
        val horizon = effectiveRolloutHorizon(overrideBudget.rolloutHorizon, view.config.seatCount)
        val timeMark = TimeSource.Monotonic.markNow()
        var r = rng

        val engine =
            Ismcts<GameState, Intent, PlayerView, PlayerId>(
                rules = KursiRules,
                // Fresh HardPolicy per rollout ply, seeded from the dedicated rolloutRng — reproduces
                // the pre-inversion per-ply reseeding that the strength floors depend on.
                rolloutPolicy = {
                    val (seed, r1) = rolloutRng.nextLong()
                    rolloutRng = r1
                    HardPolicy(seed)
                },
                staticEval = { state, viewer -> staticEval(state, viewer, style) },
                budget = overrideBudget,
            )

        return engine.search(
            determinize = {
                try {
                    val (detState, r1) = determinizer.sample(view, memory, r)
                    r = r1
                    detState
                } catch (e: Exception) {
                    val (_, r2) = r.nextLong()
                    r = r2
                    throw e
                }
            },
            legal = legal,
            viewer = view.viewer,
            rolloutHorizon = horizon,
            elapsedMillis = { timeMark.elapsedNow().inWholeMilliseconds },
        )
    }

    private fun staticEval(
        state: GameState,
        viewer: PlayerId,
        style: StyleContext,
    ): Double {
        if (state.phase is Phase.GameOver) {
            return if ((state.phase as Phase.GameOver).winner == viewer) 1.0 else 0.0
        }
        if (!state.isAlive(viewer)) return 0.0
        val myInf = state.influenceCount(viewer).toDouble()
        val myCoins = state.player(viewer).coins.toDouble()
        val alivePlusMe = state.alivePlayers()
        val opponents = alivePlusMe.filter { it.id != viewer }

        // ── Aggression-weighted threat ────────────────────────────────────────────
        // An opponent's raw material is not an equal threat across opponents: a high-aggression player
        // (one the belief model has seen Coup/Assassinate/Steal repeatedly) will *convert* coins and
        // influence into pressure on me, while a passive economy-player sits on the same material
        // benignly. So we scale each opponent's contribution to the "danger" denominator by their
        // inferred aggression. weight = 1 + AGGR_GAIN·(aggression − neutral): an above-neutral attacker
        // looms larger (their material counts for more, lowering my eval), a below-neutral one less.
        // Falls back to neutral (weight 1.0) for opponents we have no read on, so an info-free state
        // evaluates exactly as before — this only *colours* the race once a style read exists.
        val oppInf = opponents.sumOf { state.influenceCount(it.id) * style.threatWeight(it.id) }
        val oppCoins = opponents.sumOf { it.coins * style.threatWeight(it.id) }
        val infTerm = (myInf * 2.0) / (myInf * 2.0 + oppInf * 2.0 + 1.0)
        val coinTerm = myCoins / (myCoins + oppCoins + 1.0)

        // ── Information-asymmetry term ────────────────────────────────────────────
        // Kursi is a deduction game, not just a material race. Two states with identical
        // influence+coins are NOT equal: the one where opponents have revealed cards (face-up,
        // forced by lost challenges / influence loss) leaks their roles, collapsing my uncertainty
        // about what they can block/challenge — that is a real, exploitable edge. Conversely, my own
        // face-up cards leak MY roles to them. So we reward "opponent exposure minus my exposure".
        //
        // exposure(p) = revealed roles as a fraction of p's total influence cards.
        //   - An opponent with 1 of 2 cards face-up is half-read; with their last card down they are
        //     still partly legible. We average per-opponent so a 10p table isn't dominated by raw counts.
        //   - My own exposure is subtracted: keeping my roles hidden is worth defending.
        // The term is mapped into [0,1] (0.5 = symmetric information) and given a modest weight so it
        // tie-breaks/colours the material evaluation without overriding a clearly winning material line.
        val infoTerm: Double =
            run {
                if (opponents.isEmpty()) return@run 0.5
                val oppExposure = opponents.map { exposureOf(state, it.id) }.average()
                val myExposure = exposureOf(state, viewer)
                // ── challengeRate-weighted concealment ────────────────────────────────
                // My hidden (face-down) cards are an information edge — but only as durable as my claims.
                // At a trigger-happy table (high mean challengeRate) my concealed roles are likely to be
                // *forced* into the open by challenges, so the value of "being less exposed than my
                // opponents" is discounted: hidden info I can't keep hidden isn't worth a full unit.
                // At a passive table the asymmetry is worth its full weight. concealValue scales the part
                // of the asymmetry that comes from MY low exposure (myExposure < oppExposure). When the
                // table never challenges (rate→0) this is 1.0 → unchanged from before; as the mean rate
                // climbs it shrinks toward CONCEAL_FLOOR, never inverting the sign.
                val concealValue = style.concealValue
                val rawAsym = (oppExposure - myExposure)
                // Discount only the favourable (positive) portion driven by my concealment.
                val adjAsym = if (rawAsym > 0.0) rawAsym * concealValue else rawAsym
                0.5 + 0.5 * adjAsym.coerceIn(-1.0, 1.0)
            }

        // Weights: material still dominates (influence 0.74, coins 0.18), information adds a modest
        // 0.08 — enough to make the bot value forcing reveals / protecting its own cards as a tie-break
        // and soft preference, without letting the heuristic override a clearly winning material line.
        return (0.74 * infTerm + 0.18 * coinTerm + 0.08 * infoTerm).coerceIn(0.0, 1.0)
    }

    /**
     * Fraction of [pid]'s cards that are publicly revealed (face-up), in [0, 1].
     * 0.0 = fully hidden (both influence cards face-down), 1.0 = everything they ever held is face-up
     * (i.e. eliminated/fully exposed). Higher = less role-uncertainty for an observer.
     */
    private fun exposureOf(
        state: GameState,
        pid: PlayerId,
    ): Double {
        val faceUp = state.faceUpCards(pid).size
        val faceDown = state.faceDownInfluence(pid).size
        val total = faceUp + faceDown
        return if (total <= 0) 1.0 else faceUp.toDouble() / total
    }
}

/**
 * Per-decision snapshot of the inferred opponent *style* that the leaf evaluation ([staticEval])
 * consumes. This is the bridge that makes [StyleEstimate.aggression] and [StyleEstimate.challengeRate]
 * functional signals (they change move choice), rather than computed-then-discarded scalars.
 *
 *  - [threatWeight]: aggression → how much an opponent's material counts against me in the race.
 *  - [concealValue]: mean table challengeRate → how durable my hidden-information edge is.
 *
 * Built once per search from the immutable [BotMemory]; neutral (no-op) when there is no read on the
 * table, so an information-free position evaluates exactly as it did before style was wired in.
 */
internal class StyleContext private constructor(
    private val threatWeights: Map<PlayerId, Double>,
    /** In [CONCEAL_FLOOR, 1.0]; 1.0 = passive table (concealment fully valued), low = trigger-happy. */
    val concealValue: Double,
) {
    fun threatWeight(id: PlayerId): Double = threatWeights[id] ?: 1.0

    companion object {
        private const val NEUTRAL_AGGR = 0.50 // StyleEstimate.aggression prior
        private const val NEUTRAL_CHALLENGE = 0.30 // StyleEstimate.challengeRate prior
        private const val AGGR_GAIN = 0.8 // ±0.4 swing in threat weight across the aggression range
        private const val CONCEAL_FLOOR = 0.4 // most we discount concealment at a fully trigger-happy table
        private const val CONCEAL_GAIN = 1.0

        fun from(
            view: PlayerView,
            memory: BotMemory,
            exploitGain: Double = 1.0,
        ): StyleContext {
            val g = exploitGain.coerceIn(1.0, 4.0)
            val opponents = view.players.filter { it.id != view.viewer }
            val weights =
                opponents.associate { opp ->
                    val aggr = memory.beliefs[opp.id]?.style?.aggression ?: NEUTRAL_AGGR
                    // weight = 1 + (gain·exploitGain)·(aggr − neutral); coerced positive so it never flips
                    // material sign. A higher exploitGain (GRANDMASTER) widens the swing so an above-neutral
                    // attacker's material counts for much more — the search races harder to neutralise them.
                    opp.id to (1.0 + AGGR_GAIN * g * (aggr - NEUTRAL_AGGR)).coerceIn(0.4, 1.8)
                }
            // Table-level challenge pressure = mean of opponents' challengeRate (neutral when unread).
            val meanChallenge =
                if (opponents.isEmpty()) {
                    NEUTRAL_CHALLENGE
                } else {
                    opponents.map { memory.beliefs[it.id]?.style?.challengeRate ?: NEUTRAL_CHALLENGE }.average()
                }
            // Above-neutral challenge pressure discounts concealment toward CONCEAL_FLOOR; at/below
            // neutral it stays 1.0 (we don't reward a passive table beyond full value). exploitGain
            // sharpens the discount so a trigger-happy table is read as even more dangerous to bluff into.
            val excess = (meanChallenge - NEUTRAL_CHALLENGE).coerceAtLeast(0.0)
            val conceal = (1.0 - CONCEAL_GAIN * g * excess).coerceIn(CONCEAL_FLOOR, 1.0)
            return StyleContext(weights, conceal)
        }
    }
}
