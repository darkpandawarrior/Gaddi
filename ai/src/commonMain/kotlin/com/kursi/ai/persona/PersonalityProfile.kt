package com.kursi.ai.persona

import com.kursi.engine.PlayerId

/**
 * Below this a decayed grudge is dead. Dropping it keeps [GrudgeBook.topTarget] honest and stops
 * the score map growing forever, since [GrudgeBook.decay] only ever shrinks values asymptotically.
 */
private const val GrudgeEpsilon = 0.05

/**
 * Seven personality axes that drive how a bot plays.
 * These are *behavioral biases*, not strength levers — they reshape play-style
 * without changing the base policy's competence level.
 *
 * All values are in [0.0, 1.0].
 */
data class PersonalityProfile(
    /** P(claim a role not held) — scales Tax/Steal/block-bluff thresholds. */
    val bluffRate: Float,
    /** Eagerness to challenge claims: τ = lerp(0.20, 0.50, this). */
    val challengeAggression: Float,
    /** Preference for offense (Coup/Assassinate/Steal) vs accumulation (Income/Tax). */
    val economicAggression: Float,
    /** Who this bot prefers to target. */
    val targetingBias: TargetingBias,
    /** Tolerance for actions that can be challenged when remaining deck is thin. */
    val risk: Float,
    /** Memory weight on "who last hit me" — feeds VINDICTIVE targeting. */
    val vindictiveness: Float,
    /** Inverse of RNG noise: high = metronomic; low = unreadable. */
    val predictability: Float,
)

/** Targeting strategy for coup/assassinate/steal selection. */
enum class TargetingBias {
    /** Most influence, then most coins — punishes the table leader. */
    LEADER,

    /** Fewest influence — finishes off whoever is nearly dead. */
    WEAKEST,

    /** Whoever most recently challenged or hit this bot (grudge map). */
    VINDICTIVE,

    /** Chosen uniformly at random — impossible to read. */
    RANDOM,
}

/**
 * A grudge ledger: accumulated weight per opponent who has hit or challenged this bot.
 *
 * Scores are held in a [Double] so [decay] can shrink them geometrically each turn — a recent
 * attacker outweighs an old one, so VINDICTIVE retaliation tracks *who hurt me lately* rather than
 * *who hurt me most over the whole game*. [add] uses integer weights for caller convenience; the
 * accumulator is fractional purely so decay is smooth.
 */
class GrudgeMap {
    private val scores = HashMap<PlayerId, Double>()

    /** Record that [target] hit this bot, bumping their grudge score by [weight]. */
    fun add(
        target: PlayerId,
        weight: Int = 1,
    ) {
        scores[target] = (scores[target] ?: 0.0) + weight.toDouble()
    }

    /**
     * Geometrically shrink every grudge toward zero (call once per turn the bot lives through).
     * [factor] in (0,1): 0.85 halves a grudge in ~4 turns. Scores that fall below an epsilon are
     * dropped so a long-dead grudge stops influencing [topTarget].
     */
    fun decay(factor: Double = 0.85) {
        val it = scores.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val next = e.value * factor
            if (next < GrudgeEpsilon) it.remove() else e.setValue(next)
        }
    }

    /** Returns the candidate with the highest live grudge score, or null if none carries a grudge. */
    fun topTarget(candidates: List<PlayerId>): PlayerId? =
        candidates.maxByOrNull { scores[it] ?: 0.0 }?.takeIf { (scores[it] ?: 0.0) > 0.0 }

    /** Current grudge score for [pid] (0.0 if none). */
    fun scoreFor(pid: PlayerId): Double = scores[pid] ?: 0.0

    fun all(): Map<PlayerId, Double> = scores.toMap()
}
