package com.kursi.ai

import com.kursi.engine.*

/**
 * Easy bot policy — legal-random with light guardrails so it never looks broken.
 *
 * Turn: prefers value moves (Income, Tax, ForeignAid, Steal, Assassinate when affordable, Coup when
 * available) over doing nothing. Picks target = lowest-influence opponent.
 * Reactions: challenges only when claim is literally impossible (remaining[R]==0); bluff-blocks ~15%
 * of the time; otherwise Passes.
 * InfluenceLoss: drops a card randomly (no preference).
 * Exchange: keeps first keepSize cards from the pool (stable, deterministic).
 *
 * Deterministic given seed; holds its own Rng thread.
 *
 * Implements both [Policy] (the generic ai-hosted decide-shape) and [SimPolicy] (`:engine`'s
 * self-play fuzzer contract) — this module's own strength/regression tests drive bot-vs-bot games
 * through [SimHarness] directly, so every tier needs to satisfy both shapes. Both interfaces declare
 * the identical `decide(view, legal): Intent` signature, so one override satisfies both.
 */
class EasyPolicy(
    seed: Long,
) : Policy,
    SimPolicy {
    private var rng = Rng(seed)

    private companion object {
        /** Percentage dice rolls (rng.nextInt(100)). Easy's whole "good but beatable" band. */
        const val TopTierPickPct = 60
        const val RandomChallengeNoisePct = 5
        const val BluffBlockPct = 15
        const val RandomExchangeKeepPct = 40
    }

    override fun decide(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        require(legal.isNotEmpty()) { "no legal intents supplied" }
        return when (view.phase) {
            is PhaseView.Turn -> decideTurn(view, legal)
            is PhaseView.Reactions -> decideReaction(view, legal)
            is PhaseView.InfluenceLoss -> legal.first() // drop any card — random order from engine is fine
            is PhaseView.Exchange -> decideExchange(view, legal)
            // Jaanch follow-up: Easy bot disrupts ~50% of the time (coin-flip), keeps otherwise.
            is PhaseView.InvestigatePeek -> decideInvestigatePeek(legal)
            is PhaseView.Over -> error("decide called after game over")
        }
    }

    // ── Turn ──────────────────────────────────────────────────────────────────

    private fun decideTurn(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        // If only Coup intents are legal (forced coup at >=10 coins), pick weakest target.
        val coupOnly = legal.all { it is Intent.DeclareAction && it.action is Action.Coup }
        if (coupOnly) {
            val target = weakestOpponent(view) ?: return randomFrom(legal)
            val found = legal.firstOrNull { it is Intent.DeclareAction && (it.action as Action.Coup).target == target }
            return found ?: randomFrom(legal)
        }

        // Build a weighted candidate list (weight = priority bucket, pick randomly within top bucket).
        // Priority: Coup > Assassinate > Steal > Tax > ForeignAid > Income > Exchange
        val coup = legal.filter { it is Intent.DeclareAction && it.action is Action.Coup }
        val assassinate = legal.filter { it is Intent.DeclareAction && it.action is Action.Assassinate }
        val steal = legal.filter { it is Intent.DeclareAction && it.action is Action.Steal }
        val tax = legal.filter { it is Intent.DeclareAction && it.action == Action.Tax }
        val foreignAid = legal.filter { it is Intent.DeclareAction && it.action == Action.ForeignAid }
        val investigate = legal.filter { it is Intent.DeclareAction && it.action is Action.Investigate }
        val income = legal.filter { it is Intent.DeclareAction && it.action == Action.Income }
        val exchange = legal.filter { it is Intent.DeclareAction && it.action == Action.Exchange }

        // Easy bot: 60% pick from top-priority tier, 40% pick randomly among all
        val (roll, r1) = rng.nextInt(100)
        rng = r1
        if (roll < TopTierPickPct) {
            for (tier in listOf(coup, assassinate, steal, tax, investigate, foreignAid, income)) {
                if (tier.isNotEmpty()) return preferWeakTarget(tier, view)
            }
        }

        // Fallback: random among all legal (excluding Exchange if other options exist)
        val nonExchange = legal.filter { !(it is Intent.DeclareAction && it.action == Action.Exchange) }
        return if (nonExchange.isNotEmpty()) randomFrom(nonExchange) else randomFrom(exchange.ifEmpty { legal })
    }

    // For a list of targeted intents, pick the one targeting the weakest opponent.
    private fun preferWeakTarget(
        intents: List<Intent>,
        view: PlayerView,
    ): Intent {
        val weakTarget = weakestOpponent(view)
        if (weakTarget != null) {
            val best =
                intents.firstOrNull { i ->
                    i is Intent.DeclareAction && Rules.targetOf(i.action) == weakTarget
                }
            if (best != null) return best
        }
        return randomFrom(intents)
    }

    /** Returns the alive (non-teammate) opponent with fewest face-down cards (lowest influence), or null. */
    private fun weakestOpponent(view: PlayerView): PlayerId? =
        view.targetableOpponents
            .minByOrNull { it.faceDownCount }
            ?.id

    // ── Reactions ─────────────────────────────────────────────────────────────

    private fun decideReaction(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        val phase = view.phase as PhaseView.Reactions
        val hasChallenge = legal.any { it is Intent.Challenge }
        val hasBlock = legal.any { it is Intent.Block }
        val passIntent = legal.first { it is Intent.Pass }

        // Challenge only if the claim is literally impossible (remaining[R]==0).
        val claimedRole = phase.claimedRole
        if (hasChallenge && claimedRole != null) {
            val remaining = remaining(view, claimedRole)
            if (remaining <= 0) return legal.first { it is Intent.Challenge }
        }
        // Also challenge block if block role is exhausted.
        val blockRole = phase.blockRole
        if (hasChallenge && blockRole != null) {
            val remaining = remaining(view, blockRole)
            if (remaining <= 0) return legal.first { it is Intent.Challenge }
        }
        // ~5% random challenge noise.
        val (roll1, r1) = rng.nextInt(100)
        rng = r1
        if (hasChallenge && roll1 < RandomChallengeNoisePct) return legal.first { it is Intent.Challenge }

        // ~15% bluff-block when we're the target and can block.
        if (hasBlock) {
            val (roll2, r2) = rng.nextInt(100)
            rng = r2
            if (roll2 < BluffBlockPct) {
                val block = legal.filterIsInstance<Intent.Block>()
                if (block.isNotEmpty()) return randomFrom(block)
            }
        }
        return passIntent
    }

    // ── Exchange ──────────────────────────────────────────────────────────────

    private fun decideExchange(
        view: PlayerView,
        legal: List<Intent>,
    ): Intent {
        if (legal.size == 1) return legal.first()
        // Easy is noisy: ~40% of the time it keeps a random legal set, otherwise it makes the
        // role-optimal keep (resolved via myCards + Exchange.drawn). Good-but-beatable.
        val (roll, r1) = rng.nextInt(100)
        rng = r1
        if (roll < RandomExchangeKeepPct) return randomFrom(legal)
        return CardChoice.bestExchange(view, legal) ?: legal.first()
    }

    // ── InvestigatePeek (Jaanch follow-up) ─────────────────────────────────────

    /** Easy bot: coin-flip on whether to force the target to redraw the peeked card. */
    private fun decideInvestigatePeek(legal: List<Intent>): Intent {
        val (roll, r1) = rng.nextInt(100)
        rng = r1
        val want = roll < 50
        return legal.firstOrNull { it is Intent.ResolveInvestigate && it.forceRedraw == want }
            ?: legal.first()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** remaining[R] = copies of role R not yet permanently face-up. */
    private fun remaining(
        view: PlayerView,
        role: Role,
    ): Int {
        val gone = view.players.sumOf { opp -> opp.faceUpRoles.count { it == role } }
        return view.config.copiesPerRole - gone
    }

    private fun randomFrom(list: List<Intent>): Intent {
        val (i, r) = rng.nextInt(list.size)
        rng = r
        return list[i]
    }
}
