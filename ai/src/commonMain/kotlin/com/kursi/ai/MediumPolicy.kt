package com.kursi.ai

import com.kursi.engine.*

/**
 * Medium bot policy — hand-authored heuristics over the public PlayerView.
 *
 * Belief: global remaining[R] card-count only (no per-opponent model).
 *
 * Turn priorities:
 *   1. Forced Coup (>=10 coins) → Coup weakest.
 *   2. Coup if coins>=7 → Coup strongest (most influence, coin-break).
 *   3. Assassinate if affordable and target has 1 influence.
 *   4. Tax (claim Neta) if claim is survivable (remaining[NETA] > 0).
 *   5. Steal from richest opponent if possible.
 *   6. ForeignAid if few Neta remaining.
 *   7. Income safe fallback. Exchange avoided unless flush.
 *
 * Reactions (CHALLENGE_ACTION / CHALLENGE_BLOCK):
 *   Challenge when pHonest(R) < 0.35, i.e. remaining[R] is low vs total unseen.
 *   Otherwise Pass.
 * Reactions (BLOCK step):
 *   Block an Assassinate or Steal targeting us with ~40% bluff rate (capped to 0 when remaining==0).
 *   Block ForeignAid with ~10% chance.
 *   Otherwise Pass.
 *
 * InfluenceLoss: discard the lower-value card by a fixed ranking
 *   (NETA > BABU > BHAI > VAKIL > JUGAADU; keep variety if possible).
 * Exchange: keep the two highest-value roles by the same ranking.
 *
 * Deterministic given seed.
 *
 * Implements both [Policy] and [SimPolicy] — see [EasyPolicy]'s KDoc for why.
 */
class MediumPolicy(
    seed: Long,
) : Policy,
    SimPolicy {
    private var rng = Rng(seed)

    // Percentage dice rolls (rng.nextInt(100)). Each is "how often Medium takes this line when it
    // is available" — the whole difficulty band lives in these six numbers, so they are named and
    // grouped instead of being six bare literals scattered through decideTurn/decideReaction.
    private companion object {
        const val TaxWhenClaimSurvivablePct = 70
        const val StealFromRichestPct = 55
        const val AssassinateStrongestPct = 45
        const val ForeignAidWhenNetaScarcePct = 65
        const val TaxFallbackPct = 60

        /** Challenge threshold tau ~= 0.35, expressed on the 0..99 pHonest scale. */
        const val ChallengeMaxHonestPct = 34

        /** Role-value floor for "worth forcing a redraw on" — BABU's rank. See roleValue below. */
        const val ForceRedrawValueFloor = 5
    }

    // Role value ranking (higher = more valuable to keep).
    // PATRAKAAR (Inquisitor) sits high: it carries the Jaanch info-then-disrupt action AND has no
    // own counter, so it is roughly as valuable as BABU — slotted just under it.
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
        val alive = view.players.filter { !it.eliminated }

        // Forced Coup.
        val coupOnly = legal.all { it is Intent.DeclareAction && it.action is Action.Coup }
        if (coupOnly) return coupTarget(view, legal, preferWeak = true)

        // Voluntary Coup at >=7.
        if (myCoins >= cfg.coupCost) {
            val coupsAvail = legal.filter { it is Intent.DeclareAction && it.action is Action.Coup }
            if (coupsAvail.isNotEmpty()) {
                // Prefer strongest opponent (most influence, then most coins).
                return coupTarget(view, coupsAvail, preferWeak = false)
            }
        }

        // Assassinate when we can afford it and target is at 1 influence.
        if (myCoins >= cfg.assassinateCost) {
            val assassins =
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .filter { it.action is Action.Assassinate }
            val goodAssassin =
                assassins.firstOrNull { intent ->
                    val target = (intent.action as Action.Assassinate).target
                    opponentById(view, target)?.faceDownCount == 1
                }
            if (goodAssassin != null) return goodAssassin
        }

        // Tax if Neta claim is plausible (remaining[NETA] > 0 = not provably impossible).
        val taxIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
        if (taxIntent != null && remaining(view, Role.NETA) > 0) {
            // Additional survivability check: only Tax if fewer than half seats are likely Neta.
            val pHonestNeta = pHonest(view, Role.NETA)
            if (pHonestNeta >= 0 || remaining(view, Role.NETA) > 0) {
                // Use Tax 40% of the time when it's good; add some noise.
                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < TaxWhenClaimSurvivablePct) return taxIntent
            }
        }

        // Steal from the richest opponent.
        val steals =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Steal }
        if (steals.isNotEmpty()) {
            val richestTarget =
                steals.maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Steal).target)?.coins ?: 0
                }
            if (richestTarget != null) {
                val targetCoins = opponentById(view, (richestTarget.action as Action.Steal).target)?.coins ?: 0
                if (targetCoins >= 2) {
                    val (r, r1) = rng.nextInt(100)
                    rng = r1
                    if (r < StealFromRichestPct) return richestTarget
                }
            }
        }

        // Investigate (claim PATRAKAAR / Jaanch) — a low-risk info-then-disrupt move targeting the
        // strongest opponent, played when the PATRAKAAR claim is plausible (remaining[PATRAKAAR] > 0).
        // Only fires when PATRAKAAR is actually in this game's deck (big tables); otherwise no such intent.
        val investigates =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Investigate }
        if (investigates.isNotEmpty() && remaining(view, Role.PATRAKAAR) > 0) {
            val strongestTarget =
                investigates.maxByOrNull { intent ->
                    val opp = opponentById(view, (intent.action as Action.Investigate).target)
                    (opp?.faceDownCount ?: 0) * 10 + (opp?.coins ?: 0)
                }
            if (strongestTarget != null) {
                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < AssassinateStrongestPct) return strongestTarget
            }
        }

        // ForeignAid if few Neta (i.e., remaining[NETA] is low).
        val faIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        if (faIntent != null) {
            val netaLeft = remaining(view, Role.NETA)
            if (netaLeft <= 1) {
                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < ForeignAidWhenNetaScarcePct) return faIntent
            }
        }

        // Fall back: Tax if available, then Income, then ForeignAid, then anything.
        if (taxIntent != null) {
            val (r, r1) = rng.nextInt(100)
            rng = r1
            if (r < TaxFallbackPct) return taxIntent
        }
        val incomeIntent = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }
        if (incomeIntent != null) return incomeIntent
        if (faIntent != null) return faIntent

        // Last resort: pick randomly from non-Exchange intents.
        val nonExchange = legal.filter { !(it is Intent.DeclareAction && it.action == Action.Exchange) }
        return if (nonExchange.isNotEmpty()) randomFrom(nonExchange) else randomFrom(legal)
    }

    private fun coupTarget(
        view: PlayerView,
        coupsAvail: List<Intent>,
        preferWeak: Boolean,
    ): Intent {
        val opponents = view.players.filter { !it.eliminated && it.id != view.viewer }
        val sorted =
            if (preferWeak) {
                opponents.sortedWith(compareBy({ it.faceDownCount }, { it.coins }))
            } else {
                opponents.sortedWith(compareByDescending<OpponentView> { it.faceDownCount }.thenByDescending { it.coins })
            }
        for (opp in sorted) {
            val intent =
                coupsAvail.firstOrNull { i ->
                    (i as Intent.DeclareAction).action.let { a -> a is Action.Coup && a.target == opp.id }
                }
            if (intent != null) return intent
        }
        return coupsAvail.first()
    }

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
            ReactionStep.CHALLENGE_ACTION, ReactionStep.CHALLENGE_BLOCK -> {
                // Determine which role is being claimed.
                val claimedRole = if (phase.step == ReactionStep.CHALLENGE_BLOCK) phase.blockRole else phase.claimedRole
                if (hasChallenge && claimedRole != null) {
                    val left = remaining(view, claimedRole)
                    if (left <= 0) return legal.first { it is Intent.Challenge } // Guaranteed bluff.
                    val pHon = pHonest(view, claimedRole)
                    // Challenge threshold τ ≈ 0.35.
                    if (pHon in 0..ChallengeMaxHonestPct) return legal.first { it is Intent.Challenge }
                }
                passIntent
            }
            ReactionStep.BLOCK -> {
                if (!hasBlock) return passIntent
                val action = phase.action
                val blockRoles = legal.filterIsInstance<Intent.Block>()
                if (blockRoles.isEmpty()) return passIntent

                // Don't bluff a role we know is exhausted.
                val survivableBlocks = blockRoles.filter { remaining(view, it.role) > 0 }
                if (survivableBlocks.isEmpty()) return passIntent

                // Block thresholds.
                val threshold =
                    when (action) {
                        is Action.Assassinate -> {
                            // Always block if we're at 1 influence (survival).
                            if (view.myInfluence.size == 1) return randomFrom(survivableBlocks)
                            40 // 40% bluff-block otherwise
                        }
                        is Action.Steal -> 35
                        Action.ForeignAid -> 15
                        else -> 0
                    }

                val (r, r1) = rng.nextInt(100)
                rng = r1
                if (r < threshold) randomFrom(survivableBlocks) else passIntent
            }
        }
    }

    // ── InfluenceLoss ─────────────────────────────────────────────────────────

    private fun decideLoss(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        if (legal.size == 1) return legal.first()
        // Shed the card whose ACTUAL role (resolved via PlayerView.myCards) is lowest-value.
        // Resolving CardId -> Role directly fixes the prior role-ordinal-vs-CardId index bug:
        // myInfluence is sorted by ordinal (NETA,BHAI,BABU,...), which is NOT value order, and the
        // engine enumerates loss intents by CardId — so positional mapping shed the wrong card.
        return CardChoice.worstLoss(view, legal) ?: randomFrom(legal)
    }

    // ── Exchange ──────────────────────────────────────────────────────────────

    private fun decideExchange(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        if (legal.size == 1) return legal.first()
        // Resolve each candidate keep-set's CardIds to roles via myCards (own face-down) + Exchange.drawn
        // (own drawn cards), then keep the highest-summed-value legal combination. This will keep the
        // drawn cards over the originals whenever they are more valuable — no longer always legal.first().
        return CardChoice.bestExchange(view, legal) ?: legal.first()
    }

    // ── InvestigatePeek (Jaanch follow-up) ─────────────────────────────────────

    /**
     * After privately peeking the target's card, decide whether to force a redraw. Heuristic: disrupt
     * (force redraw) when the peeked card is valuable to the target — denying them a strong role is the
     * whole point of Jaanch. If we can't see the card (shouldn't happen for the examiner), keep it.
     */
    private fun decideInvestigatePeek(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val peek = view.phase as PhaseView.InvestigatePeek
        val peeked = peek.examinedCard
        val forceRedraw =
            legal.firstOrNull {
                it is Intent.ResolveInvestigate && it.forceRedraw
            }
        val keep =
            legal.firstOrNull {
                it is Intent.ResolveInvestigate && !it.forceRedraw
            }
        if (peeked == null) return keep ?: legal.first()
        val value = roleValue[peeked.role] ?: 0
        // Force a redraw on the target's high-value cards (value >= BABU); keep otherwise.
        return if (value >= (roleValue[Role.BABU] ?: ForceRedrawValueFloor)) {
            (forceRedraw ?: keep ?: legal.first())
        } else {
            (keep ?: legal.first())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** remaining[R] = copies of role R not yet permanently face-up (from public view). */
    private fun remaining(
        view: PlayerView,
        role: Role,
    ): Int {
        val gone = view.players.sumOf { opp -> opp.faceUpRoles.count { it == role } }
        return view.config.copiesPerRole - gone
    }

    /**
     * Global prior: probability (0..99 integer-scaled %) that a freshly drawn hidden card is [role].
     * Returns -1 if totalUnseen == 0 (degenerate — should not occur mid-game).
     */
    private fun pHonest(
        view: PlayerView,
        role: Role,
    ): Int {
        val unseenThisRole = remaining(view, role) - view.myInfluence.count { it == role }
        val totalFaceUpGone = view.players.sumOf { it.faceUpRoles.size }
        val totalUnseen = view.config.deckSize - view.myInfluence.size - totalFaceUpGone
        if (totalUnseen <= 0) return -1
        // Scale to 0..99 integer percent to avoid floating point.
        return (unseenThisRole * 100) / totalUnseen
    }

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
