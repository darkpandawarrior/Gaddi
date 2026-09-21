package com.kursi.ai

import com.kursi.engine.*

/**
 * Hard bot policy — per-opponent belief model with improvements over MediumPolicy.
 *
 * Key improvements over Medium:
 *
 *  1. TRUTHFUL BLOCKS (primary edge): Hard ALWAYS blocks when holding the blocking role.
 *     Medium only blocks at fixed random rates (40%/35%/15% for Assassinate/Steal/FA)
 *     without checking if it actually holds the role.
 *     Hard's free blocks: Assassinate → always if we hold VAKIL.
 *                         Steal       → always if we hold BABU or JUGAADU.
 *                         ForeignAid  → always if we hold NETA.
 *     These blocks can never be successfully challenged, so they're pure value.
 *
 *  2. BETTER ECONOMY: Tax on ~90% of eligible turns (vs Medium's ~70%).
 *     +3 coins races to Coup faster. Medium's probabilistic rate leaves money on the table.
 *
 *  3. SMARTER TARGET SELECTION: Threat score = influence×3 + coins/2.
 *     Medium coups weakest (for forced) or strongest (for voluntary) but uses a blunt metric.
 *     Hard uses a combined influence+coins threat score for consistent dangerous targeting.
 *
 *  4. MATCHED CHALLENGE LOGIC: Same pSlot < 0.35 threshold as Medium for action challenges,
 *     with per-opponent adjustments (looser threshold for 1-influence claimants, tighter
 *     when we're at 1 influence).
 *
 * Challenge formula (pSlot = unseenR / totalUnseen, same as Medium):
 *   Challenge when pSlot < τ.
 *   CHALLENGE_ACTION: τ = 0.35 baseline (same as Medium), 0.45 vs 1-inf claimant, 0.25 at 1 inf.
 *   CHALLENGE_BLOCK: τ = 0.40 (more aggressive — lower cost of being wrong).
 *
 * Deterministic given seed.
 *
 * Implements both [Policy] and [SimPolicy] — see [EasyPolicy]'s KDoc for why.
 */
class HardPolicy(
    seed: Long,
) : Policy,
    SimPolicy {
    private var rng = Rng(seed)

    // PATRAKAAR (Inquisitor / Jaanch): info-then-disrupt, no own counter — valued just under BABU.
    private companion object {
        /** Percentage dice rolls (rng.nextInt(100)) — Hard's difficulty band in four numbers. */
        const val StealWhenTargetRichPct = 60
        const val BluffJaanchPct = 25
        const val ForeignAidWhenNetaScarcePct = 70

        /** A face-down influence card is worth this many coins of threat; coins count for half. */
        const val InfluenceThreatWeight = 3
        const val CoinThreatDivisor = 2

        /** Role-value floor for "worth forcing a redraw on" — BABU's rank. See roleValue below. */
        const val ForceRedrawValueFloor = 5
    }

    private val roleValue: Map<Role, Int> =
        mapOf(
            Role.NETA to 6,
            Role.BABU to 5,
            Role.PATRAKAAR to 4,
            Role.BHAI to 3,
            Role.VAKIL to 2,
            Role.JUGAADU to 1,
        )

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents supplied" }
        return when (view.phase) {
            is PhaseView.Turn -> decideTurn(view, legal)
            is PhaseView.Reactions -> decideReaction(view, legal)
            is PhaseView.InfluenceLoss -> decideLoss(view, legal)
            is PhaseView.Exchange -> decideExchange(view, legal)
            is PhaseView.InvestigatePeek -> decideInvestigatePeek(view, legal)
            is PhaseView.Over -> error("decide called after game over")
        }
    }

    // ── Turn ──────────────────────────────────────────────────────────────────

    private fun decideTurn(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val cfg = view.config
        val myCoins = view.myCoins

        // Forced Coup.
        val coupOnly = legal.all { it is Intent.DeclareAction && it.action is Action.Coup }
        if (coupOnly) return coupByThreat(view, legal)

        // Voluntary Coup (≥7) — always preferred.
        if (myCoins >= cfg.coupCost) {
            val coupsAvail = legal.filter { it is Intent.DeclareAction && it.action is Action.Coup }
            if (coupsAvail.isNotEmpty()) return coupByThreat(view, coupsAvail)
        }

        // Assassinate a 1-influence opponent (finish them off).
        if (myCoins >= cfg.assassinateCost) {
            val assassins =
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .filter { it.action is Action.Assassinate }
            val wounded =
                assassins.filter { intent ->
                    opponentById(view, (intent.action as Action.Assassinate).target)?.faceDownCount == 1
                }
            if (wounded.isNotEmpty()) {
                // Among wounded, prefer the most threatening (most coins → near Coup).
                return wounded.maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Assassinate).target)?.coins ?: 0
                } ?: wounded.first()
            }
        }

        // Tax: prefer truthful claim (we hold NETA) — never gets caught; bluff sparingly.
        val taxIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
        if (taxIntent != null && remaining(view, Role.NETA) > 0) {
            val holdsNeta = view.myInfluence.contains(Role.NETA)
            if (holdsNeta) {
                // Truthful Tax: always do it (100% rate — free +3, no challenge risk).
                return taxIntent
            } else {
                // Bluff Tax: selective — avoid getting caught too often.
                // At N=4, pSlot is slightly higher so bluffing is slightly safer.
                val netaLeft = remaining(view, Role.NETA)
                val bluffRate = if (netaLeft >= view.config.copiesPerRole - 1) 20 else 30
                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < bluffRate) return taxIntent
            }
        }

        // Steal from richest opponent with ≥2 coins.
        val steals =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Steal }
        if (steals.isNotEmpty()) {
            val best =
                steals.maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Steal).target)?.coins ?: 0
                }
            if (best != null) {
                val targetCoins = opponentById(view, (best.action as Action.Steal).target)?.coins ?: 0
                if (targetCoins >= 2) {
                    val (r, r1) = rng.nextInt(100)
                    rng = r1
                    if (r < StealWhenTargetRichPct) return best
                }
            }
        }

        // Investigate (claim PATRAKAAR / Jaanch): prefer a truthful claim (we hold PATRAKAAR) for a
        // free info-then-disrupt against the most threatening opponent; bluff it sparingly otherwise.
        val investigates =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Investigate }
        if (investigates.isNotEmpty() && remaining(view, Role.PATRAKAAR) > 0) {
            val best =
                investigates.maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Investigate).target)?.let { threatScore(it) } ?: 0
                }
            if (best != null) {
                val holdsPatrakaar = view.myInfluence.contains(Role.PATRAKAAR)
                if (holdsPatrakaar) return best // truthful Jaanch — uncatchable, pure info+disruption
                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < BluffJaanchPct) return best // occasional bluff
            }
        }

        // ForeignAid when few Neta remain.
        val faIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        if (faIntent != null && remaining(view, Role.NETA) <= 1) {
            val (r, r1) = rng.nextInt(100)
            rng = r1
            if (r < ForeignAidWhenNetaScarcePct) return faIntent
        }

        // Tax fallback.
        if (taxIntent != null) return taxIntent

        val incomeIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }
        if (incomeIntent != null) return incomeIntent
        if (faIntent != null) return faIntent

        val nonExchange = legal.filter { !(it is Intent.DeclareAction && it.action == Action.Exchange) }
        return if (nonExchange.isNotEmpty()) randomFrom(nonExchange) else randomFrom(legal)
    }

    private fun coupByThreat(
        view: PlayerView,
        coupsAvail: List<Intent>,
    ): Intent {
        val opponents = view.targetableOpponents
        val sorted = opponents.sortedByDescending { opp -> threatScore(opp) }
        for (opp in sorted) {
            val intent =
                coupsAvail.firstOrNull { i ->
                    (i as Intent.DeclareAction).action.let { a -> a is Action.Coup && a.target == opp.id }
                }
            if (intent != null) return intent
        }
        return coupsAvail.first()
    }

    private fun threatScore(opp: OpponentView): Int = opp.faceDownCount * InfluenceThreatWeight + opp.coins / CoinThreatDivisor

    // ── Reactions ─────────────────────────────────────────────────────────────

    private fun decideReaction(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val phase = view.phase as PhaseView.Reactions
        val passIntent = legal.first { it is Intent.Pass }
        val hasChallenge = legal.any { it is Intent.Challenge }
        val hasBlock = legal.any { it is Intent.Block }

        return when (phase.step) {
            ReactionStep.CHALLENGE_ACTION -> {
                val claimedRole = phase.claimedRole
                if (hasChallenge && claimedRole != null) {
                    val left = remaining(view, claimedRole)
                    if (left <= 0) return legal.first { it is Intent.Challenge } // guaranteed bluff
                    val p = pSlot(view, claimedRole)
                    val claimantInfluence = opponentById(view, phase.actor)?.faceDownCount ?: 1
                    val myInfluence = view.myInfluence.size
                    val tau =
                        when {
                            myInfluence == 1 -> 0.25 // conservative when vulnerable
                            claimantInfluence == 1 -> 0.45 // aggressive vs wounded opponents
                            else -> 0.35 // same as Medium's baseline
                        }
                    if (p < tau) return legal.first { it is Intent.Challenge }
                }
                passIntent
            }
            ReactionStep.CHALLENGE_BLOCK -> {
                val blockRole = phase.blockRole
                if (hasChallenge && blockRole != null) {
                    val left = remaining(view, blockRole)
                    if (left <= 0) return legal.first { it is Intent.Challenge } // guaranteed bluff
                    val p = pSlot(view, blockRole)
                    val blockerInfluence = opponentById(view, phase.blocker ?: phase.actor)?.faceDownCount ?: 1
                    val myInfluence = view.myInfluence.size
                    val tau =
                        when {
                            myInfluence == 1 -> 0.25 // conservative
                            blockerInfluence == 1 -> 0.50 // aggressive vs wounded blockers
                            else -> 0.40 // more aggressive than action challenges
                        }
                    if (p < tau) return legal.first { it is Intent.Challenge }
                }
                passIntent
            }
            ReactionStep.BLOCK -> {
                if (!hasBlock) return passIntent
                val action = phase.action
                val blockRoles = legal.filterIsInstance<Intent.Block>()
                if (blockRoles.isEmpty()) return passIntent

                // Never bluff a role with no remaining copies.
                val survivableBlocks = blockRoles.filter { remaining(view, it.role) > 0 }
                if (survivableBlocks.isEmpty()) return passIntent

                // *** KEY HARD ADVANTAGE ***: Always block truthfully when holding the role.
                // Medium never distinguishes truthful from bluff blocks.
                val truthfulBlocks = survivableBlocks.filter { view.myInfluence.contains(it.role) }
                if (truthfulBlocks.isNotEmpty()) return truthfulBlocks.first()

                // Survival: always bluff-block Assassinate at 1 influence.
                if (action is Action.Assassinate && view.myInfluence.size == 1) {
                    val vakils = survivableBlocks.filter { it.role == Role.VAKIL }
                    return if (vakils.isNotEmpty()) vakils.first() else randomFrom(survivableBlocks)
                }

                // Belief-based bluff blocks: use P(attacker holds claimed role) to decide.
                // If P(holds) is LOW → attacker likely bluffing → we bluff-block safely.
                val attackerHoldsP = pHolds(view, phase.actor, Rules.claimedRole(action))
                val threshold =
                    when (action) {
                        is Action.Assassinate -> if (attackerHoldsP < 0.42) 45 else 0
                        is Action.Steal -> if (attackerHoldsP < 0.38) 35 else 0
                        Action.ForeignAid -> 12 // Low bluff-block rate for FA (no per-opponent belief needed)
                        else -> 0
                    }
                if (threshold > 0) {
                    val (r, r1) = rng.nextInt(100)
                    rng = r1
                    if (r < threshold) return randomFrom(survivableBlocks)
                }
                passIntent
            }
        }
    }

    // ── InfluenceLoss ─────────────────────────────────────────────────────────

    private fun decideLoss(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        if (legal.size == 1) return legal.first()
        // Shed the card whose ACTUAL role (via PlayerView.myCards) is lowest-value. Resolving
        // CardId -> Role directly fixes the role-ordinal-vs-CardId index bug (myInfluence is ordinal-
        // sorted, loss intents are CardId-ordered, so positional mapping discarded the wrong card).
        return CardChoice.worstLoss(view, legal) ?: randomFrom(legal)
    }

    // ── Exchange ──────────────────────────────────────────────────────────────

    private fun decideExchange(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        if (legal.size == 1) return legal.first()
        // Optimal keep-set: resolve myCards (own face-down) + Exchange.drawn to roles and keep the
        // highest summed value. Keeps drawn cards whenever they beat the originals.
        return CardChoice.bestExchange(view, legal) ?: legal.first()
    }

    // ── InvestigatePeek (Jaanch follow-up) ─────────────────────────────────────

    /**
     * After privately peeking, force a redraw on the target's high-value cards (deny them a strong
     * role); keep low-value cards in place (a redraw could only help them). Uses the examiner-only
     * PeekedCard surfaced by the engine's secrecy boundary.
     */
    private fun decideInvestigatePeek(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val peek = view.phase as PhaseView.InvestigatePeek
        val keep = legal.firstOrNull { it is Intent.ResolveInvestigate && !it.forceRedraw }
        val redraw = legal.firstOrNull { it is Intent.ResolveInvestigate && it.forceRedraw }
        val role = peek.examinedCard?.role ?: return keep ?: legal.first()
        val value = roleValue[role] ?: 0
        // Disrupt the target's strong roles (>= BABU value); leave weak ones alone.
        return if (value >= (roleValue[Role.BABU] ?: ForceRedrawValueFloor)) {
            (redraw ?: keep ?: legal.first())
        } else {
            (keep ?: legal.first())
        }
    }

    // ── Belief helpers ────────────────────────────────────────────────────────

    /** remaining[R] = copiesPerRole − permanently face-up R cards. */
    private fun remaining(
        view: PlayerView,
        role: Role,
    ): Int {
        val gone = view.players.sumOf { opp -> opp.faceUpRoles.count { it == role } }
        return view.config.copiesPerRole - gone
    }

    /**
     * pSlot(R) = unseenR / totalUnseen (same formula as MediumPolicy.pHonest).
     * This is the probability a random hidden-pool slot holds role R.
     */
    private fun pSlot(
        view: PlayerView,
        role: Role,
    ): Double {
        val cfg = view.config
        val faceUpGone = view.players.sumOf { it.faceUpRoles.size }
        val unseenR = remaining(view, role) - view.myInfluence.count { it == role }
        if (unseenR <= 0) return 0.0
        val totalUnseen = cfg.deckSize - view.myInfluence.size - faceUpGone
        if (totalUnseen <= 0) return 0.0
        return unseenR.toDouble() / totalUnseen
    }

    /**
     * P(opponent holds role R | k face-down cards) = 1 − (1 − pSlot)^k.
     * Used for bluff-block decisions.
     */
    private fun pHolds(
        view: PlayerView,
        opponentId: PlayerId,
        role: Role?,
    ): Double {
        if (role == null) return 0.0
        val p = pSlot(view, role)
        val k = opponentById(view, opponentId)?.faceDownCount ?: 0
        if (k <= 0) return 0.0
        return minOf(1.0, 1.0 - powDouble(1.0 - p, k))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun opponentById(
        view: PlayerView,
        id: PlayerId,
    ): OpponentView? = view.players.firstOrNull { it.id == id }

    private fun randomFrom(list: List<Intent>): Intent {
        val (i, r) = rng.nextInt(list.size)
        rng = r
        return list[i]
    }
}

private fun powDouble(
    base: Double,
    exp: Int,
): Double {
    var result = 1.0
    repeat(exp) { result *= base }
    return result
}
