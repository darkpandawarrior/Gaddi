package com.kursi.ai.persona

import com.kursi.ai.BeliefModel
import com.kursi.ai.BotMemory
import com.kursi.ai.ExpertPolicy
import com.kursi.ai.GrandmasterPolicy
import com.kursi.ai.Policy
import com.kursi.engine.*

/**
 * Persona decorator over a base tier [Policy].
 *
 * ## Design contract
 * - Wraps any [Policy] (Easy/Medium/Hard/Expert) with persona biases.
 * - Biases are *re-weightings*, never overrides: a clearly winning move is
 *   never discarded just because the persona profile disfavors it.
 * - All bias math is deterministic given [seed].
 *
 * ## What changes vs the base policy
 *
 * 1. **Bluff gates** (Turn phase): if the base returns Tax/Steal, we sometimes
 *    suppress or promote it based on [PersonalityProfile.bluffRate] ±
 *    [PersonalityProfile.predictability] jitter.  High bluffRate → keep bluff
 *    actions more aggressively.  Low bluffRate → fall back to safer Income.
 *
 * 2. **Target selection** (Turn phase): if the base chose a targeted action
 *    (Coup/Assassinate/Steal), we may swap the target according to
 *    [PersonalityProfile.targetingBias].  We only swap to another *legal* target
 *    of the same action type, so the engine never sees an illegal intent.
 *
 * 3. **Challenge aggression** (Reactions/CHALLENGE_ACTION, CHALLENGE_BLOCK):
 *    if the base returned Pass but [pSlot] < our persona τ, we upgrade to
 *    Challenge; conversely if the base returned Challenge but pSlot > persona τ,
 *    we downgrade to Pass.  τ = lerp(0.20, 0.50, challengeAggression).
 *
 * 4. **Grudge tracking**: whenever the engine signals someone challenged/hit
 *    this bot (via [notifyHit]), the [GrudgeMap] is updated.  VINDICTIVE
 *    targeting uses it.
 *
 * The decorator is intentionally *thin*: it does not duplicate the base policy's
 * threat-assessment, bluff-detection, or ISMCTS search. It only steers the
 * *output* of those systems in a personality-consistent direction.
 */
class PersonaPolicy(
    val persona: BotPersona,
    val base: Policy,
    private val seed: Long,
) : Policy,
    SimPolicy {
    private val p = persona.personality
    private var rng = Rng(seed)
    private val grudge = GrudgeMap()

    // ── Belief posterior ──────────────────────────────────────────────────────
    // The persona's challenge/block decision should consult the *posterior* over a claimant's roles
    // (which folds in observed claim/reveal history + per-opponent style), not just the static
    // deck-frequency prior. When this decorator wraps an [ExpertPolicy] we share that policy's live
    // [BotMemory] so the persona reasons over the same evidence the search does; otherwise we keep our
    // own memory that callers feed via [observe]. Either way [pHolds] reflects deduction, not just odds.
    private val beliefModel = BeliefModel()

    // Share the live belief memory of whichever search-based tier we wrap (Expert or Grandmaster) so
    // the persona's challenge/block decisions reason over the same evidence the search does.
    private val sharedMemory: BotMemory =
        when (base) {
            is ExpertPolicy -> base.memory
            is GrandmasterPolicy -> base.memory
            else -> BotMemory()
        }

    // ── External notification ─────────────────────────────────────────────────

    /**
     * Call after each game event to keep the grudge map updated.
     * Only matters when [p.vindictiveness] > 0 and [p.targetingBias] == VINDICTIVE.
     *
     * [weight] is scaled by [PersonalityProfile.vindictiveness] so a mildly vindictive persona builds
     * a grudge more slowly than a fully vindictive one; the raw weight is at least 1 once it fires so a
     * single hit always registers.
     */
    fun notifyHit(
        attacker: PlayerId,
        weight: Int = 1,
    ) {
        if (p.vindictiveness <= 0f) return
        val scaled = (weight * (0.5f + p.vindictiveness)).toInt().coerceAtLeast(1)
        grudge.add(attacker, scaled)
    }

    /**
     * Call once per turn this bot survives so old grudges fade (recent attackers dominate targeting).
     * No-op for non-vindictive personas — they never accumulate grudges to decay.
     */
    fun notifyTurnPassed() {
        if (p.vindictiveness > 0f) grudge.decay()
    }

    /** Current grudge score this bot holds against [pid] (0.0 = none). Exposed for diagnostics/tests. */
    fun grudgeAgainst(pid: PlayerId): Double = grudge.scoreFor(pid)

    /**
     * Feed a game event into this persona's belief memory so its challenge/block decisions can consult
     * the posterior. A no-op (other than the shared update) when wrapping an [ExpertPolicy], since that
     * policy's own [ExpertPolicy.observe] already updates the same shared memory — call exactly one of
     * the two per event to avoid double-counting; prefer the Expert's when present.
     */
    fun observe(
        event: GameEvent,
        turnNumber: Int,
    ) {
        // When wrapping a search tier (Expert/Grandmaster) we share ITS memory, and the ViewModel feeds
        // events straight into that policy's own observe(); updating here too would double-count. Only
        // self-feed when we own a private memory (non-search base tiers).
        val sharesSearchMemory = base is ExpertPolicy || base is GrandmasterPolicy
        if (!sharesSearchMemory) sharedMemory.observe(event, turnNumber)
    }

    // ── Policy.decide ─────────────────────────────────────────────────────────

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents supplied" }
        if (legal.size == 1) return legal.single()

        return when (view.phase) {
            is PhaseView.Turn -> decideTurn(view, legal)
            is PhaseView.Reactions -> decideReaction(view, legal)
            else -> base.decide(view, legal) // InfluenceLoss/Exchange — delegate fully
        }
    }

    // ── Turn ──────────────────────────────────────────────────────────────────

    private fun decideTurn(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val baseChoice = base.decide(view, legal)

        // 1. Maybe re-target if the base chose a targeted action.
        val retargeted = maybeRetarget(baseChoice, view, legal)

        // 2. Maybe suppress a bluff the persona wouldn't make, or push one it would.
        return maybeAdjustBluff(retargeted, view, legal)
    }

    /**
     * If the base chose a targeted action and targetingBias disagrees, pick a
     * better target from the legal intents of the same action type.
     */
    private fun maybeRetarget(
        chosen: Intent,
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val declared = chosen as? Intent.DeclareAction ?: return chosen
        val targetOf = Rules.targetOf(declared.action) ?: return chosen // untargeted action

        val aliveOpponents = view.players.filter { !it.eliminated && it.id != view.viewer }
        if (aliveOpponents.isEmpty()) return chosen

        val preferredTarget = pickTarget(aliveOpponents)
        if (preferredTarget == null || preferredTarget == targetOf) return chosen

        // Find a legal intent of the same action type targeting our preferred target.
        val sameTypeToPreferred =
            legal.filterIsInstance<Intent.DeclareAction>().firstOrNull { intent ->
                Rules.targetOf(intent.action) == preferredTarget &&
                    actionsSameType(intent.action, declared.action)
            }
        return sameTypeToPreferred ?: chosen
    }

    private fun pickTarget(opponents: List<OpponentView>): PlayerId? {
        if (opponents.isEmpty()) return null
        return when (p.targetingBias) {
            TargetingBias.LEADER -> opponents.maxByOrNull { it.standingScore() }?.id
            TargetingBias.WEAKEST -> opponents.minByOrNull { it.standingScore() }?.id
            TargetingBias.VINDICTIVE -> {
                val ids = opponents.map { it.id }
                grudge.topTarget(ids) ?: opponents.minByOrNull { it.faceDownCount }?.id
            }
            TargetingBias.RANDOM -> {
                val (i, r) = rng.nextInt(opponents.size)
                rng = r
                opponents[i].id
            }
        }
    }

    /**
     * Possibly suppress or promote a bluff action (Tax/Steal/Assassinate when not holding the role)
     * based on bluffRate + predictability jitter.
     *
     * - For LOW-bluff personas (bluffRate < 0.3): sometimes revert Tax/Steal to Income when
     *   remaining[role] is getting thin — they wouldn't risk the bluff.
     * - For HIGH-bluff personas (bluffRate > 0.6): sometimes push Tax/Steal even when the base
     *   skipped it — they love the bluff.
     * - predictability controls the jitter band around each gate (low = wider random spread).
     */
    private fun maybeAdjustBluff(
        chosen: Intent,
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val declared = chosen as? Intent.DeclareAction ?: return chosen
        val claimedRole = Rules.claimedRole(declared.action) ?: return chosen // not a role-claim
        val holdsRole = view.myInfluence.contains(claimedRole)

        // Jitter: [0, jitterRange) randomised addition/subtraction to thresholds
        val jitterRange = ((1f - p.predictability) * 25f).toInt().coerceAtLeast(1)
        val (j, r1) = rng.nextInt(jitterRange + 1)
        rng = r1
        val jitter = j - jitterRange / 2 // roughly ±jitterRange/2

        // For truthful claims, always keep (never suppress a truthful play).
        if (holdsRole) return chosen

        // Bluff case: gate based on bluffRate vs a random roll.
        val bluffGate = (p.bluffRate * 100f).toInt() + jitter
        val (roll, r2) = rng.nextInt(100)
        rng = r2
        if (roll < bluffGate) return chosen // persona would make this bluff → keep

        // Persona would NOT make this bluff — fall back to Income or FDI if available.
        val safeAction =
            legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }
                ?: legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.ForeignAid }
                ?: return chosen // no safe fallback — keep the bluff anyway
        return safeAction
    }

    // ── Reactions ─────────────────────────────────────────────────────────────

    private fun decideReaction(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val phase = view.phase as PhaseView.Reactions

        return when (phase.step) {
            ReactionStep.CHALLENGE_ACTION,
            ReactionStep.CHALLENGE_BLOCK,
            -> adjustChallenge(view, legal, phase)
            ReactionStep.BLOCK -> base.decide(view, legal)
        }
    }

    /**
     * Override the base's challenge/pass decision when our personal τ disagrees.
     *
     * τ = lerp(0.20, 0.50, challengeAggression)
     * We challenge when pSlot(claimedRole) < τ.
     */
    private fun adjustChallenge(
        view: PlayerView,
        legal: List<Intent>,
        phase: PhaseView.Reactions,
    ): Intent {
        val hasChallenge = legal.any { it is Intent.Challenge }
        val passIntent = legal.firstOrNull { it is Intent.Pass } ?: return base.decide(view, legal)
        val challengeIntent = legal.firstOrNull { it is Intent.Challenge }

        if (!hasChallenge || challengeIntent == null) return base.decide(view, legal)

        val claimedRole =
            if (phase.step == ReactionStep.CHALLENGE_BLOCK) {
                phase.blockRole
            } else {
                phase.claimedRole
            }
        if (claimedRole == null) return base.decide(view, legal)

        // Guaranteed bluff — always challenge regardless of persona.
        val remaining = remaining(view, claimedRole)
        if (remaining <= 0) return challengeIntent

        // The claimant whose hidden hand we're judging: the blocker on a block-challenge, else the actor.
        val claimant = if (phase.step == ReactionStep.CHALLENGE_BLOCK) phase.blocker else phase.actor

        // pHold = our best estimate that the claimant actually holds the claimed role.
        //
        // Baseline is the deck-frequency prior [pSlot] (per-slot unseen odds) — the exact quantity the
        // persona always used, so with NO observed history this path is behaviourally unchanged (and
        // strength stays persona-neutral). When we DO have evidence on this claimant — accumulated
        // role-evidence from claims/reveals (the deduction signal) — we blend in the belief POSTERIOR,
        // scaled by how much evidence we actually have. An opponent we've watched bluff this role, or
        // who revealed it face-up, then scores well below the raw odds and gets challenged more.
        val deckPrior = pSlot(view, claimedRole)
        val pHold =
            if (claimant != null) {
                val belief = sharedMemory.beliefFor(claimant)
                // Total absolute role-evidence we've gathered on this claimant. 0 ⇒ no deduction signal,
                // so we trust the prior entirely (neutrality-preserving when nobody fed observe()).
                val evidenceMass = belief.roleEvidence.values.sumOf { kotlin.math.abs(it) }
                if (evidenceMass <= 0.0) {
                    deckPrior
                } else {
                    // Per-slot posterior (probability a single claimed card is the role), comparable to the
                    // per-slot deckPrior. posterior() already returns the per-slot role probability folding
                    // in evidence; pHolds raises it to faceDownCount, which we don't want here.
                    val posterior = beliefModel.posterior(view, belief)[claimedRole] ?: deckPrior
                    // Posterior weight grows with evidence, saturating at 0.65 once we've seen a few signals.
                    val w = (evidenceMass / (evidenceMass + 4.0)).coerceIn(0.0, 0.65)
                    (1.0 - w) * deckPrior + w * posterior
                }
            } else {
                deckPrior
            }

        val tau = lerp(0.20f, 0.50f, p.challengeAggression)

        return if (pHold < tau) challengeIntent else passIntent
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun remaining(
        view: PlayerView,
        role: Role,
    ): Int {
        val gone = view.players.sumOf { opp -> opp.faceUpRoles.count { it == role } }
        return view.config.copiesPerRole - gone
    }

    private fun pSlot(
        view: PlayerView,
        role: Role,
    ): Double {
        val faceUpGone = view.players.sumOf { it.faceUpRoles.size }
        val unseenR = remaining(view, role) - view.myInfluence.count { it == role }
        if (unseenR <= 0) return 0.0
        val totalUnseen = view.config.deckSize - view.myInfluence.size - faceUpGone
        if (totalUnseen <= 0) return 0.0
        return unseenR.toDouble() / totalUnseen
    }

    private fun actionsSameType(
        a: Action,
        b: Action,
    ): Boolean =
        when {
            a is Action.Coup && b is Action.Coup -> true
            a is Action.Assassinate && b is Action.Assassinate -> true
            a is Action.Steal && b is Action.Steal -> true
            a is Action.Investigate && b is Action.Investigate -> true
            else -> false
        }

    private fun lerp(
        min: Float,
        max: Float,
        t: Float,
    ): Float = min + (max - min) * t.coerceIn(0f, 1f)
}

/**
 * How "ahead" a seat looks from across the table: a face-down influence card is worth ten coins,
 * because cards are what actually keep you in the game and coins only buy one Khela. LEADER and
 * WEAKEST targeting are the two ends of this same ordering, so it lives in one place.
 */
private const val InfluenceCoinEquivalent = 10

private fun OpponentView.standingScore(): Int = faceDownCount * InfluenceCoinEquivalent + coins
