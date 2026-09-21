package com.kursi.ai

import com.kursi.engine.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/** A recorded role claim by an opponent, with its challenge outcome. */
data class RoleClaim(
    val claimant: PlayerId,
    val role: Role,
    val challenged: Boolean = false,
    val survived: Boolean? = null, // null = unchallenged; true = proven; false = bluff
    val turnNumber: Int = 0,
)

/** Inferred style scalars for one opponent, EWMA-updated over the game. */
data class StyleEstimate(
    val bluffRate: Double = 0.20,
    val aggression: Double = 0.50,
    val challengeRate: Double = 0.30,
)

/** Accumulated per-opponent belief, carried in BotMemory. */
data class OpponentBelief(
    // log-space evidence accumulated on top of the base-rate prior
    val roleEvidence: MutableMap<Role, Double> = Role.entries.associateWith { 0.0 }.toMutableMap(),
    val style: StyleEstimate = StyleEstimate(),
    val claimCount: Int = 0,
    val bluffCount: Int = 0,
    /**
     * Raw style counters (feed StyleEstimate; see BotMemory.recomputeStyle).
     * Total actions this opponent has declared (Income/FDI/Tax/Steal/Assassinate/Coup/Exchange).
     */
    val actionCount: Int = 0,
    /** Of those, how many were "aggressive" (Coup / Assassinate / Steal — they attacked someone). */
    val aggressiveCount: Int = 0,
    /** How many challenges this opponent has *initiated* (whether they won or lost). */
    val challengeCount: Int = 0,
)

/**
 * Scratchpad carried across turns by a bot. The coordinator (or ExpertPolicy) calls
 * [observe] after each game event so the belief tracks claim/reveal history.
 */
class BotMemory {
    val beliefs: MutableMap<PlayerId, OpponentBelief> = mutableMapOf()
    val claimHistory: MutableList<RoleClaim> = mutableListOf()

    /**
     * Total role-claims observed across the whole table (every ActionDeclared/Blocked with a role).
     * Used as the denominator for each opponent's challengeRate: "of the claims this opponent could
     * have challenged, how often did they pull the trigger?" It is a table-wide proxy for challenge
     * *opportunities* — we don't track per-player eligibility windows here, but more claims on the
     * table means more chances to challenge, so the ratio stays well-scaled.
     */
    private var totalClaimsObserved: Int = 0

    fun beliefFor(pid: PlayerId) = beliefs.getOrPut(pid) { OpponentBelief() }

    fun observe(
        event: GameEvent,
        turnNumber: Int,
    ) {
        when (event) {
            is GameEvent.ActionDeclared -> {
                // Track aggression for EVERY declared action, role-claim or not (Coup claims no role
                // but is the most aggressive move there is, so it must count toward aggression).
                run {
                    val belief = beliefs.getOrPut(event.actor) { OpponentBelief() }
                    beliefs[event.actor] =
                        belief.copy(
                            actionCount = belief.actionCount + 1,
                            aggressiveCount = belief.aggressiveCount + if (isAggressive(event.action)) 1 else 0,
                        )
                    recomputeStyle(event.actor)
                }
                val role = event.claimedRole ?: return
                totalClaimsObserved++
                // Log this claim as unchallenged tentatively
                claimHistory.add(RoleClaim(event.actor, role, turnNumber = turnNumber))
                // Weak evidence: unchallenged claim scaled by (1 - estimated bluffRate)
                val belief = beliefs.getOrPut(event.actor) { OpponentBelief() }
                val weight = (1.0 - belief.style.bluffRate) * 0.4
                belief.roleEvidence[role] = (belief.roleEvidence[role] ?: 0.0) + weight
                // A claim is a tracked claim for bluff-rate purposes too.
                beliefs[event.actor] =
                    beliefs.getValue(event.actor).copy(
                        claimCount = beliefs.getValue(event.actor).claimCount + 1,
                    )
                recomputeStyle(event.actor)
            }
            is GameEvent.Challenged -> {
                // This opponent pulled the trigger on a challenge → drives challengeRate.
                val belief = beliefs.getOrPut(event.challenger) { OpponentBelief() }
                beliefs[event.challenger] = belief.copy(challengeCount = belief.challengeCount + 1)
                recomputeStyle(event.challenger)
            }
            is GameEvent.Blocked -> {
                totalClaimsObserved++
                val belief = beliefs.getOrPut(event.blocker) { OpponentBelief() }
                val weight = (1.0 - belief.style.bluffRate) * 0.3
                belief.roleEvidence[event.role] = (belief.roleEvidence[event.role] ?: 0.0) + weight
                claimHistory.add(RoleClaim(event.blocker, event.role, turnNumber = turnNumber))
                // A block is also a role-claim that could be a bluff.
                beliefs[event.blocker] =
                    beliefs.getValue(event.blocker).copy(
                        claimCount = beliefs.getValue(event.blocker).claimCount + 1,
                    )
                recomputeStyle(event.blocker)
            }
            is GameEvent.ChallengeRevealed -> {
                val belief = beliefs.getOrPut(event.player) { OpponentBelief() }
                if (event.hadRole) {
                    // Survived challenge = near-proof; card shuffled back to deck — strong evidence
                    belief.roleEvidence[event.role] = (belief.roleEvidence[event.role] ?: 0.0) + SurvivedChallengeEvidence
                } else {
                    // Bluff exposed — strong negative evidence for claimed role
                    belief.roleEvidence[event.role] = (belief.roleEvidence[event.role] ?: 0.0) + ExposedBluffEvidence
                    // Confirmed bluff — bump the bluff counter; bluffRate is recomputed from counters below.
                    beliefs[event.player] = belief.copy(bluffCount = belief.bluffCount + 1)
                }
                recomputeStyle(event.player)
                // Update claim history
                val last = claimHistory.indexOfLast { it.claimant == event.player && it.role == event.role }
                if (last >= 0) {
                    claimHistory[last] = claimHistory[last].copy(challenged = true, survived = event.hadRole)
                }
            }
            is GameEvent.InfluenceLost -> {
                // Card permanently revealed — strong negative evidence for that role
                val belief = beliefs.getOrPut(event.player) { OpponentBelief() }
                belief.roleEvidence[event.role] = (belief.roleEvidence[event.role] ?: 0.0) + InfluenceLostEvidence
            }
            else -> {}
        }
    }

    /**
     * Recompute all three [StyleEstimate] dimensions for [pid] from its raw counters, blended over a
     * neutral prior so early-game estimates aren't jumpy from a single sample.
     *
     *  - bluffRate     = bluffCount / claimCount      (how often their claims turned out to be bluffs)
     *  - aggression    = aggressiveCount / actionCount(how often they attacked vs. played economy)
     *  - challengeRate = challengeCount / opportunities(how trigger-happy they are on others' claims)
     *
     * Each ratio is shrunk toward the prior by a pseudo-count `k`, i.e. (obs + k·prior) / (n + k),
     * an empirical-Bayes smoother: with no data the estimate equals the prior, and it converges to the
     * raw frequency as evidence accumulates. Keeps everything deterministic (pure arithmetic).
     */
    private fun recomputeStyle(pid: PlayerId) {
        val b = beliefs.getValue(pid)
        val k = 3.0 // pseudo-count: ~3 observations before we trust the data over the prior

        val bluffRate = smooth(b.bluffCount.toDouble(), b.claimCount.toDouble(), prior = 0.20, k = k)
        val aggression = smooth(b.aggressiveCount.toDouble(), b.actionCount.toDouble(), prior = 0.50, k = k)
        // Opportunities = claims this opponent did NOT make (can't challenge your own claim).
        val opportunities = (totalClaimsObserved - b.claimCount).coerceAtLeast(0)
        val challengeRate = smooth(b.challengeCount.toDouble(), opportunities.toDouble(), prior = 0.30, k = k)

        beliefs[pid] =
            b.copy(
                style =
                    StyleEstimate(
                        bluffRate = bluffRate.coerceIn(0.0, MaxInferredBluffRate),
                        aggression = aggression.coerceIn(0.0, 1.0),
                        challengeRate = challengeRate.coerceIn(0.0, 1.0),
                    ),
            )
    }

    private fun smooth(
        observed: Double,
        n: Double,
        prior: Double,
        k: Double,
    ): Double = (observed + k * prior) / (n + k)

    /** Aggressive = a move that directly attacks another player (Coup / Assassinate / Steal). */
    private fun isAggressive(action: Action): Boolean =
        when (action) {
            is Action.Coup, is Action.Assassinate, is Action.Steal -> true
            else -> false
        }
}

/**
 * Log-evidence deltas applied to a role's posterior when the table learns something about it.
 * Ordered by how conclusive the observation is: an exposed bluff is worse news for the claimed role
 * than a survived challenge is good news, and a permanently revealed card is the strongest signal
 * of all because the card is gone rather than shuffled back.
 */
private const val SurvivedChallengeEvidence = 2.5
private const val ExposedBluffEvidence = -4.0
private const val InfluenceLostEvidence = -5.0

/**
 * Ceiling on the inferred bluff rate. Capped below 1.0 on purpose: even an opponent caught bluffing
 * every single time so far should not be modelled as a certain bluffer, or the search stops
 * considering that their next claim might be true.
 */
private const val MaxInferredBluffRate = 0.8

/**
 * Computes posterior over roles for a given opponent using a Bayesian update
 * on top of the global deck-composition prior.
 */
class BeliefModel {
    /**
     * Posterior P(this role | observations), for one opponent.
     * Returns a normalized probability map over Role.
     */
    fun posterior(
        view: PlayerView,
        belief: OpponentBelief,
    ): Map<Role, Double> {
        val cfg = view.config
        val faceUpTotal = view.players.sumOf { it.faceUpRoles.size } + view.myFaceUp.size
        val totalUnseen = cfg.deckSize - view.myInfluence.size - faceUpTotal

        val logits = mutableMapOf<Role, Double>()
        // Iterate activeRoles, NOT Role.entries: PATRAKAAR only exists on big tables, so a small-table
        // posterior must never put mass on it (it has 0 copies in the deck there).
        for (role in cfg.activeRoles) {
            val unseenR = (
                cfg.copiesPerRole -
                    view.players.sumOf { p -> p.faceUpRoles.count { it == role } } -
                    view.myFaceUp.count { it == role } -
                    view.myInfluence.count { it == role }
            )
            val baseLogit =
                if (totalUnseen > 0 && unseenR > 0) {
                    ln(unseenR.toDouble() / totalUnseen)
                } else {
                    -10.0
                }
            val evidence = belief.roleEvidence[role] ?: 0.0
            logits[role] = baseLogit + evidence
        }
        return softmax(logits)
    }

    /** P(opponent holds role in ≥1 of their k face-down cards). */
    fun pHolds(
        view: PlayerView,
        opponentId: PlayerId,
        belief: OpponentBelief,
        role: Role,
    ): Double {
        val k = view.players.firstOrNull { it.id == opponentId }?.faceDownCount ?: 0
        if (k <= 0) return 0.0
        val pSlot = posterior(view, belief)[role] ?: 0.0
        if (pSlot <= 0.0) return 0.0
        return 1.0 - (1.0 - pSlot).pow(k)
    }

    private fun softmax(logits: Map<Role, Double>): Map<Role, Double> {
        val max = logits.values.maxOrNull() ?: 0.0
        val expMap = logits.mapValues { exp(it.value - max) }
        val sum = expMap.values.sum().coerceAtLeast(1e-12)
        return expMap.mapValues { it.value / sum }
    }
}
