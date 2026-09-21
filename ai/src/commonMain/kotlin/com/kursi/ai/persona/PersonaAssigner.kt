package com.kursi.ai.persona

import com.kursi.ai.EasyPolicy
import com.kursi.ai.ExpertPolicy
import com.kursi.ai.GRANDMASTER_DEFAULT_BUDGET
import com.kursi.ai.GrandmasterPolicy
import com.kursi.ai.HardPolicy
import com.kursi.ai.MediumPolicy
import com.kursi.engine.PlayerId
import com.siddharth.kmp.botspolicy.SearchBudget

/** Difficulty tiers available to the player. */
enum class BotDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    GRANDMASTER,
}

/**
 * Assigns distinct [BotPersona]s to bot seats and wraps each in a [PersonaPolicy]
 * with the appropriate tier base policy.
 *
 * @param seatCount  Number of bot seats (≤ 9; capped to roster size).
 * @param difficulty Base tier for ALL bots.
 * @param seed       Deterministic shuffle seed — same seed produces the same roster every game.
 * @return Map from [PlayerId] to its [PersonaPolicy] (seats 1..seatCount-1 when human is seat 0,
 *         but callers decide seat mapping; this returns a list in order 0..<seatCount).
 */
object PersonaAssigner {
    /**
     * Strong in-game Expert budget: 8 000 iterations / 900 ms / horizon 16.
     * Wall-clock cap keeps it safe on slower machines; extra iterations and
     * deeper rollout horizon give noticeably stronger play vs the old 4k/700ms.
     * Distinct from test budgets (defined locally in test files) so CI stays fast.
     */
    val EXPERT_GAME_BUDGET =
        SearchBudget(
            maxMillis = 900L,
            maxIterations = 8000,
            rolloutHorizon = 16,
        )

    fun assign(
        seatCount: Int,
        difficulty: BotDifficulty,
        seed: Long,
    ): List<Pair<BotPersona, PersonaPolicy>> {
        require(seatCount in 1..PersonaRoster.ALL.size) {
            "seatCount $seatCount out of range 1..${PersonaRoster.ALL.size}"
        }

        // Deterministic shuffle using a simple LCG on the seed.
        val shuffled = PersonaRoster.ALL.shuffledDeterministic(seed)
        val chosen = shuffled.take(seatCount)

        // Optional hue-lightness nudge: if two personas sharing a seat color land at the same
        // table, the second gets a nudged ARGB (+0x10 on the blue/green channel lightness bump).
        // This is purely cosmetic — the policy math uses the original persona object.
        val seenColors = mutableSetOf<Long>()
        val result = mutableListOf<Pair<BotPersona, PersonaPolicy>>()
        chosen.forEachIndexed { i, persona ->
            val finalPersona =
                if (persona.seatColorArgb in seenColors) {
                    // Nudge lightness by brightening the persona's color slightly for disambiguation.
                    persona.copy(seatColorArgb = persona.seatColorArgb.nudgeLightness())
                } else {
                    persona
                }
            seenColors.add(finalPersona.seatColorArgb)

            val botSeed = seed * 31L + i
            val base =
                when (difficulty) {
                    BotDifficulty.EASY -> EasyPolicy(botSeed)
                    BotDifficulty.MEDIUM -> MediumPolicy(botSeed)
                    BotDifficulty.HARD -> HardPolicy(botSeed)
                    BotDifficulty.EXPERT -> ExpertPolicy(seed = botSeed, budget = EXPERT_GAME_BUDGET)
                    BotDifficulty.GRANDMASTER ->
                        GrandmasterPolicy(seed = botSeed, budget = GRANDMASTER_DEFAULT_BUDGET)
                }
            result.add(finalPersona to PersonaPolicy(finalPersona, base, seed = botSeed + 99L))
        }
        return result
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Knuth's MMIX linear-congruential constants. Like SplitMix64's in :engine Rng.kt these are a
    // citation, not a tuning knob: change either and every seed deals a different persona order.
    private const val LcgMultiplier = 6364136223846793005L
    private const val LcgIncrement = 1442695040888963407L

    /** Take the high bits of the LCG state — the low bits of an LCG have short periods. */
    private const val LcgHighBitsShift = 33

    /** ARGB channel layout for [nudgeLightness]. */
    private const val AlphaShift = 24
    private const val RedShift = 16
    private const val GreenShift = 8
    private const val ChannelMask = 0xFFL

    /** How much to lift each channel when two personas collide on the same seat hue. */
    private const val LightnessNudge = 0x18L

    /** Deterministic Fisher-Yates shuffle using a LCG seeded from [seed]. */
    private fun <T> List<T>.shuffledDeterministic(seed: Long): List<T> {
        val list = toMutableList()
        var s = seed
        for (i in list.indices.reversed()) {
            s = s * LcgMultiplier + LcgIncrement
            val j = ((s ushr LcgHighBitsShift) % (i + 1)).toInt().and(Int.MAX_VALUE) % (i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }

    /**
     * Bump the lightness of an ARGB Long by adding a small amount to the brightest channel.
     * This is purely visual disambiguation when two personas share a seat hue.
     */
    private fun Long.nudgeLightness(): Long {
        val a = (this shr AlphaShift) and ChannelMask
        val r = ((this shr RedShift) and ChannelMask) + LightnessNudge
        val g = ((this shr GreenShift) and ChannelMask) + LightnessNudge
        val b = (this and ChannelMask) + LightnessNudge
        return (a shl AlphaShift) or
            (r.coerceAtMost(ChannelMask) shl RedShift) or
            (g.coerceAtMost(ChannelMask) shl GreenShift) or
            b.coerceAtMost(ChannelMask)
    }
}
