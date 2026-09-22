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
    // is available" — the whole difficulty band lives in these numbers, so they are named and
    // grouped instead of being bare literals scattered through decideTurn/decideReaction.
    private companion object {
        const val TaxWhenClaimSurvivablePct = 70
        const val StealFromRichestPct = 55
        const val InvestigateStrongestPct = 45
        const val ForeignAidWhenNetaScarcePct = 65
        const val TaxFallbackPct = 60

        /** How often Medium bluff-blocks an Assassinate that is not yet lethal to it. */
        const val BlockAssassinatePct = 40

        /** How often Medium blocks a Steal aimed at it. Below Assassinate: coins are recoverable. */
        const val BlockStealPct = 35

        /** How often Medium blocks ForeignAid. Rare: it costs a Neta claim to deny two coins. */
        const val BlockForeignAidPct = 15

        /** Challenge threshold tau ~= 0.35, expressed on the 0..99 pHonest scale. */
        const val ChallengeMaxHonestPct = 34

        /** Role-value floor for "worth forcing a redraw on" — BABU's rank. See roleValue below. */
        const val ForceRedrawValueFloor = 5

        /**
         * Weight on influence when ranking "strongest opponent" for Investigate: one extra face-down
         * card outranks any coin pile a seat can legally hold (coups cap it well under 10).
         */
        const val InfluenceWeightVsCoins = 10

        /** A Steal only pays when the target actually holds the full two coins it takes. */
        const val MinCoinsWorthStealing = 2

        /** remaining[NETA] at or below this makes a ForeignAid block unlikely — worth the two coins. */
        const val NetaScarceThreshold = 1
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

        // Fall back: Tax on a looser roll, then Income, then ForeignAid.
        val fallback =
            legal
                .firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
                ?.takeIf { rollUnder(TaxFallbackPct) }
                ?: legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }
                ?: legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        if (fallback != null) return fallback

        // Last resort: pick randomly from non-Exchange intents.
        val nonExchange = legal.filter { !(it is Intent.DeclareAction && it.action == Action.Exchange) }
        return if (nonExchange.isNotEmpty()) randomFrom(nonExchange) else randomFrom(legal)
    }

    /** Coup first (forced, then voluntary), then an Assassinate that actually finishes a seat. */
    private fun aggressiveLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        // Forced Coup: at >= 10 coins it is the only legal action, so spend it on the weakest seat.
        if (legal.all { it is Intent.DeclareAction && it.action is Action.Coup }) {
            return coupTarget(view, legal, preferWeak = true)
        }
        if (view.myCoins >= view.config.coupCost) {
            val coupsAvail = legal.filter { it is Intent.DeclareAction && it.action is Action.Coup }
            // Voluntary Coup: prefer the strongest opponent (most influence, then most coins).
            if (coupsAvail.isNotEmpty()) return coupTarget(view, coupsAvail, preferWeak = false)
        }
        if (view.myCoins < view.config.assassinateCost) return null
        // Assassinate only when it finishes a seat that is down to its last influence.
        return legal
            .filterIsInstance<Intent.DeclareAction>()
            .filter { it.action is Action.Assassinate }
            .firstOrNull { intent ->
                opponentById(view, (intent.action as Action.Assassinate).target)?.faceDownCount == 1
            }
    }

    /** Tax while the Neta claim is not provably a bluff, then Steal from the richest worthwhile seat. */
    private fun economicLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        val tax = legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
        if (tax != null && remaining(view, Role.NETA) > 0 && rollUnder(TaxWhenClaimSurvivablePct)) return tax

        val richest =
            legal
                .filterIsInstance<Intent.DeclareAction>()
                .filter { it.action is Action.Steal }
                .maxByOrNull { intent ->
                    opponentById(view, (intent.action as Action.Steal).target)?.coins ?: 0
                } ?: return null
        val targetCoins = opponentById(view, (richest.action as Action.Steal).target)?.coins ?: 0
        if (targetCoins < MinCoinsWorthStealing) return null
        return richest.takeIf { rollUnder(StealFromRichestPct) }
    }

    /**
     * Investigate (claim PATRAKAAR / Jaanch) against the strongest seat — a low-risk
     * info-then-disrupt move played while the claim is plausible — then ForeignAid once Neta is
     * scarce enough that a block is unlikely. Investigate is only reachable when PATRAKAAR is in
     * this game's deck (big tables); smaller decks never offer the intent.
     */
    private fun opportunistLine(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        val strongest =
            if (remaining(view, Role.PATRAKAAR) > 0) {
                legal
                    .filterIsInstance<Intent.DeclareAction>()
                    .filter { it.action is Action.Investigate }
                    .maxByOrNull { intent ->
                        val opp = opponentById(view, (intent.action as Action.Investigate).target)
                        (opp?.faceDownCount ?: 0) * InfluenceWeightVsCoins + (opp?.coins ?: 0)
                    }
            } else {
                null
            }
        if (strongest != null && rollUnder(InvestigateStrongestPct)) return strongest

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
        return when (phase.step) {
            ReactionStep.CHALLENGE_ACTION, ReactionStep.CHALLENGE_BLOCK ->
                challengeOrPass(view, legal, phase) ?: passIntent
            ReactionStep.BLOCK -> blockOrPass(view, legal, phase, passIntent)
        }
    }

    /**
     * Returns a Challenge when the claim looks like a bluff — provably one (no copies of the role
     * left) or probably one (pHonest under the tau threshold) — and null to pass otherwise.
     */
    private fun challengeOrPass(
        view: PlayerView,
        legal: List<Intent>,
        phase: PhaseView.Reactions,
    ): Intent? {
        val challenge = legal.firstOrNull { it is Intent.Challenge } ?: return null
        // Determine which role is being claimed.
        val claimedRole =
            if (phase.step == ReactionStep.CHALLENGE_BLOCK) phase.blockRole else phase.claimedRole
        if (claimedRole == null) return null
        if (remaining(view, claimedRole) <= 0) return challenge // Guaranteed bluff.
        // Challenge threshold tau ~= 0.35.
        return challenge.takeIf { pHonest(view, claimedRole) in 0..ChallengeMaxHonestPct }
    }

    /**
     * Picks a block for the action aimed at us, or [passIntent]. Blocks whose role is provably
     * exhausted are dropped first, so Medium never bluffs a claim the table can already disprove.
     */
    private fun blockOrPass(
        view: PlayerView,
        legal: List<Intent>,
        phase: PhaseView.Reactions,
        passIntent: Intent,
    ): Intent {
        // Don't bluff a role we know is exhausted.
        val survivableBlocks = legal.filterIsInstance<Intent.Block>().filter { remaining(view, it.role) > 0 }
        if (survivableBlocks.isEmpty()) return passIntent

        // Always block a lethal Assassinate — survival beats the odds.
        val lethalAssassinate = phase.action is Action.Assassinate && view.myInfluence.size == 1
        if (lethalAssassinate) return randomFrom(survivableBlocks)

        val threshold =
            when (phase.action) {
                is Action.Assassinate -> BlockAssassinatePct
                is Action.Steal -> BlockStealPct
                Action.ForeignAid -> BlockForeignAidPct
                else -> 0
            }
        return if (rollUnder(threshold)) randomFrom(survivableBlocks) else passIntent
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
