package com.kursi.feature.game

/**
 * Progressive-disclosure spine (spec §3). The same game revealed at three densities:
 *  - FOCUS   — whose turn · one plain-language line · your hand · your actions. Nothing else.
 *  - GUIDED  — FOCUS + one suggestion at a time.
 *  - ANALYST — the full instrument panel (today's screen). Default; preserves existing behavior.
 *
 * Persisted as a String in AppPrefs (core/prefs stays enum-free); the app layer maps String ↔ enum.
 */
enum class DensityLayer {
    FOCUS,
    GUIDED,
    ANALYST,
    ;

    companion object {
        /** Unknown / null → ANALYST (safe default = today's full behavior). */
        fun fromName(name: String?): DensityLayer = entries.firstOrNull { it.name == name } ?: ANALYST
    }
}

// ─────────────────────────── Graduation policy (spec §3, §6) ──────────────────────────

/** Games-played thresholds for the FOCUS → GUIDED → ANALYST climb (spec §3: "match count + DecisionQuality"). */
private const val FOCUS_TO_GUIDED_MATCHES = 3
private const val GUIDED_TO_ANALYST_MATCHES = 8

/** A demonstrated competence signal (enough sample, not RECKLESS) shortens the climb. */
private const val FOCUS_TO_GUIDED_MATCHES_COMPETENT = 2
private const val GUIDED_TO_ANALYST_MATCHES_COMPETENT = 5

/** Minimum decisions before the competence read is trusted at all (mirrors core/prefs' DecisionGrade.of). */
private const val MIN_DECISIONS_FOR_COMPETENCE_READ = 6

/** Below this best-move match rate (with enough sample), play reads as RECKLESS, not competent. */
private const val RECKLESS_ACCURACY_PCT = 45

/** At/above this average win-probability bled per decision, play reads as RECKLESS, not competent. */
private const val RECKLESS_EV_LOST_PCT = 12

/**
 * Pure graduation evaluator (spec §3, §6): advances [current] one rung at a time as the player racks
 * up completed matches ([gamesPlayed], from core/prefs' StatsLedger.games), climbing faster when their
 * lifetime decision-quality read shows real competence (a large-enough sample — [decisions] — that
 * doesn't read RECKLESS by [accuracyPct]/[avgEvLostPct], the same thresholds core/prefs' DecisionGrade
 * uses). Takes primitives rather than a core/prefs type so feature/game stays free of a core:prefs
 * dependency (the same seam the DensityLayer String↔enum mapping already uses — see GaddiApp.kt).
 * Never advances a player who has [manuallySet] their density layer themselves — a manual choice
 * (Settings, or any future in-game override) always wins; this function only ever proposes moving
 * FOCUS → GUIDED → ANALYST, never sideways or backward, and never past ANALYST. Callers persist the
 * result only when it differs from [current] (see [com.kursi.shared.GaddiApp]).
 */
fun evaluateDensityGraduation(
    current: DensityLayer,
    manuallySet: Boolean,
    gamesPlayed: Int,
    decisions: Int = 0,
    accuracyPct: Int = 0,
    avgEvLostPct: Int = 0,
): DensityLayer {
    if (manuallySet) return current
    val competent =
        decisions >= MIN_DECISIONS_FOR_COMPETENCE_READ &&
            accuracyPct >= RECKLESS_ACCURACY_PCT &&
            avgEvLostPct < RECKLESS_EV_LOST_PCT
    return when (current) {
        DensityLayer.FOCUS -> {
            val threshold = if (competent) FOCUS_TO_GUIDED_MATCHES_COMPETENT else FOCUS_TO_GUIDED_MATCHES
            if (gamesPlayed >= threshold) DensityLayer.GUIDED else DensityLayer.FOCUS
        }
        DensityLayer.GUIDED -> {
            val threshold = if (competent) GUIDED_TO_ANALYST_MATCHES_COMPETENT else GUIDED_TO_ANALYST_MATCHES
            if (gamesPlayed >= threshold) DensityLayer.ANALYST else DensityLayer.GUIDED
        }
        DensityLayer.ANALYST -> DensityLayer.ANALYST
    }
}
