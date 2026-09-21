package com.kursi.cli

import com.kursi.engine.GameConfig
import com.kursi.engine.PlayerId
import com.kursi.engine.RandomLegalPolicy
import com.kursi.engine.SimHarness

/**
 * Headless Kursi. Runs games entirely through the engine's public API, so a green run here is
 * evidence the engine carries the whole rule set without any UI, storage or platform help.
 *
 * Usage: kursi-cli [seats] [games] [seed]
 */
fun main(args: Array<String>) {
    val seats = args.getOrNull(0)?.toIntOrNull() ?: 4
    val games = args.getOrNull(1)?.toIntOrNull() ?: 1000
    val seed = args.getOrNull(2)?.toLongOrNull() ?: 1L

    require(seats in 2..10) { "seats must be between 2 and 10, got $seats" }
    require(games > 0) { "games must be positive, got $games" }

    // copiesPerRole scales with the table so the deck stays a uniform multiset over active roles.
    val config = GameConfig(seatCount = seats, copiesPerRole = if (seats <= 6) 3 else 4)

    println("Kursi engine, headless")
    println("  seats $seats, games $games, seed $seed")
    println()

    // A single narrated game first, so the output shows the engine actually resolving a match
    // rather than only reporting aggregates.
    val one =
        SimHarness.playOut(
            config = config,
            seed = seed,
            policies = (0 until seats).associate { PlayerId(it) to RandomLegalPolicy(seed + it) },
        )
    println("one game:  winner seat ${one.winner.raw}, ${one.turns} turns, ${one.steps} steps")
    println()

    val stats =
        SimHarness.playMany(
            config = config,
            seeds = seed until (seed + games),
            policyFactory = { player, s -> RandomLegalPolicy(s + player.raw) },
        )

    println("$games games:")
    println("  average turns  ${"%.1f".format(stats.avgTurns)}")
    println("  wins by seat")
    // Seat win-rate is the cheapest fairness signal the engine can produce: a uniform-ish spread
    // means no seat carries a structural advantage under identical random play.
    val total =
        stats.winsBySeat.values
            .sum()
            .coerceAtLeast(1)
    for (s in 0 until seats) {
        val w = stats.winsBySeat[s] ?: 0
        val pct = 100.0 * w / total
        println("    seat $s  ${w.toString().padStart(5)}  ${"%5.1f".format(pct)}%  ${"#".repeat((pct / 2).toInt())}")
    }
}
