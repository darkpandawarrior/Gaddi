package com.kursi.ai

import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.engine.Role

/**
 * A per-role claim tally for one opponent: how many times they have publicly claimed [role]
 * (via an action or a block), and — of the claims that got challenged and resolved — how many
 * proved to be bluffs ([caughtBluffing]) vs. were proven true ([proven]).
 */
data class RoleClaimStat(
    val role: Role,
    val claims: Int,
    val proven: Int,
    val caughtBluffing: Int,
)

/**
 * Everything the UI is allowed to render about ONE opponent's "read", derived ENTIRELY from
 * PUBLIC information — the table-visible claim/block/reveal history accumulated in a [BotMemory]
 * (the human's own decision-coach belief). It NEVER contains an opponent's hidden cards.
 *
 * ## What each field means
 * - [posterior]      P(this opponent's face-down slot is each role), folding the deck-composition
 *                    prior with public claim/reveal evidence. Sums to ~1.0 across [Role].
 * - [pHolds]         P(opponent holds each role in *at least one* of their face-down cards
 *                    (1 − (1 − pSlot)^faceDownCount). NOT a probability distribution — each entry
 *                    is independent and they can sum past 1.0.
 * - [claimStats]     Per-role claim history with bluff-caught / proven counts.
 * - [bluffRate]      Inferred P(this opponent's next claim is a bluff) — the EWMA-smoothed style
 *                    scalar from observed bluff-caught frequency.
 * - [style]          The full inferred [StyleEstimate] (bluffRate / aggression / challengeRate).
 * - [totalClaims] / [bluffsCaught]  Headline counters for a quick "shady-meter".
 *
 * SECRECY: every field here is a function of public events only. Construct via [OpponentInsight.from].
 */
data class OpponentInsight(
    val opponentId: PlayerId,
    val seatIndex: Int,
    val eliminated: Boolean,
    val posterior: Map<Role, Double>,
    val pHolds: Map<Role, Double>,
    val claimStats: List<RoleClaimStat>,
    val bluffRate: Double,
    val style: StyleEstimate,
    val totalClaims: Int,
    val bluffsCaught: Int,
) {
    /** The single role this opponent most likely holds in a face-down slot (posterior argmax). */
    val mostLikelyRole: Role?
        get() = posterior.maxByOrNull { it.value }?.key

    companion object {
        private val beliefModel = BeliefModel()

        /**
         * Build the dossier for [opponentId] from [view] (the viewer's PUBLIC projection) and the
         * coach's [memory]. The memory must have been fed ONLY public game events — do not pass a
         * memory that has peeked at hidden state. Returns null if [opponentId] is not in the view.
         */
        fun from(
            view: PlayerView,
            memory: BotMemory,
            opponentId: PlayerId,
        ): OpponentInsight? {
            val opp = view.players.firstOrNull { it.id == opponentId } ?: return null
            val belief = memory.beliefFor(opponentId)

            val posterior = beliefModel.posterior(view, belief)
            val pHolds =
                Role.entries.associateWith { role ->
                    beliefModel.pHolds(view, opponentId, belief, role)
                }

            // Tally per-role claim history from the public claim log.
            val claimStats =
                Role.entries.map { role ->
                    val forRole = memory.claimHistory.filter { it.claimant == opponentId && it.role == role }
                    RoleClaimStat(
                        role = role,
                        claims = forRole.size,
                        proven = forRole.count { it.challenged && it.survived == true },
                        caughtBluffing = forRole.count { it.challenged && it.survived == false },
                    )
                }

            return OpponentInsight(
                opponentId = opponentId,
                seatIndex = opp.seatIndex,
                eliminated = opp.eliminated,
                posterior = posterior,
                pHolds = pHolds,
                claimStats = claimStats,
                bluffRate = belief.style.bluffRate,
                style = belief.style,
                totalClaims = belief.claimCount,
                bluffsCaught = belief.bluffCount,
            )
        }

        /** Build a dossier for every (optionally alive) opponent of the viewer, in seat order. */
        fun forAll(
            view: PlayerView,
            memory: BotMemory,
            includeEliminated: Boolean = true,
        ): List<OpponentInsight> =
            view.players
                .asSequence()
                .filter { it.id != view.viewer }
                .filter { includeEliminated || !it.eliminated }
                .sortedBy { it.seatIndex }
                .mapNotNull { from(view, memory, it.id) }
                .toList()
    }
}
