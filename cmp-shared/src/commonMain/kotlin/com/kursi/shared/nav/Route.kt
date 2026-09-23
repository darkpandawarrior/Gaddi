package com.kursi.shared.nav

import kotlinx.serialization.Serializable

/**
 * All app destinations for Gaddi — type-safe sealed hierarchy.
 * All routes are @Serializable so navigation-compose can encode them in the back stack.
 *
 * §2 nav model from gaddi-plan/docs/17_app_plan.md
 */
sealed interface Route {
    /** S0 — Brass-door splash: reads hasSeenPrimer, routes to Primer or Home. */
    @Serializable
    data object Boot : Route

    /** S1 — Home / Daftar: the office waiting room (main hub). */
    @Serializable
    data object Home : Route

    /** S8 — First-run primer / onboarding (gate on AppPrefs.hasSeenPrimer). */
    @Serializable
    data object Primer : Route

    /**
     * PEHLI HAZRI — Player profile setup: display name, avatar emoji, seat color.
     * Shown after Primer on first run when no profile exists; also reachable from Settings.
     * [fromSettings] true = shows back button instead of skip link.
     */
    @Serializable
    data class ProfileSetup(
        val fromSettings: Boolean = false,
    ) : Route

    /** S7 — NIYAM GAZETTE: rules / who-beats-whom leaf overlay. */
    @Serializable
    data object Gazette : Route

    /** S6 — Settings / Daftari: DataStore-backed form. */
    @Serializable
    data object Settings : Route

    /** Career / Roznamcha register — persisted lifetime stats ledger (M3 §3). */
    @Serializable
    data object Career : Route

    /** PEHLI HAZRI — M5 interactive tutorial (scripted teaching round, guaranteed bluff-caught beat). */
    @Serializable
    data object Tutorial : Route

    /**
     * Nested graph wrapping the two-step new-game wizard.
     * Setup and Lobby share a SetupViewModel scoped to this graph.
     */
    @Serializable
    data object NewGameGraph : Route

    /** S2 — Setup / Requisition Form (inside NewGameGraph). */
    @Serializable
    data object Setup : Route

    /** S3 — Lobby / Hazri Register — persona preview (inside NewGameGraph). */
    @Serializable
    data class Lobby(
        val seed: Long,
        val players: Int,
        val difficulty: String,
        /** M5 pass-and-play: number of hot-seat humans (1 = vs-AI). */
        val humanCount: Int = 1,
        /** M6e TEAM KHEL: number of teams (last-team-standing). < 2 = classic free-for-all. */
        val teamCount: Int = 0,
        /** Narrative / Darbar mode — bots chat, conspire, and react to arcs. */
        val narrativeEnabled: Boolean = false,
        /** Anarchy (Andher Nagari) variant — Khela cost reduction. */
        val anarchy: Boolean = false,
        /** Deck-draft preset code (from DraftPresets); empty = standard deck. */
        val draftCode: String = "",
        // ── Vishesh (Special) variants — all default false; classic path unchanged ───────────────────
        val bailEnabled: Boolean = false,
        val sabotageEnabled: Boolean = false,
        val hawalaEnabled: Boolean = false,
        val emergencyEnabled: Boolean = false,
        val khazanaEnabled: Boolean = false,
        val khazanaTarget: Int = 25,
        val inflationEnabled: Boolean = false,
        val scarcityEnabled: Boolean = false,
    ) : Route

    /** M6e GAUNTLET — the escalating promotion ladder (Tarakki ki Seedhi) hub. */
    @Serializable
    data object Gauntlet : Route

    /**
     * S4 — In-game / Mez — the table.
     * Fully describes a match; same seed → same deal (deterministic engine).
     * Deep-link and process-death restoration come free.
     */
    @Serializable
    data class Game(
        val seed: Long,
        val players: Int,
        /** Difficulty.name — serializable as String. */
        val difficulty: String,
        /** M5 pass-and-play: number of hot-seat humans (1 = vs-AI). */
        val humanCount: Int = 1,
        /**
         * M6d — the calendar epoch-day when this match is the Daily Challenge (Aaj ki Chunauti);
         * -1 for an ordinary match. On game-over a daily match records the day's win/loss + streak.
         */
        val dailyDay: Long = -1L,
        /**
         * M6e GAUNTLET — the 0-based ladder rung this match is a gauntlet bout for; -1 for an ordinary
         * match. On game-over a gauntlet match records the rung's win/loss (promotion on a win).
         */
        val gauntletRung: Int = -1,
        /** M6e TEAM KHEL: number of teams (last-team-standing). < 2 = classic free-for-all. */
        val teamCount: Int = 0,
        /** M6e TAMASHA / DEMO: watch-only spectator match — the advisor auto-plays the human seat. */
        val spectator: Boolean = false,
        /** Narrative / Darbar mode — bots chat, conspire, and react to arcs. */
        val narrativeEnabled: Boolean = false,
        /** Lead arc for story mode — an ArcId.name or empty string for free Darbar. */
        val storyArc: String = "",
        /** Deck-draft preset code (from DraftPresets); empty = standard deck. */
        val draftCode: String = "",
        /** Anarchy (Andher Nagari) variant — Khela cost reduction. */
        val anarchy: Boolean = false,
        // ── Vishesh (Special) variants — all default false; classic path unchanged ───────────────────
        val bailEnabled: Boolean = false,
        val sabotageEnabled: Boolean = false,
        val hawalaEnabled: Boolean = false,
        val emergencyEnabled: Boolean = false,
        val khazanaEnabled: Boolean = false,
        val khazanaTarget: Int = 25,
        val inflationEnabled: Boolean = false,
        val scarcityEnabled: Boolean = false,
    ) : Route

    /** S5 — Results / Faisla: reads a serialized MatchSummary, not the live VM. */
    @Serializable
    data class Results(
        val matchId: String,
    ) : Route

    /**
     * M6c REVIEW — replay a recorded finished match on the real in-game table with a scrubber +
     * advisor annotations. [matchIndex] indexes into the persisted recent-matches store
     * (most-recent first; 0 = the latest finished game). The route carries only the index, so the
     * recorded match is resolved (and the deterministic replay reconstructed) inside the screen.
     */
    @Serializable
    data class Review(
        val matchIndex: Int = 0,
    ) : Route

    /**
     * M6d STANDINGS — local leaderboard: ranked ELO + rank tier + rating history spark-line, best
     * results, and the daily-challenge streak. Server-backed standings are deferred to the M7 online
     * tie-in; this is the local version behind a clean seam (reads only persisted AppPrefs).
     */
    @Serializable
    data object Leaderboard : Route

    /** Online stub — declared but not wired until M3. */
    @Serializable
    data object OnlineHub : Route

    /**
     * KISSA — Story / Narrative mode hub. Pick a lead arc (or free-form Darbar),
     * then route to a narrative-enabled Game.
     */
    @Serializable
    data object Story : Route
}
