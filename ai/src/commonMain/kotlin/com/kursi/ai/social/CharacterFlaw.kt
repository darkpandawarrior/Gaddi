package com.kursi.ai.social

import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.persona.TargetingBias

/**
 * A persona's exploitable psychological FLAW — the lever a manipulator (the human player, in
 * narrative mode) can pull to make a bot act against its own interest.
 *
 * Flaws are derived *deterministically* from a bot's [PersonalityProfile] knobs, so a persona's
 * flaws are a stable function of who they already are — Bhai Teja (vindictive enforcer) is hot-headed
 * and vengeful; Netaji Vachan (high-bluff schemer) is an egomaniac; Babu Filewala (cautious banker)
 * is greedy-cautious. Nothing here touches the deterministic engine; flaws only ever bias a bot's
 * choice AMONG already-legal intents (see `SocialDirector.nudgeBotIntent`).
 */
enum class CharacterFlaw {
    /** Craves being seen as the strongest. Flattery → reckless overreach (attacks the leader, over-bluffs). */
    EGO,

    /** Sees threats everywhere. A planted rumour → wild challenges and pre-emptive strikes. */
    PARANOIA,

    /** Can't pass up coins or a kill. Baited into a doomed grab. */
    GREED,

    /** Remembers every slight. A grudge can be redirected onto a rival of the manipulator's choosing. */
    VENGEANCE,

    /** Self-righteous crusader. Provoked into "principled" but costly blunders. */
    ZEAL,

    /** Erratic, easily distracted. Nudged off the optimal line with little effort. */
    IMPULSE,
}

/**
 * Maps a [PersonalityProfile] to the flaws it exposes. Pure + deterministic — the same profile
 * always yields the same flaw set and susceptibility curve. Used by the narrative layer to decide
 * which manipulations (flattery / rumour / bait / grudge-redirect) actually land on a given bot.
 */
object FlawModel {
    /** A flaw counts as "present" once its susceptibility clears this bar. The dominant flaw is always present. */
    const val PRESENT_THRESHOLD = 0.50f

    /**
     * How exploitable [profile] is via [flaw], in `[0,1]`. Higher = the manipulation lands harder /
     * more reliably. Continuous so the director can scale the nudge probability by the read.
     */
    fun susceptibility(
        profile: PersonalityProfile,
        flaw: CharacterFlaw,
    ): Float =
        with(profile) {
            val leader = if (targetingBias == TargetingBias.LEADER) 1f else 0f
            val weakHunter = if (targetingBias == TargetingBias.WEAKEST) 1f else 0f
            val raw =
                when (flaw) {
                    CharacterFlaw.EGO -> 0.55f * bluffRate + 0.45f * (economicAggression * leader)
                    CharacterFlaw.PARANOIA -> 0.80f * challengeAggression + 0.20f * (1f - bluffRate)
                    CharacterFlaw.GREED -> 0.50f * economicAggression + 0.30f * risk + 0.20f * weakHunter
                    CharacterFlaw.VENGEANCE -> vindictiveness
                    CharacterFlaw.ZEAL -> 0.55f * (1f - bluffRate) + 0.45f * challengeAggression
                    CharacterFlaw.IMPULSE -> 1f - predictability
                }
            raw.coerceIn(0f, 1f)
        }

    /** Every flaw [profile] exposes (susceptibility ≥ [PRESENT_THRESHOLD]); always includes [dominantFlaw]. */
    fun flawsOf(profile: PersonalityProfile): Set<CharacterFlaw> {
        val present = CharacterFlaw.entries.filter { susceptibility(profile, it) >= PRESENT_THRESHOLD }.toMutableSet()
        present.add(dominantFlaw(profile))
        return present
    }

    /** The single most-exploitable flaw — the one a manipulator should aim for first. */
    fun dominantFlaw(profile: PersonalityProfile): CharacterFlaw =
        CharacterFlaw.entries.maxByOrNull { susceptibility(profile, it) } ?: CharacterFlaw.IMPULSE
}
