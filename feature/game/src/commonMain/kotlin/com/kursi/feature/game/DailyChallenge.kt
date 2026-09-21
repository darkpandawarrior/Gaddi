package com.kursi.feature.game

/**
 * M6d — the deterministic Daily Challenge (Aaj ki Chunauti).
 *
 * A daily is a pure function of the calendar [epochDay] (days since 1970-01-01, supplied by the
 * UI/VM — never derived from a clock in pure code). The SAME date yields the SAME challenge for
 * everyone: a fixed seed, player count, and difficulty. The persona lineup follows for free, because
 * the in-game [com.kursi.ai.persona.PersonaAssigner] derives the roster deterministically from the
 * seed + difficulty — so by fixing the seed and difficulty, the daily fixes the whole table.
 *
 * Determinism note: derivation uses only integer arithmetic on [epochDay] (a SplitMix64-style hash),
 * so it is identical across platforms (JVM / iOS / wasm) and across runs.
 */
data class DailyChallenge(
    /** The calendar epoch-day this challenge is for. */
    val epochDay: Long,
    /** The deterministic match seed — drives the deal AND the persona lineup. */
    val seed: Long,
    /** Number of seats at the table (human seat 0 + bots). */
    val players: Int,
    /** Table difficulty for the day. */
    val difficulty: Difficulty,
) {
    companion object {
        /** Player-count band the daily rotates through (kept tight so a daily is always finishable). */
        private val PLAYER_CHOICES = intArrayOf(3, 4, 4, 5, 6)

        /** Difficulty band the daily rotates through (weighted toward the middle of the ladder). */
        private val DIFFICULTY_CHOICES =
            arrayOf(
                Difficulty.Easy,
                Difficulty.Medium,
                Difficulty.Medium,
                Difficulty.Hard,
                Difficulty.Hard,
                Difficulty.Expert,
            )

        /**
         * Derive the fixed daily challenge for [epochDay]. Pure + deterministic: same day → same
         * (seed, players, difficulty), hence the same persona lineup, on every device.
         */
        fun forDay(epochDay: Long): DailyChallenge {
            // SplitMix64 finalizer on the day index — a good integer hash with no float/clock use.
            val h = mix(epochDay)
            // Seed is a second, independent mix so the seed isn't trivially the day's hash.
            val seed = mix(h xor GOLDEN_GAMMA) and NON_NEGATIVE_MASK
            val players = PLAYER_CHOICES[((h ushr PLAYER_CHOICE_SHIFT) % PLAYER_CHOICES.size).toInt()]
            val difficulty = DIFFICULTY_CHOICES[((h ushr DIFFICULTY_CHOICE_SHIFT) % DIFFICULTY_CHOICES.size).toInt()]
            return DailyChallenge(epochDay = epochDay, seed = seed, players = players, difficulty = difficulty)
        }

        // SplitMix64's constants, same citation as :engine Rng.kt. Not tunable: they are what
        // makes this the published hash rather than an arbitrary one, and the daily challenge's
        // whole contract is that every device derives the same seed from the same day index.
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        private const val MIX_MULTIPLIER_1 = -0x40a7b892e31b1a47L
        private const val MIX_MULTIPLIER_2 = -0x6b2fb644ecceee15L
        private const val MIX_SHIFT_1 = 30
        private const val MIX_SHIFT_2 = 27
        private const val MIX_SHIFT_3 = 31

        /** Clears the sign bit so the seed is always non-negative. */
        private const val NON_NEGATIVE_MASK = 0x7fff_ffff_ffff_ffffL

        /** Bit offsets used to carve independent choices out of one hash. */
        private const val PLAYER_CHOICE_SHIFT = 8
        private const val DIFFICULTY_CHOICE_SHIFT = 21

        /** SplitMix64 finalizer — deterministic integer hash, identical on every Kotlin target. */
        private fun mix(x: Long): Long {
            var z = x + GOLDEN_GAMMA
            z = (z xor (z ushr MIX_SHIFT_1)) * MIX_MULTIPLIER_1
            z = (z xor (z ushr MIX_SHIFT_2)) * MIX_MULTIPLIER_2
            return (z xor (z ushr MIX_SHIFT_3)) and NON_NEGATIVE_MASK
        }
    }
}
