package com.kursi.engine

/**
 * Immutable, counter-based seeded PRNG (SplitMix64).
 *
 * The whole engine's determinism rests here: output is a pure function of (seed, step), so a state's
 * RNG can be replayed exactly. Every draw returns the value AND the advanced Rng — nothing mutates.
 * Integer-only math, identical on every Kotlin target (JVM today; wasmJs/native later).
 */
data class RngState(
    val seed: Long,
    val step: Long,
)

private const val GOLDEN: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

// SplitMix64's finalizer constants, verbatim from the reference implementation. They are not
// tunable: change one and the generator stops being SplitMix64, every recorded seed replays a
// different game, and the engine's determinism tests fail. Named so the numbers stop reading as
// arbitrary and start reading as a citation.
private const val MixMultiplier1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
private const val MixMultiplier2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB
private const val MixShift1 = 30
private const val MixShift2 = 27
private const val MixShift3 = 31

private fun mix(z0: Long): Long {
    var z = z0
    z = (z xor (z ushr MixShift1)) * MixMultiplier1
    z = (z xor (z ushr MixShift2)) * MixMultiplier2
    return z xor (z ushr MixShift3)
}

data class Rng(
    val state: RngState,
) {
    constructor(seed: Long) : this(RngState(seed, 0L))

    fun nextLong(): Pair<Long, Rng> {
        val nextStep = state.step + 1
        val v = mix(state.seed + nextStep * GOLDEN)
        return v to Rng(state.copy(step = nextStep))
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Pair<Int, Rng> {
        require(bound > 0) { "bound must be positive, was $bound" }
        val (v, r) = nextLong()
        val x = ((v ushr 1) % bound).toInt() // (v ushr 1) is non-negative; modulo is in range
        return x to r
    }

    /** Draws one element uniformly, returning (chosen, remaining, advancedRng). */
    fun <T> draw(from: List<T>): Triple<T, List<T>, Rng> {
        require(from.isNotEmpty()) { "cannot draw from an empty pool" }
        val (i, r) = nextInt(from.size)
        val chosen = from[i]
        val remaining = from.toMutableList().also { it.removeAt(i) }
        return Triple(chosen, remaining, r)
    }
}

fun rngFrom(state: RngState): Rng = Rng(state)
