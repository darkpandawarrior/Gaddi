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
        /** Percentage dice rolls (rng.nextInt(100)) — Hard's difficulty band in one place. */
        const val StealWhenTargetRichPct = 60
        const val BluffJaanchPct = 25
        const val ForeignAidWhenNetaScarcePct = 70

        /** Tax-bluff rate while most NETA copies are still unseen, so a challenge is unlikely. */
        const val TaxBluffWhileNetaPlentifulPct = 20

        /** Tax-bluff rate once NETA is visibly scarce and the table is watching for the claim. */
        const val TaxBluffWhenNetaScarcePct = 30

        /** A Steal only pays when the target actually holds the full two coins it takes. */
        const val MinCoinsWorthStealing = 2

        /** remaining[NETA] at or below this makes a ForeignAid block unlikely — worth the two coins. */
        const val NetaScarceThreshold = 1

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

    /**
     * The turn priority ladder in three groups, each returning the intent it wants or null to hand
     * on to the next: take a seat out if we can, else build the economy, else take an opportunity,
     * else fall back to something safe.
     *
     * Order matters twice over: it is the priority order AND the order in which [rng] is consumed,
     * so a line that rolls its dice and then declines still advances the stream. Reordering the
     * groups, or skipping one, changes every seeded game.
     */
    private fun decideTurn(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val chosen =
            aggressiveLine(view, legal)
                ?: economicLine(view, legal)
                ?: opportunistLine(view, legal)
        if (chosen != null) return chosen

        // Fall back: Tax unconditionally, then Income, then ForeignAid.
        val fallback =
            legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
                ?: legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }
                ?: legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        if (fallback != null) return fallback

        val nonExchange = legal.filter { !(it is Intent.DeclareAction && it.action == Action.Exchange) }
        return if (nonExchange.isNotEmpty()) randomFrom(nonExchange) else randomFrom(legal)
    }

    /** Coup first (forced, then voluntary), then an Assassinate that finishes a wounded seat. */
    private fun aggressiveLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        // Forced Coup: at >= 10 coins it is the only legal action.
        if (legal.all { it is Intent.DeclareAction && it.action is Action.Coup }) {
            return coupByThreat(view, legal)
        }
        if (view.myCoins >= view.config.coupCost) {
            // Voluntary Coup — Hard always prefers it once affordable.
            val coupsAvail = legal.filter { it is Intent.DeclareAction && it.action is Action.Coup }
            if (coupsAvail.isNotEmpty()) return coupByThreat(view, coupsAvail)
        }
        if (view.myCoins < view.config.assassinateCost) return null
        // Assassinate a seat down to its last influence, richest (closest to Coup) first.
        return legal
            .filterIsInstance<Intent.DeclareAction>()
            .filter { it.action is Action.Assassinate }
            .filter { intent ->
                opponentById(view, (intent.action as Action.Assassinate).target)?.faceDownCount == 1
            }.maxByOrNull { intent ->
                opponentById(view, (intent.action as Action.Assassinate).target)?.coins ?: 0
            }
    }

    /**
     * Tax — always when we truly hold NETA (uncatchable +3), otherwise a selective bluff — then a
     * Steal from the richest seat holding at least the two coins it takes.
     */
    private fun economicLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        val tax = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
        val netaLeft = remaining(view, Role.NETA)
        if (tax != null && netaLeft > 0) {
            // Truthful Tax: always do it (100% rate — free +3, no challenge risk).
            if (view.myInfluence.contains(Role.NETA)) return tax
            // Bluff Tax: with almost every NETA still unseen a challenge is unlikely, so bluff a
            // little less often than when NETA is scarce and the table is watching for the claim.
            val netaMostlyUnseen = netaLeft >= view.config.copiesPerRole - 1
            val bluffRate = if (netaMostlyUnseen) TaxBluffWhileNetaPlentifulPct else TaxBluffWhenNetaScarcePct
            if (rollUnder(bluffRate)) return tax
        }

        val best =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Steal }
                .maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Steal).target)?.coins ?: 0
                }
        val targetCoins = best?.let { opponentById(view, (it.action as Action.Steal).target)?.coins } ?: 0
        if (best == null || targetCoins < MinCoinsWorthStealing) return null
        return best.takeIf { rollUnder(StealWhenTargetRichPct) }
    }

    /**
     * Investigate (claim PATRAKAAR / Jaanch) against the most threatening seat — free
     * info-then-disruption when we hold PATRAKAAR, a sparing bluff when we do not — then ForeignAid
     * once NETA is scarce enough that a block is unlikely.
     */
    private fun opportunistLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        val best =
            if (remaining(view, Role.PATRAKAAR) > 0) {
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .filter { it.action is Action.Investigate }
                    .maxByOrNull { intent ->
                        opponentById(view, (intent.action as Action.Investigate).target)?.let { threatScore(it) } ?: 0
                    }
            } else {
                null
            }
        if (best != null) {
            // Truthful Jaanch — uncatchable, pure info + disruption.
            if (view.myInfluence.contains(Role.PATRAKAAR)) return best
            if (rollUnder(BluffJaanchPct)) return best // occasional bluff
        }

        val foreignAid = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        val netaScarce = remaining(view, Role.NETA) <= NetaScarceThreshold
        if (foreignAid != null && netaScarce && rollUnder(ForeignAidWhenNetaScarcePct)) return foreignAid
        return null
    }

    /** Draws the next 0..99 value off [rng] and reports whether it lands under [pct]. */
    private fun rollUnder(pct: Int): Boolean {
        val (r, r1) = rng.nextInt(100)
        rng = r1
        return r < pct
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
        return when (phase.step) {
            ReactionStep.CHALLENGE_ACTION ->
                challengeOrPass(view, legal, phase.claimedRole, phase.actor, actionChallenge = true) ?: passIntent
            ReactionStep.CHALLENGE_BLOCK ->
                challengeOrPass(
                    view,
                    legal,
                    phase.blockRole,
                    phase.blocker ?: phase.actor,
                    actionChallenge = false,
                ) ?: passIntent
            ReactionStep.BLOCK -> blockOrPass(view, legal, phase, passIntent)
        }
    }

    /**
     * Challenges [claimedRole] when it looks like a bluff, or returns null to pass.
     *
     * The threshold tau moves with the stakes: lower (more cautious) when we are one card from
     * elimination, higher (more aggressive) against a claimant who is, and higher again for block
     * claims than action claims because a wrong block-challenge costs less.
     */
    private fun challengeOrPass(
        view: PlayerView,
        legal: List<Intent>,
        claimedRole: Role?,
        claimant: PlayerId,
        actionChallenge: Boolean,
    ): Intent? {
        val challenge = legal.firstOrNull { it is Intent.Challenge } ?: return null
        if (claimedRole == null) return null
        if (remaining(view, claimedRole) <= 0) return challenge // guaranteed bluff
        val claimantInfluence = opponentById(view, claimant)?.faceDownCount ?: 1
        val tau =
            when {
                view.myInfluence.size == 1 -> 0.25 // conservative when vulnerable
                claimantInfluence == 1 -> if (actionChallenge) 0.45 else 0.50 // aggressive vs wounded
                actionChallenge -> 0.35 // same as Medium's baseline
                else -> 0.40 // block claims: more aggressive, a wrong call costs less
            }
        return challenge.takeIf { pSlot(view, claimedRole) < tau }
    }

    /**
     * Picks a block for the action aimed at us, or [passIntent]. Truthful blocks come first — that
     * is Hard's main edge over Medium, which never distinguishes a held role from a bluff.
     */
    private fun blockOrPass(
        view: PlayerView,
        legal: List<Intent>,
        phase: PhaseView.Reactions,
        passIntent: Intent,
    ): Intent {
        // Never bluff a role with no remaining copies.
        val survivableBlocks = legal.filterIsInstance<Intent.Block>().filter { remaining(view, it.role) > 0 }
        if (survivableBlocks.isEmpty()) return passIntent

        // *** KEY HARD ADVANTAGE ***: Always block truthfully when holding the role.
        val truthfulBlock = survivableBlocks.firstOrNull { view.myInfluence.contains(it.role) }
        if (truthfulBlock != null) return truthfulBlock

        val action = phase.action
        // Survival: always bluff-block Assassinate at 1 influence, claiming VAKIL if that is open.
        if (action is Action.Assassinate && view.myInfluence.size == 1) {
            return survivableBlocks.firstOrNull { it.role == Role.VAKIL } ?: randomFrom(survivableBlocks)
        }

        // Belief-based bluff blocks: use P(attacker holds claimed role) to decide.
        // If P(holds) is LOW -> attacker likely bluffing -> we bluff-block safely.
        val attackerHoldsP = pHolds(view, phase.actor, Rules.claimedRole(action))
        val threshold =
            when (action) {
                is Action.Assassinate -> if (attackerHoldsP < 0.42) 45 else 0
                is Action.Steal -> if (attackerHoldsP < 0.38) 35 else 0
                Action.ForeignAid -> 12 // Low bluff-block rate for FA (no per-opponent belief needed)
                else -> 0
            }
        if (threshold <= 0) return passIntent
        return if (rollUnder(threshold)) randomFrom(survivableBlocks) else passIntent
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
