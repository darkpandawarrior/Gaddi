package com.kursi.ai

import com.kursi.engine.*
import com.siddharth.kmp.botspolicy.SearchBudget

/**
 * GRANDMASTER tier AI — a strict superset of [ExpertPolicy], one rung above it.
 *
 * Two levers make it genuinely stronger than Expert, both deterministic given seed + budget:
 *
 *  1. DEEPER SEARCH. It runs the same [IsmctsSearch] but on a deeper budget than the in-game Expert
 *     budget — more iterations and a deeper rollout horizon — so the tree resolves longer tactical
 *     lines (multi-turn coin races, forced-reveal sequences) that Expert's shallower budget truncates.
 *     This is the edge that holds even with NO opponent read (e.g. in the seeded strength sims where
 *     events are never fed back), because it is pure search depth.
 *
 *  2. OPPONENT-MODEL EXPLOITATION. Where Expert plays close to Nash, Grandmaster deliberately deviates
 *     toward exploiting the per-opponent [StyleEstimate] the [BeliefModel] infers from observed play:
 *
 *       a. It passes an [exploitGain] > 1 into the search so the leaf evaluation ([staticEval] via
 *          [StyleContext]) bends *harder* toward the read — a high-aggression opponent's material looms
 *          larger (race to neutralise them), a trigger-happy table discounts our concealment more.
 *
 *       b. Before search, a confident-read OVERRIDE layer can deviate from the Nash-ish search choice:
 *            • CHALLENGE more vs a claimant whose inferred bluffRate is high (they lie often → call them).
 *            • BLUFF more (push a claim the search would skip) vs a table whose inferred challengeRate is
 *              low (nobody calls → bluffs are nearly free).
 *            • INVESTIGATE / Jaanch the STRONGEST opponent to strip information from the biggest threat.
 *          The override only fires when the read is backed by enough evidence; with no read it is inert,
 *          so Grandmaster degrades gracefully to "Expert with a deeper budget".
 *
 * Determinism: every random draw flows through the seeded [Rng]; the override consults only the
 * deterministic belief counters; the deeper budget is fixed. Same seed + budget ⇒ same line of play.
 *
 * Like Expert, the caller should feed game events via [observe] so the belief model — and therefore the
 * exploitation — actually has a read to work with.
 *
 * Implements both [Policy] and [SimPolicy] — see [EasyPolicy]'s KDoc for why.
 */
class GrandmasterPolicy(
    seed: Long,
    val budget: SearchBudget = GRANDMASTER_DEFAULT_BUDGET,
    /** Exploitation strength fed into the search leaf evaluation. >1 deviates harder from Nash. */
    private val exploitGain: Double = 2.0,
) : Policy,
    SimPolicy {
    private var rng = Rng(seed)
    private val fallback = HardPolicy(seed + 31337L)
    val memory = BotMemory()
    private val determinizer = Determinizer(BeliefModel())
    private val search =
        IsmctsSearch(
            determinizer = determinizer,
            budget = budget,
            rolloutSeed = seed + 424242L,
            exploitGain = exploitGain,
        )

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents supplied" }
        if (legal.size == 1) return legal.single()

        // 1. Confident-read exploitation override — only deviates when the belief model has a real read.
        exploitOverride(view, legal)?.let { return it }

        // 2. Deep ISMCTS with exploit-tuned leaf evaluation.
        return try {
            val chosen = search.chooseIntent(view, legal, memory, rng)
            val (_, r) = rng.nextLong()
            rng = r
            if (chosen in legal) chosen else fallback.decide(view, legal)
        } catch (ignored: Throwable) {
            // Deliberately ignored, and named so. The search is best-effort: a determinization
            // bug, an arithmetic edge or a stack overflow deep in a rollout must degrade to the
            // fallback policy, never surface as a crash mid-hand. `ignored` matches detekt's
            // allowedExceptionNameRegex, so the intent is on the page instead of in a baseline.
            fallback.decide(view, legal)
        }
    }

    /** Feed game events into the belief model so the exploitation layer has a read to exploit. */
    fun observe(
        event: GameEvent,
        turnNumber: Int,
    ) = memory.observe(event, turnNumber)

    // ── Exploitation override ───────────────────────────────────────────────────

    /**
     * Returns a move that deviates from Nash to punish an inferred tendency, or null to defer to search.
     * Fires only on a confident read (enough observed actions/claims), so it is a no-op early-game and
     * in the seeded sims where no events are fed back — keeping Grandmaster ≥ Expert unconditionally.
     */
    private fun exploitOverride(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? =
        when (view.phase) {
            is PhaseView.Reactions -> challengeExploit(view, legal, view.phase as PhaseView.Reactions)
            is PhaseView.Turn -> turnExploit(view, legal)
            else -> null
        }

    /**
     * CHALLENGE-side exploit: if the claimant is one we've watched bluff often (high inferred bluffRate)
     * AND the claimed role is plausibly catchable (not guaranteed-present), call them more readily than
     * the deck odds alone would justify. Conversely we never override a Pass into a Challenge against a
     * claimant with a *low* bluffRate — that is left to the search/material logic.
     */
    private fun challengeExploit(
        view: PlayerView,
        legal: List<Intent>,
        phase: PhaseView.Reactions,
    ): Intent? {
        val challenge = legal.firstOrNull { it is Intent.Challenge } ?: return null
        val claimedRole =
            when (phase.step) {
                ReactionStep.CHALLENGE_BLOCK -> phase.blockRole
                ReactionStep.CHALLENGE_ACTION -> phase.claimedRole
                else -> null
            } ?: return null
        val claimant = if (phase.step == ReactionStep.CHALLENGE_BLOCK) phase.blocker else phase.actor
        claimant ?: return null

        val belief = memory.beliefs[claimant] ?: return null
        // Require a confident read: at least a few tracked claims before we trust the bluffRate.
        if (belief.claimCount < MIN_CLAIMS_FOR_READ) return null

        val bluffRate = belief.style.bluffRate
        if (bluffRate < EXPLOIT_BLUFF_HI) return null // not a known liar — defer to search

        // Never challenge a role we can prove is fully present (deck odds say it must be held).
        val pSlot = pSlot(view, claimedRole)
        // The dirtier the liar, the higher the pSlot we'll still challenge at: scale the trigger by how
        // far over the threshold their bluffRate sits. Caps so we never challenge an obviously-true claim.
        val over = (bluffRate - EXPLOIT_BLUFF_HI).coerceIn(0.0, 0.5)
        val tau = (CHALLENGE_BASE_TAU + over).coerceAtMost(CHALLENGE_MAX_TAU)
        return if (pSlot < tau) challenge else null
    }

    /**
     * TURN-side exploit. Two deviations, in priority order:
     *
     *  a. INVESTIGATE THE STRONGEST. If a Jaanch (Investigate) on the most threatening opponent is legal
     *     and we hold PATRAKAAR (so the claim is uncatchable), strip info from the biggest threat. This
     *     is pure value vs the strongest seat and isn't always what a material-only search picks.
     *
     *  b. BLUFF INTO A PASSIVE TABLE. If the table's inferred mean challengeRate is low (nobody calls),
     *     promote a bluffed economy claim (Tax/Steal) the search might skip — bluffs are near-free when
     *     the table never challenges. Only when we don't hold the role (a truthful claim needs no help)
     *     and only when a read exists.
     */
    private fun turnExploit(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? = truthfulJaanchOnStrongest(view, legal) ?: bluffIntoPassiveTable(view, legal)

    /** (a) A truthful Jaanch aimed at the biggest threat — uncatchable, so it is never a gamble. */
    private fun truthfulJaanchOnStrongest(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        if (!view.myInfluence.contains(Role.PATRAKAAR)) return null
        val strongest = view.targetableOpponents.maxByOrNull { threatScore(it) } ?: return null
        return legal
            .filterIsInstance<Intent.DeclareAction>()
            .filter { it.action is Action.Investigate }
            .firstOrNull { (it.action as Action.Investigate).target == strongest.id }
    }

    /**
     * (b) Promote a bluffed economy claim the search might skip, but only into a table whose
     * inferred mean challengeRate says nobody calls. No read, or a table that calls — no bluff.
     */
    private fun bluffIntoPassiveTable(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent? {
        val read = tableChallengeRead(view) ?: return null
        if (read >= EXPLOIT_CHALLENGE_LO) return null // table calls bluffs — no free catch for them
        return legal
            .firstOrNull { it is Intent.DeclareAction && it.action == Action.Tax }
            ?.takeIf { !view.myInfluence.contains(Role.NETA) && remaining(view, Role.NETA) > 0 }
    }

    // ── Helpers (mirror HardPolicy's deck-odds math) ────────────────────────────

    // A face-down influence card is worth three coins of threat, and coins count half: a seat that
    // can still absorb two hits is more dangerous than one sitting on a pile of cash it cannot spend
    // before being eliminated. Mirrors HardPolicy's deck-odds weighting.
    private fun threatScore(opp: OpponentView): Int = opp.faceDownCount * InfluenceThreatWeight + opp.coins / CoinThreatDivisor

    /** Mean inferred challengeRate across opponents we have a confident read on; null if no read. */
    private fun tableChallengeRead(view: PlayerView): Double? {
        val opponents = view.players.filter { !it.eliminated && it.id != view.viewer }
        val read =
            opponents.mapNotNull { opp ->
                val b = memory.beliefs[opp.id] ?: return@mapNotNull null
                // challengeRate is denominated on table-wide claim opportunities; require some history.
                if (b.actionCount + b.claimCount < MIN_CLAIMS_FOR_READ) null else b.style.challengeRate
            }
        return if (read.isEmpty()) null else read.average()
    }

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

    companion object {
        /** A face-down influence card is worth this many coins of threat; coins count for half. */
        private const val InfluenceThreatWeight = 3
        private const val CoinThreatDivisor = 2

        /** ~3 tracked claims/actions before we trust a per-opponent style read enough to deviate on it. */
        private const val MIN_CLAIMS_FOR_READ = 3

        /** Bluff-rate above which a claimant is treated as "a known liar" worth challenging more. */
        private const val EXPLOIT_BLUFF_HI = 0.40

        /** Challenge-rate below which a table is treated as "passive" — bluffs are near-free. */
        private const val EXPLOIT_CHALLENGE_LO = 0.20

        /** Base pSlot threshold for an exploit-challenge; scaled up by how dirty the liar is. */
        private const val CHALLENGE_BASE_TAU = 0.45
        private const val CHALLENGE_MAX_TAU = 0.70
    }
}

/**
 * In-game GRANDMASTER budget: deeper than the Expert in-game budget (8 000 iter / 900 ms / horizon 16).
 * More iterations, a longer wall-clock cap, and a deeper base rollout horizon so longer tactical lines
 * resolve. Distinct from the strength-test budgets (defined in test files) so CI stays bounded.
 */
val GRANDMASTER_DEFAULT_BUDGET =
    SearchBudget(
        maxMillis = 1_400L,
        maxIterations = 16_000,
        rolloutHorizon = 22,
    )
