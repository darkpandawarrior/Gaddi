package com.kursi.core.prefs

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppPrefs — multiplatform key-value preferences backed by multiplatform-settings.
 *
 * Persists:
 *  - hasSeenPrimer (Boolean) — first-run primer flag
 *  - soundEnabled (Boolean) — master sound toggle
 *  - reducedMotion (Boolean) — accessibility motion switch
 *  - defaultDifficulty (String) — "Easy" | "Medium" | "Hard" | "Expert"
 *  - defaultPlayerCount (Int) — 2..10
 *  - aiConsentGiven (Boolean) — Munshi narrator on-device/cloud model call permission
 *  - aiSelectedProviderName (String) — last-picked cloud provider (a ProviderId name)
 *
 * Usage:
 *   val prefs = AppPrefs()     // uses platform default Settings backend
 *   prefs.hasSeenPrimer        // read
 *   prefs.markPrimerSeen()     // write
 */
class AppPrefs(
    private val settings: Settings = Settings(),
) {
    companion object {
        private const val KEY_HAS_SEEN_PRIMER = "has_seen_primer"

        /** M5 ONBOARD — whether the post-primer "take the tutorial?" offer has been shown once. */
        private const val KEY_HAS_SEEN_TUTORIAL_OFFER = "has_seen_tutorial_offer"

        /** Guided-funnel (spec §6) — whether the first-run funnel (Boot/Primer → Tutorial → Home) has run. */
        private const val KEY_HAS_SEEN_FUNNEL = "has_seen_funnel"

        /** Graduation policy (spec §3) — true once the player has changed the density layer themselves;
         *  the auto-graduation evaluator never overrides a manual choice once this is set. */
        private const val KEY_DENSITY_LAYER_MANUAL = "density_layer_manual"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_DEFAULT_DIFFICULTY = "default_difficulty"
        private const val KEY_DEFAULT_PLAYERS = "default_player_count"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_COACH_ENABLED = "coach_enabled"
        private const val KEY_DENSITY_LAYER = "density_layer"

        // ── Player profile ──

        /** Display name shown in-game and in moments; default empty string → "Khiladi" fallback. */
        private const val KEY_PLAYER_NAME = "player_name"

        /** Index (0–11) into a fixed emoji avatar roster; -1 = no avatar set. */
        private const val KEY_PLAYER_AVATAR_IDX = "player_avatar_idx"

        /** Seat accent color as ARGB long (same encoding as OpponentPersona.seatColorArgb). */
        private const val KEY_PLAYER_COLOR_ARGB = "player_color_argb"

        // ── M5 auto-mode / assistant ──

        /** Turn pacing: "SLOW" | "NORMAL" | "FAST" — scales the bot-step delays. */
        private const val KEY_TURN_SPEED = "turn_speed"

        /** Auto-pass when the human's only meaningful legal reaction is Pass. */
        private const val KEY_AUTO_PASS = "auto_pass"

        /** Auto-play forced moves (e.g. the forced Coup at >= forcedCoupThreshold coins). */
        private const val KEY_AUTO_FORCED = "auto_forced"

        // ── Lifetime stats ledger (M3) ──
        private const val KEY_LEDGER_GAMES = "ledger_games"
        private const val KEY_LEDGER_WINS = "ledger_wins"
        private const val KEY_LEDGER_BLUFFS_HELD = "ledger_bluffs_held"
        private const val KEY_LEDGER_BLUFFS_CAUGHT = "ledger_bluffs_caught"

        /** Per-persona head-to-head, encoded as "personaId:played:wins;personaId:played:wins;…". */
        private const val KEY_LEDGER_H2H = "ledger_h2h"

        // ── In-progress match snapshot (M3 resume) ──

        /** Opaque serialized snapshot string written by the feature layer; null/blank = no game. */
        private const val KEY_MATCH_SNAPSHOT = "match_snapshot"

        // ── M6c completed-match replay store ──

        /**
         * Newline-separated list of opaque serialized COMPLETED-match records (most-recent FIRST),
         * each written by the feature layer. Capped to [MAX_RECENT_MATCHES]. Separate from the
         * in-progress [KEY_MATCH_SNAPSHOT]: M3 clears the in-progress snapshot on game-over, while
         * this register ADDS a finished-match record so the game can be reviewed/replayed afterward.
         * The record strings are base64-url-safe (no newlines), so a bare `\n` join is unambiguous.
         */
        private const val KEY_RECENT_MATCHES = "recent_matches"

        /** Cap on retained completed-match records — the most recent N. */
        const val MAX_RECENT_MATCHES = 10

        /** Record separator for the recent-matches list (records never contain a newline). */
        private const val RECENT_SEP = "\n"

        // ── M6b decision-quality ledger ──

        /** Lifetime count of HUMAN decisions the advisor read (coached or not). */
        private const val KEY_DQ_DECISIONS = "dq_decisions"

        /** How many of those matched the advisor's single best move. */
        private const val KEY_DQ_MATCHED = "dq_matched"

        /** Cumulative EV (win-probability) lost vs the best move, ×1000 to keep an integer store. */
        private const val KEY_DQ_EV_LOST_MILLI = "dq_ev_lost_milli"

        /** Challenges the human made (a Challenge intent submitted). */
        private const val KEY_DQ_CHALLENGES = "dq_challenges"

        /** Of those, how many were +EV per the advisor's odds (P(bluff) ≥ 0.5 at decision time). */
        private const val KEY_DQ_CHALLENGES_GOOD = "dq_challenges_good"

        /** Bluff actions/blocks the human attempted (claimed a role they did not hold). */
        private const val KEY_DQ_BLUFFS_TRIED = "dq_bluffs_tried"

        /** Of those attempts, how many survived to the table (were not caught) — folded at game end. */
        private const val KEY_DQ_BLUFFS_OK = "dq_bluffs_ok"

        // ── M6d ranked ELO ladder ────────────────────────────────────────────────

        /** The player's current ELO rating (Int). Seeds at [ELO_SEED] before any ranked game. */
        private const val KEY_ELO_RATING = "elo_rating"

        /** Peak ELO ever reached — surfaced on the leaderboard as a personal best. */
        private const val KEY_ELO_PEAK = "elo_peak"

        /** Count of ranked games folded into the rating (drives the leaderboard caption). */
        private const val KEY_ELO_GAMES = "elo_games"

        /**
         * Newline-separated rating-history points (OLDEST first), each "rating" as an int string,
         * capped to [MAX_RATING_HISTORY]. The first entry is always the seed so the spark-line has a
         * baseline. Drawn as the rating spark-line on the leaderboard.
         */
        private const val KEY_ELO_HISTORY = "elo_history"

        /** Starting ELO for a brand-new player — a mid-ladder "Clerk" rating. */
        const val ELO_SEED = 1000

        /** Cap on retained rating-history points (the spark-line window). */
        const val MAX_RATING_HISTORY = 40
        private const val ELO_HISTORY_SEP = "\n"

        // ── M6d daily challenge (Aaj ki Chunauti) ────────────────────────────────

        /** The calendar epoch-day (Long) of the LAST recorded daily-challenge result. -1 = none. */
        private const val KEY_DAILY_LAST_DAY = "daily_last_day"

        /** Whether the last recorded daily (on [KEY_DAILY_LAST_DAY]) was a win. */
        private const val KEY_DAILY_LAST_WON = "daily_last_won"

        /** Current consecutive-day win streak. Broken by a loss or a skipped day. */
        private const val KEY_DAILY_STREAK = "daily_streak"

        /** Best-ever daily win streak — surfaced on the leaderboard. */
        private const val KEY_DAILY_BEST_STREAK = "daily_best_streak"

        /** Lifetime count of daily challenges the player has completed (won or lost). */
        private const val KEY_DAILY_PLAYED = "daily_played"

        /** Lifetime count of daily challenges won. */
        private const val KEY_DAILY_WON = "daily_won"

        // ── In-App Review ─────────────────────────────────────────────────────────

        /** App version string for which the in-app review prompt has already been shown. */
        private const val KEY_REVIEW_SHOWN_VERSION = "review_shown_version"

        // ── M6e GAUNTLET ladder (Tarakki ki Seedhi) ──────────────────────────────

        /**
         * Highest gauntlet RUNG the player has CLEARED, 0-based into [GauntletLadder.RUNGS]. -1 = none
         * cleared yet (start at the bottom). A rung index of [GauntletLadder.RUNGS].lastIndex means the
         * whole ladder is conquered. The "current target" rung is always (cleared + 1), clamped.
         */
        private const val KEY_GAUNTLET_CLEARED = "gauntlet_cleared"

        /** Lifetime count of gauntlet bouts the player has WON (clears + re-wins on already-cleared rungs). */
        private const val KEY_GAUNTLET_WINS = "gauntlet_wins"

        // ── AI narration (Munshi BYOK) ────────────────────────────────────────────

        /** Whether the player has allowed the Munshi narrator to call an on-device or cloud model. */
        private const val KEY_AI_CONSENT = "ai_consent_given"

        /** Which cloud provider (a `ProviderId` name) the settings screen's picker last selected. */
        private const val KEY_AI_SELECTED_PROVIDER = "ai_selected_provider"
    }

    // ── hasSeenPrimer ─────────────────────────────────────────────────────────

    var hasSeenPrimer: Boolean
        get() = settings.getBoolean(KEY_HAS_SEEN_PRIMER, defaultValue = false)
        set(v) {
            settings.putBoolean(KEY_HAS_SEEN_PRIMER, v)
        }

    fun markPrimerSeen() {
        hasSeenPrimer = true
    }

    // ── Player profile ─────────────────────────────────────────────────────────

    /** The player's chosen display name. Empty string = not set (UI should show "Khiladi" as fallback). */
    var playerName: String
        get() = settings.getString(KEY_PLAYER_NAME, defaultValue = "")
        set(v) {
            settings.putString(KEY_PLAYER_NAME, v.trim())
        }

    /** True once the player has completed the profile setup screen at least once. */
    val hasPlayerProfile: Boolean get() = playerName.isNotBlank()

    /** 0-based index into the canonical avatar emoji roster; -1 = none chosen. */
    var playerAvatarIdx: Int
        get() = settings.getInt(KEY_PLAYER_AVATAR_IDX, defaultValue = -1)
        set(v) {
            settings.putInt(KEY_PLAYER_AVATAR_IDX, v)
        }

    /**
     * Seat accent color as packed ARGB Long (same as [com.kursi.feature.game.OpponentPersona.seatColorArgb]).
     * 0L = not set; UI falls back to a default warm red.
     */
    var playerColorArgb: Long
        get() = settings.getLong(KEY_PLAYER_COLOR_ARGB, defaultValue = 0L)
        set(v) {
            settings.putLong(KEY_PLAYER_COLOR_ARGB, v)
        }

    /** The display name to actually show (never empty — falls back to "Khiladi"). */
    val displayName: String get() = playerName.ifBlank { "Khiladi" }

    // ── hasSeenTutorialOffer (M5 ONBOARD) ──────────────────────────────────────

    /** True once the post-primer one-time "Pehli Hazri / take the tutorial?" offer has been shown. */
    var hasSeenTutorialOffer: Boolean
        get() = settings.getBoolean(KEY_HAS_SEEN_TUTORIAL_OFFER, defaultValue = false)
        set(v) {
            settings.putBoolean(KEY_HAS_SEEN_TUTORIAL_OFFER, v)
        }

    // ── hasSeenFunnel (guided funnel, spec §6) ─────────────────────────────────

    /**
     * Gates first-run routing into the interactive guided funnel (Boot/Primer/ProfileSetup → Tutorial
     * → Home). Defaults to [hasSeenTutorialOffer] when unset so a player upgrading from a build that
     * predates the funnel — and who therefore already reached Home at least once, resolving the old
     * offer dialog — is never routed into the funnel retroactively. Only a genuinely brand-new install
     * (both flags unset) sees it. The app layer sets this alongside [hasSeenTutorialOffer] when the
     * tutorial finishes (see KursiApp.kt), whether reached via the funnel or replayed later from Home.
     */
    var hasSeenFunnel: Boolean
        get() = settings.getBoolean(KEY_HAS_SEEN_FUNNEL, defaultValue = hasSeenTutorialOffer)
        set(v) {
            settings.putBoolean(KEY_HAS_SEEN_FUNNEL, v)
        }

    // ── Sound ─────────────────────────────────────────────────────────────────

    var soundEnabled: Boolean
        get() = settings.getBoolean(KEY_SOUND_ENABLED, defaultValue = true)
        set(v) {
            settings.putBoolean(KEY_SOUND_ENABLED, v)
            _soundFlow.value = v
        }

    // ── Reduced motion ────────────────────────────────────────────────────────

    var reducedMotion: Boolean
        get() = settings.getBoolean(KEY_REDUCED_MOTION, defaultValue = false)
        set(v) {
            settings.putBoolean(KEY_REDUCED_MOTION, v)
            _reducedMotionFlow.value = v
        }

    // ── Default difficulty ────────────────────────────────────────────────────

    var defaultDifficulty: String
        get() = settings.getString(KEY_DEFAULT_DIFFICULTY, defaultValue = "Medium")
        set(v) {
            settings.putString(KEY_DEFAULT_DIFFICULTY, v)
        }

    // ── Default player count ──────────────────────────────────────────────────

    var defaultPlayerCount: Int
        get() = settings.getInt(KEY_DEFAULT_PLAYERS, defaultValue = 4)
        set(v) {
            settings.putInt(KEY_DEFAULT_PLAYERS, v)
        }

    // ── Language ──────────────────────────────────────────────────────────────

    var language: String
        get() = settings.getString(KEY_LANGUAGE, defaultValue = "HINGLISH")
        set(v) {
            settings.putString(KEY_LANGUAGE, v)
            _languageFlow.value = v
        }

    // ── Decision Coach ────────────────────────────────────────────────────────

    var coachEnabled: Boolean
        get() = settings.getBoolean(KEY_COACH_ENABLED, defaultValue = true)
        set(v) {
            settings.putBoolean(KEY_COACH_ENABLED, v)
            _coachEnabledFlow.value = v
        }

    // ── Density layer (progressive disclosure, spec §3) ──────────────────────

    /** Density layer name ("FOCUS"|"GUIDED"|"ANALYST"); default ANALYST for existing installs.
     *  Stored as a plain String — this module stays enum-free; the app layer maps it. */
    var densityLayerName: String
        get() = settings.getString(KEY_DENSITY_LAYER, defaultValue = "ANALYST")
        set(v) {
            settings.putString(KEY_DENSITY_LAYER, v)
            _densityLayerFlow.value = v
        }

    /** True once the player has changed the density layer themselves (Settings / an in-game override).
     *  [com.kursi.feature.game.evaluateDensityGraduation] reads this and never auto-advances past it. */
    var densityLayerManuallySet: Boolean
        get() = settings.getBoolean(KEY_DENSITY_LAYER_MANUAL, defaultValue = false)
        set(v) {
            settings.putBoolean(KEY_DENSITY_LAYER_MANUAL, v)
        }

    // ── M5 Turn speed / auto-mode ─────────────────────────────────────────────

    /** Turn pacing tier — scales the bot-step delays. Default NORMAL. */
    var turnSpeed: TurnSpeed
        get() = TurnSpeed.fromName(settings.getString(KEY_TURN_SPEED, defaultValue = TurnSpeed.NORMAL.name))
        set(v) {
            settings.putString(KEY_TURN_SPEED, v.name)
            _turnSpeedFlow.value = v
            _turnSpeedMultiplierFlow.value = v.multiplier
        }

    /** AUTO-PASS: when the human's only meaningful legal reaction is Pass, pass for them. Default true. */
    var autoPass: Boolean
        get() = settings.getBoolean(KEY_AUTO_PASS, defaultValue = true)
        set(v) {
            settings.putBoolean(KEY_AUTO_PASS, v)
            _autoPassFlow.value = v
        }

    /** AUTO-PLAY FORCED MOVES: when the human's only legal move is forced (e.g. the >=10-coin Coup),
     *  play the best one automatically. Default false (let the player at least pick the target). */
    var autoPlayForced: Boolean
        get() = settings.getBoolean(KEY_AUTO_FORCED, defaultValue = false)
        set(v) {
            settings.putBoolean(KEY_AUTO_FORCED, v)
            _autoPlayForcedFlow.value = v
        }

    // ── AI narration (Munshi BYOK) ────────────────────────────────────────────

    /**
     * Whether the player has allowed the Munshi narrator to call an on-device or cloud model at
     * all — the settings screen's toolkit `AiSettingsSection` "Enable AI features" switch. Default
     * true preserves the pre-existing zero-setup on-device narration for a player who never opens
     * Settings; the switch only lets someone turn it OFF. False forces
     * [com.kursi.ai.createMunshiNarrator] down to the templated floor (no on-device or cloud call).
     */
    var aiConsentGiven: Boolean
        get() = settings.getBoolean(KEY_AI_CONSENT, defaultValue = true)
        set(v) {
            settings.putBoolean(KEY_AI_CONSENT, v)
        }

    /**
     * The cloud provider (by `com.siddharth.kmp.llmchat.ProviderId` name) the settings screen's
     * picker last selected — this module stays enum/toolkit-type-free, same as [defaultDifficulty].
     * Only reorders the provider chain's priority; any saved key is still tried regardless. Default
     * "OFFLINE_FALLBACK" (no reorder).
     */
    var aiSelectedProviderName: String
        get() = settings.getString(KEY_AI_SELECTED_PROVIDER, defaultValue = "OFFLINE_FALLBACK")
        set(v) {
            settings.putString(KEY_AI_SELECTED_PROVIDER, v)
        }

    // ── Observable wrappers (optional, for Settings with callbacks) ───────────

    private val _soundFlow = MutableStateFlow(soundEnabled)
    val soundFlow: StateFlow<Boolean> = _soundFlow.asStateFlow()

    private val _reducedMotionFlow = MutableStateFlow(reducedMotion)
    val reducedMotionFlow: StateFlow<Boolean> = _reducedMotionFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(language)
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _coachEnabledFlow = MutableStateFlow(coachEnabled)
    val coachEnabledFlow: StateFlow<Boolean> = _coachEnabledFlow.asStateFlow()

    private val _densityLayerFlow = MutableStateFlow(densityLayerName)
    val densityLayerFlow: StateFlow<String> = _densityLayerFlow.asStateFlow()

    private val _turnSpeedFlow = MutableStateFlow(turnSpeed)
    val turnSpeedFlow: StateFlow<TurnSpeed> = _turnSpeedFlow.asStateFlow()

    private val _turnSpeedMultiplierFlow = MutableStateFlow(turnSpeed.multiplier)

    /** The turn-speed multiplier as a plain Float flow, for layers that don't depend on [TurnSpeed]. */
    val turnSpeedMultiplierFlow: StateFlow<Float> = _turnSpeedMultiplierFlow.asStateFlow()

    private val _autoPassFlow = MutableStateFlow(autoPass)
    val autoPassFlow: StateFlow<Boolean> = _autoPassFlow.asStateFlow()

    private val _autoPlayForcedFlow = MutableStateFlow(autoPlayForced)
    val autoPlayForcedFlow: StateFlow<Boolean> = _autoPlayForcedFlow.asStateFlow()

    // ── In-progress match snapshot (M3 resume) ──────────────────────────────────

    /**
     * Opaque serialized snapshot of an in-progress match (seed + human action log). The feature
     * layer encodes/decodes the actual structure; AppPrefs only stores the string. Null when no
     * game is in progress (cleared on game end or when resumed-and-finished).
     */
    var matchSnapshot: String?
        get() = settings.getString(KEY_MATCH_SNAPSHOT, defaultValue = "").ifBlank { null }
        set(v) {
            if (v.isNullOrBlank()) {
                settings.remove(KEY_MATCH_SNAPSHOT)
            } else {
                settings.putString(KEY_MATCH_SNAPSHOT, v)
            }
            _matchSnapshotFlow.value = matchSnapshot
        }

    fun clearMatchSnapshot() {
        matchSnapshot = null
    }

    private val _matchSnapshotFlow = MutableStateFlow(matchSnapshot)
    val matchSnapshotFlow: StateFlow<String?> = _matchSnapshotFlow.asStateFlow()

    // ── M6c completed-match replay store ─────────────────────────────────────────

    /**
     * The retained completed-match records (most-recent FIRST), as opaque serialized strings. The
     * feature layer decodes each into a `CompletedMatch`; AppPrefs only stores the strings and caps
     * the list. Empty when no game has finished yet.
     */
    fun recentMatches(): List<String> {
        val raw = settings.getString(KEY_RECENT_MATCHES, defaultValue = "")
        if (raw.isBlank()) return emptyList()
        return raw.split(RECENT_SEP).filter { it.isNotBlank() }
    }

    /**
     * Prepend one finished-match [record] (an opaque serialized string) to the recent-matches list,
     * trim to [MAX_RECENT_MATCHES], and persist. No-op on a blank record. Returns the new list
     * (most-recent first). The record string MUST NOT contain a newline (JSON encodes them as `\n`
     * escapes, so this holds for any `CompletedMatch.encode()` output).
     */
    fun addRecentMatch(record: String): List<String> {
        if (record.isBlank()) return recentMatches()
        val next = (listOf(record) + recentMatches()).take(MAX_RECENT_MATCHES)
        settings.putString(KEY_RECENT_MATCHES, next.joinToString(RECENT_SEP))
        _recentMatchesFlow.value = next
        return next
    }

    /** Wipe every retained completed-match record (e.g. a "clear history" action / career reset). */
    fun clearRecentMatches() {
        settings.remove(KEY_RECENT_MATCHES)
        _recentMatchesFlow.value = emptyList()
    }

    private val _recentMatchesFlow = MutableStateFlow(recentMatches())

    /** Reactive recent-matches list (most-recent first) for the Review entry point. */
    val recentMatchesFlow: StateFlow<List<String>> = _recentMatchesFlow.asStateFlow()

    // ── Lifetime stats ledger (M3) ──────────────────────────────────────────────

    /** Read the full lifetime ledger as an immutable snapshot. */
    fun readLedger(): StatsLedger =
        StatsLedger(
            games = settings.getInt(KEY_LEDGER_GAMES, 0),
            wins = settings.getInt(KEY_LEDGER_WINS, 0),
            bluffsHeld = settings.getInt(KEY_LEDGER_BLUFFS_HELD, 0),
            bluffsCaught = settings.getInt(KEY_LEDGER_BLUFFS_CAUGHT, 0),
            headToHead = decodeH2H(settings.getString(KEY_LEDGER_H2H, "")),
        )

    /**
     * Fold one completed game into the lifetime ledger and persist the new totals. [opponentIds]
     * are the persona ids the human faced this game; [humanWon] marks a victory for every per-persona
     * head-to-head entry. Returns the updated ledger.
     */
    fun recordGame(
        humanWon: Boolean,
        bluffsHeld: Int,
        bluffsCaught: Int,
        opponentIds: List<String>,
    ): StatsLedger {
        val cur = readLedger()
        val h2h = cur.headToHead.toMutableMap()
        for (id in opponentIds.filter { it.isNotBlank() }) {
            val prev = h2h[id] ?: PersonaRecord(0, 0)
            h2h[id] =
                PersonaRecord(
                    played = prev.played + 1,
                    wins = prev.wins + if (humanWon) 1 else 0,
                )
        }
        val next =
            StatsLedger(
                games = cur.games + 1,
                wins = cur.wins + if (humanWon) 1 else 0,
                bluffsHeld = cur.bluffsHeld + bluffsHeld,
                bluffsCaught = cur.bluffsCaught + bluffsCaught,
                headToHead = h2h,
            )
        settings.putInt(KEY_LEDGER_GAMES, next.games)
        settings.putInt(KEY_LEDGER_WINS, next.wins)
        settings.putInt(KEY_LEDGER_BLUFFS_HELD, next.bluffsHeld)
        settings.putInt(KEY_LEDGER_BLUFFS_CAUGHT, next.bluffsCaught)
        settings.putString(KEY_LEDGER_H2H, encodeH2H(next.headToHead))
        _ledgerFlow.value = next
        return next
    }

    /** Wipe the lifetime ledger (e.g. a "reset career" action). */
    fun resetLedger() {
        settings.remove(KEY_LEDGER_GAMES)
        settings.remove(KEY_LEDGER_WINS)
        settings.remove(KEY_LEDGER_BLUFFS_HELD)
        settings.remove(KEY_LEDGER_BLUFFS_CAUGHT)
        settings.remove(KEY_LEDGER_H2H)
        _ledgerFlow.value = readLedger()
        // A career reset clears the decision-quality dossier too — they are one register.
        resetDecisionLedger()
        // M6c: and the completed-match replay history — a wiped career has no past games to review.
        clearRecentMatches()
        // M6d: and the ranked ELO ladder + daily-challenge standings — one career, one file.
        resetRanked()
        resetDaily()
        // M6e: and the gauntlet ladder progress — a fresh career starts at the bottom rung.
        resetGauntlet()
    }

    private val _ledgerFlow = MutableStateFlow(readLedger())
    val ledgerFlow: StateFlow<StatsLedger> = _ledgerFlow.asStateFlow()

    // ── M6b decision-quality ledger ─────────────────────────────────────────────

    /** Read the full lifetime decision-quality ledger as an immutable snapshot. */
    fun readDecisionLedger(): DecisionLedger =
        DecisionLedger(
            decisions = settings.getInt(KEY_DQ_DECISIONS, 0),
            matchedBest = settings.getInt(KEY_DQ_MATCHED, 0),
            evLostMilli = settings.getLong(KEY_DQ_EV_LOST_MILLI, 0L),
            challenges = settings.getInt(KEY_DQ_CHALLENGES, 0),
            challengesGood = settings.getInt(KEY_DQ_CHALLENGES_GOOD, 0),
            bluffsTried = settings.getInt(KEY_DQ_BLUFFS_TRIED, 0),
            bluffsOk = settings.getInt(KEY_DQ_BLUFFS_OK, 0),
        )

    /**
     * Fold one completed game's worth of decision-quality counters into the lifetime ledger and
     * persist. [tally] is accumulated OFF the UI thread by the feature layer (one entry per human
     * decision, using the advisor read for that decision). Returns the updated ledger.
     */
    fun recordDecisionTally(tally: DecisionTally): DecisionLedger {
        val cur = readDecisionLedger()
        val next =
            DecisionLedger(
                decisions = cur.decisions + tally.decisions,
                matchedBest = cur.matchedBest + tally.matchedBest,
                evLostMilli = cur.evLostMilli + tally.evLostMilli,
                challenges = cur.challenges + tally.challenges,
                challengesGood = cur.challengesGood + tally.challengesGood,
                bluffsTried = cur.bluffsTried + tally.bluffsTried,
                bluffsOk = cur.bluffsOk + tally.bluffsOk,
            )
        settings.putInt(KEY_DQ_DECISIONS, next.decisions)
        settings.putInt(KEY_DQ_MATCHED, next.matchedBest)
        settings.putLong(KEY_DQ_EV_LOST_MILLI, next.evLostMilli)
        settings.putInt(KEY_DQ_CHALLENGES, next.challenges)
        settings.putInt(KEY_DQ_CHALLENGES_GOOD, next.challengesGood)
        settings.putInt(KEY_DQ_BLUFFS_TRIED, next.bluffsTried)
        settings.putInt(KEY_DQ_BLUFFS_OK, next.bluffsOk)
        _decisionLedgerFlow.value = next
        return next
    }

    /** Wipe the lifetime decision-quality ledger (folded into [resetLedger]). */
    fun resetDecisionLedger() {
        settings.remove(KEY_DQ_DECISIONS)
        settings.remove(KEY_DQ_MATCHED)
        settings.remove(KEY_DQ_EV_LOST_MILLI)
        settings.remove(KEY_DQ_CHALLENGES)
        settings.remove(KEY_DQ_CHALLENGES_GOOD)
        settings.remove(KEY_DQ_BLUFFS_TRIED)
        settings.remove(KEY_DQ_BLUFFS_OK)
        _decisionLedgerFlow.value = readDecisionLedger()
    }

    private val _decisionLedgerFlow = MutableStateFlow(readDecisionLedger())
    val decisionLedgerFlow: StateFlow<DecisionLedger> = _decisionLedgerFlow.asStateFlow()

    // ── M6d ranked ELO ladder ───────────────────────────────────────────────────

    /** Read the current ranked standing as an immutable snapshot. */
    fun readRankedStanding(): RankedStanding =
        RankedStanding(
            rating = settings.getInt(KEY_ELO_RATING, ELO_SEED),
            peak = settings.getInt(KEY_ELO_PEAK, ELO_SEED),
            games = settings.getInt(KEY_ELO_GAMES, 0),
            history = readRatingHistory(),
        )

    private fun readRatingHistory(): List<Int> {
        val raw = settings.getString(KEY_ELO_HISTORY, "")
        if (raw.isBlank()) return listOf(ELO_SEED)
        val pts = raw.split(ELO_HISTORY_SEP).mapNotNull { it.toIntOrNull() }
        return pts.ifEmpty { listOf(ELO_SEED) }
    }

    /**
     * Fold one finished ranked game into the ELO rating and persist the new standing. [newRating] is
     * computed by the feature layer's pure ELO step ([Elo.step]) from the current [readRatingStanding]
     * rating and the table's implied opponent rating. This method only stores the result, tracks the
     * peak, bumps the game count, and appends a history point. Returns the updated standing.
     */
    fun recordRankedResult(newRating: Int): RankedStanding {
        val cur = readRankedStanding()
        val clamped = newRating.coerceIn(0, 4000)
        val nextHistory = (cur.history + clamped).takeLast(MAX_RATING_HISTORY)
        val next =
            RankedStanding(
                rating = clamped,
                peak = maxOf(cur.peak, clamped),
                games = cur.games + 1,
                history = nextHistory,
            )
        settings.putInt(KEY_ELO_RATING, next.rating)
        settings.putInt(KEY_ELO_PEAK, next.peak)
        settings.putInt(KEY_ELO_GAMES, next.games)
        settings.putString(KEY_ELO_HISTORY, nextHistory.joinToString(ELO_HISTORY_SEP))
        _rankedFlow.value = next
        return next
    }

    /** Wipe the ranked ELO ladder back to the seed (folded into [resetLedger]). */
    fun resetRanked() {
        settings.remove(KEY_ELO_RATING)
        settings.remove(KEY_ELO_PEAK)
        settings.remove(KEY_ELO_GAMES)
        settings.remove(KEY_ELO_HISTORY)
        _rankedFlow.value = readRankedStanding()
    }

    private val _rankedFlow = MutableStateFlow(readRankedStanding())

    /** Reactive ranked standing for the Home strip + Career + Leaderboard. */
    val rankedFlow: StateFlow<RankedStanding> = _rankedFlow.asStateFlow()

    // ── M6d daily challenge (Aaj ki Chunauti) ─────────────────────────────────────

    /** Read the daily-challenge standing as an immutable snapshot. */
    fun readDailyStanding(): DailyStanding =
        DailyStanding(
            lastDay = settings.getLong(KEY_DAILY_LAST_DAY, -1L),
            lastWon = settings.getBoolean(KEY_DAILY_LAST_WON, false),
            streak = settings.getInt(KEY_DAILY_STREAK, 0),
            bestStreak = settings.getInt(KEY_DAILY_BEST_STREAK, 0),
            played = settings.getInt(KEY_DAILY_PLAYED, 0),
            won = settings.getInt(KEY_DAILY_WON, 0),
        )

    /** True when the daily challenge for [epochDay] has already been recorded (one attempt/day). */
    fun isDailyDone(epochDay: Long): Boolean = settings.getLong(KEY_DAILY_LAST_DAY, -1L) == epochDay

    /**
     * Record today's daily-challenge result and persist the new standing. [epochDay] is supplied by
     * the UI/VM (never derived from a clock here). The streak increments only on a win whose [epochDay]
     * is exactly one day after the previous recorded day (or the first daily ever); a win on a later
     * day restarts the streak at 1; any loss resets it to 0. A repeat call for an already-recorded day
     * is a no-op (one attempt counts per calendar day). Returns the updated standing.
     */
    fun recordDailyResult(
        epochDay: Long,
        won: Boolean,
    ): DailyStanding {
        val cur = readDailyStanding()
        if (cur.lastDay == epochDay) return cur // already recorded today — ignore.
        val continued = won && cur.lastDay >= 0 && epochDay == cur.lastDay + 1 && cur.lastWon
        val nextStreak =
            when {
                !won -> 0
                continued -> cur.streak + 1
                else -> 1 // first daily, or a win after a gap — streak restarts at 1.
            }
        val next =
            DailyStanding(
                lastDay = epochDay,
                lastWon = won,
                streak = nextStreak,
                bestStreak = maxOf(cur.bestStreak, nextStreak),
                played = cur.played + 1,
                won = cur.won + if (won) 1 else 0,
            )
        settings.putLong(KEY_DAILY_LAST_DAY, next.lastDay)
        settings.putBoolean(KEY_DAILY_LAST_WON, next.lastWon)
        settings.putInt(KEY_DAILY_STREAK, next.streak)
        settings.putInt(KEY_DAILY_BEST_STREAK, next.bestStreak)
        settings.putInt(KEY_DAILY_PLAYED, next.played)
        settings.putInt(KEY_DAILY_WON, next.won)
        _dailyFlow.value = next
        return next
    }

    /** Wipe the daily-challenge standing (folded into [resetLedger]). */
    fun resetDaily() {
        settings.remove(KEY_DAILY_LAST_DAY)
        settings.remove(KEY_DAILY_LAST_WON)
        settings.remove(KEY_DAILY_STREAK)
        settings.remove(KEY_DAILY_BEST_STREAK)
        settings.remove(KEY_DAILY_PLAYED)
        settings.remove(KEY_DAILY_WON)
        _dailyFlow.value = readDailyStanding()
    }

    private val _dailyFlow = MutableStateFlow(readDailyStanding())

    /** Reactive daily-challenge standing for the Home daily entry + Leaderboard. */
    val dailyFlow: StateFlow<DailyStanding> = _dailyFlow.asStateFlow()

    // ── M6e GAUNTLET ladder (Tarakki ki Seedhi) ───────────────────────────────────

    /** Read the gauntlet ladder progress as an immutable snapshot. */
    fun readGauntlet(): GauntletProgress =
        GauntletProgress(
            clearedRung = settings.getInt(KEY_GAUNTLET_CLEARED, -1),
            wins = settings.getInt(KEY_GAUNTLET_WINS, 0),
        )

    /**
     * Record a gauntlet bout for [rung] (0-based into the ladder). A WIN on the player's current target
     * rung advances [GauntletProgress.clearedRung] by one (promotion). A win on an already-cleared rung
     * (a re-run) still bumps the lifetime win count but never regresses progress. A loss never regresses
     * progress — the ladder is forgiving (you simply re-attempt the rung). Returns the updated progress.
     */
    fun recordGauntletResult(
        rung: Int,
        won: Boolean,
    ): GauntletProgress {
        val cur = readGauntlet()
        val nextCleared = if (won) maxOf(cur.clearedRung, rung) else cur.clearedRung
        val next =
            GauntletProgress(
                clearedRung = nextCleared,
                wins = cur.wins + if (won) 1 else 0,
            )
        settings.putInt(KEY_GAUNTLET_CLEARED, next.clearedRung)
        settings.putInt(KEY_GAUNTLET_WINS, next.wins)
        _gauntletFlow.value = next
        return next
    }

    /** Wipe the gauntlet ladder progress (folded into [resetLedger]). */
    fun resetGauntlet() {
        settings.remove(KEY_GAUNTLET_CLEARED)
        settings.remove(KEY_GAUNTLET_WINS)
        _gauntletFlow.value = readGauntlet()
    }

    private val _gauntletFlow = MutableStateFlow(readGauntlet())

    /** Reactive gauntlet ladder progress for the Home entry + the Gauntlet screen. */
    val gauntletFlow: StateFlow<GauntletProgress> = _gauntletFlow.asStateFlow()

    // ── In-App Review ───────────────────────────────────────────────────────────

    private var reviewShownVersion: String
        get() = settings.getString(KEY_REVIEW_SHOWN_VERSION, defaultValue = "")
        set(v) {
            settings.putString(KEY_REVIEW_SHOWN_VERSION, v)
        }

    fun shouldShowReview(currentVersion: String): Boolean = reviewShownVersion != currentVersion

    fun markReviewShown(version: String) {
        reviewShownVersion = version
    }

    // H2H codec: "id:played:wins;id:played:wins" — avoids a serialization dependency in :core:prefs.
    private fun encodeH2H(map: Map<String, PersonaRecord>): String = map.entries.joinToString(";") { (id, r) -> "$id:${r.played}:${r.wins}" }

    private fun decodeH2H(raw: String): Map<String, PersonaRecord> {
        if (raw.isBlank()) return emptyMap()
        return raw
            .split(";")
            .mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0]
                val played = parts[1].toIntOrNull() ?: return@mapNotNull null
                val wins = parts[2].toIntOrNull() ?: return@mapNotNull null
                if (id.isBlank()) null else id to PersonaRecord(played, wins)
            }.toMap()
    }
}

/**
 * Turn pacing tier (M5 §2a). [multiplier] scales the bot-step delays the ViewModel waits between
 * each bot action: SLOW lingers (1.4×) for first-time / 10-player legibility, FAST (0.5×) cuts the
 * idle dead-time. The actual delay constants live in the feature layer — this only carries the factor.
 */
enum class TurnSpeed(
    val multiplier: Float,
) {
    SLOW(1.4f),
    NORMAL(1.0f),
    FAST(0.5f),
    ;

    companion object {
        fun fromName(name: String): TurnSpeed = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** One opponent persona's lifetime head-to-head record against the human. */
data class PersonaRecord(
    val played: Int,
    val wins: Int,
)

/**
 * Immutable lifetime career ledger surfaced on Home + the Roznamcha career screen.
 * [wins] is human wins; [headToHead] maps a persona id to how often the human faced them and won.
 */
data class StatsLedger(
    val games: Int = 0,
    val wins: Int = 0,
    val bluffsHeld: Int = 0,
    val bluffsCaught: Int = 0,
    val headToHead: Map<String, PersonaRecord> = emptyMap(),
) {
    val losses: Int get() = games - wins

    /** Win rate in [0,1]; 0 when no games played. */
    val winRate: Float get() = if (games == 0) 0f else wins.toFloat() / games
}

/**
 * M6b — one game's worth of decision-quality counters, accumulated by the feature layer (off the UI
 * thread, reusing the decision-coach's advisor read for each human decision) and folded into the
 * lifetime [DecisionLedger] at game end via [AppPrefs.recordDecisionTally].
 *
 * - [decisions]      every human decision the advisor could read (≥ 2 legal moves).
 * - [matchedBest]    of those, how many played the advisor's single recommended move.
 * - [evLostMilli]    cumulative (winProb(best) − winProb(chosen)) × 1000, clamped ≥ 0 per decision.
 * - [challenges]     Challenge intents the human submitted.
 * - [challengesGood] of those, how many were +EV per the read (P(bluff) ≥ 0.5 at decision time).
 * - [bluffsTried]    bluff actions/blocks attempted (claimed a role not held).
 * - [bluffsOk]       of those attempts, how many were not caught (folded from the M3 bluff reveal scrape).
 */
data class DecisionTally(
    val decisions: Int = 0,
    val matchedBest: Int = 0,
    val evLostMilli: Long = 0L,
    val challenges: Int = 0,
    val challengesGood: Int = 0,
    val bluffsTried: Int = 0,
    val bluffsOk: Int = 0,
) {
    /** True when there is anything worth folding (so an empty game doesn't churn the store). */
    val isEmpty: Boolean
        get() = decisions == 0 && challenges == 0 && bluffsTried == 0

    /** % of this game's coachable decisions that matched the advisor best, 0 when none. */
    val accuracyPct: Int get() = if (decisions == 0) 0 else (matchedBest * 100 / decisions)
}

/**
 * M6b — lifetime decision-quality dossier surfaced on the Career screen. Tracks how sharply the human
 * plays vs the (fair, internally-redacted) [com.kursi.ai.advisor.MoveAdvisor] read: how often they
 * find the best move, how much win-probability they bleed, how disciplined their challenges are, and
 * how often their bluffs survive.
 */
data class DecisionLedger(
    val decisions: Int = 0,
    val matchedBest: Int = 0,
    val evLostMilli: Long = 0L,
    val challenges: Int = 0,
    val challengesGood: Int = 0,
    val bluffsTried: Int = 0,
    val bluffsOk: Int = 0,
) {
    /** True when no decisions have been recorded yet (hide the dossier). */
    val isEmpty: Boolean get() = decisions == 0

    /** % of decisions matching the advisor's best move, in [0,100]; 0 when none recorded. */
    val accuracyPct: Int get() = if (decisions == 0) 0 else (matchedBest * 100 / decisions)

    /** Average win-probability lost per decision, in [0,1]; 0 when none recorded. */
    val avgEvLost: Float get() = if (decisions == 0) 0f else (evLostMilli / 1000f) / decisions

    /** Average EV lost as whole percentage points per decision (e.g. 4 = 4% win-prob bled). */
    val avgEvLostPct: Int get() = (avgEvLost * 100).toInt()

    /** Challenge discipline: % of challenges that were +EV per the read; 0 when none made. */
    val challengeAccuracyPct: Int get() = if (challenges == 0) 0 else (challengesGood * 100 / challenges)

    /** Bluff success: % of bluff attempts that survived uncaught; 0 when none attempted. */
    val bluffSuccessPct: Int get() = if (bluffsTried == 0) 0 else (bluffsOk * 100 / bluffsTried)

    /** A short in-voice grade keyed off accuracy + EV bled. Used for the Career headline stamp. */
    val grade: DecisionGrade get() = DecisionGrade.of(accuracyPct, avgEvLostPct, decisions)
}

/**
 * In-voice decision-quality grade (M6b). The bilingual labels live in the strings layer; this enum
 * only carries the verdict tier so the UI/strings can render it in the active language.
 */
enum class DecisionGrade {
    /** Not enough data yet (few decisions recorded). */
    UNRATED,

    /** Tight, near-optimal play: high match rate, little EV bled. "Sharp Babu". */
    SHARP,

    /** Competent, mostly-sound reads. "Steady Hand". */
    STEADY,

    /** Loose: misses a lot of best moves / bleeds EV. "Reckless". */
    RECKLESS,

    ;

    companion object {
        /**
         * Tiering: needs a sample of [decisions] ≥ 6 to rate at all. SHARP = ≥ 70% best-move match
         * AND ≤ 5% avg EV bled; RECKLESS = < 45% match OR ≥ 12% avg EV bled; STEADY in between.
         */
        fun of(
            accuracyPct: Int,
            avgEvLostPct: Int,
            decisions: Int,
        ): DecisionGrade =
            when {
                decisions < 6 -> UNRATED
                accuracyPct >= 70 && avgEvLostPct <= 5 -> SHARP
                accuracyPct < 45 || avgEvLostPct >= 12 -> RECKLESS
                else -> STEADY
            }
    }
}

/**
 * M6d — immutable ranked ELO standing surfaced on Home (a compact strip), Career, and the
 * Leaderboard. [rating] maps to a bilingual sarkari rank tier ([SarkariRank.of]); [history] is the
 * rating spark-line (oldest first, always ≥ 1 point with the seed as baseline).
 */
data class RankedStanding(
    val rating: Int = AppPrefs.ELO_SEED,
    val peak: Int = AppPrefs.ELO_SEED,
    val games: Int = 0,
    val history: List<Int> = listOf(AppPrefs.ELO_SEED),
) {
    /** The sarkari rank tier this rating sits in. */
    val rank: SarkariRank get() = SarkariRank.of(rating)

    /** True before any ranked game has been folded in (hide the strip's progress framing). */
    val isProvisional: Boolean get() = games == 0

    /** Progress in [0,1] from this rank's floor toward the next rank's floor (1 at the top tier). */
    val tierProgress: Float get() = rank.progressAt(rating)
}

/**
 * M6d — the sarkari RANK ladder. A rating band maps to a bureaucratic title; the player climbs from
 * Clerk to Cabinet Secretary as their ELO rises. [floor] is the inclusive lower rating bound; tiers
 * are contiguous and ordered. Bilingual labels live in the strings layer — this enum only carries the
 * tier identity + band so the UI/strings can render the active language.
 */
enum class SarkariRank(
    val floor: Int,
) {
    CLERK(0), // Babu / Clerk          — entry rung
    SECTION_OFFICER(950), // Section Officer
    UNDER_SECRETARY(1100), // Under Secretary
    DEPUTY_SECRETARY(1250), // Deputy Secretary
    JOINT_SECRETARY(1400), // Joint Secretary
    SECRETARY(1600), // Secretary
    CABINET_SECRETARY(1850), // Cabinet Secretary     — top rung
    ;

    /** The next rung up, or null if this is already the top tier. */
    val next: SarkariRank? get() = entries.getOrNull(ordinal + 1)

    /** Progress in [0,1] from this tier's [floor] toward [next]'s floor; 1f at the top tier. */
    fun progressAt(rating: Int): Float {
        val ceil = next?.floor ?: return 1f
        val span = (ceil - floor).coerceAtLeast(1)
        return ((rating - floor).toFloat() / span).coerceIn(0f, 1f)
    }

    companion object {
        /** The rank tier a [rating] falls into (the highest tier whose [floor] it clears). */
        fun of(rating: Int): SarkariRank = entries.last { rating >= it.floor }
    }
}

/**
 * M6d — immutable daily-challenge standing. [lastDay] is the calendar epoch-day of the most recent
 * recorded daily (-1 = none); [streak] is the current consecutive-day win streak; [bestStreak] the
 * personal record. The "is today done?" check is [AppPrefs.isDailyDone].
 */
data class DailyStanding(
    val lastDay: Long = -1L,
    val lastWon: Boolean = false,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val played: Int = 0,
    val won: Int = 0,
) {
    /** True when at least one daily has been completed (show the standing). */
    val hasPlayed: Boolean get() = played > 0

    /** Daily win rate in [0,1]; 0 when none played. */
    val winRate: Float get() = if (played == 0) 0f else won.toFloat() / played

    /** True iff the daily for [epochDay] is the one already recorded. */
    fun isDoneFor(epochDay: Long): Boolean = lastDay == epochDay
}

/**
 * M6e — immutable GAUNTLET ladder progress (Tarakki ki Seedhi / the sarkari "promotion ladder"). The
 * player climbs a fixed bracket of difficulty rungs (Easy → Grandmaster); [clearedRung] is the highest
 * 0-based rung index they have BEATEN (-1 = none yet). The "current target" is the next rung up. The
 * actual rung definitions (difficulty + seat count + seed + nameplate) live in the feature layer's
 * GauntletLadder — this carries only the persisted progress so :core:prefs stays engine-free.
 *
 * @property clearedRung Highest rung index beaten (-1 = none, ladder.lastIndex = fully conquered).
 * @property wins        Lifetime gauntlet bouts won (clears + re-wins on already-cleared rungs).
 */
data class GauntletProgress(
    val clearedRung: Int = -1,
    val wins: Int = 0,
) {
    /** True before any rung has been cleared (fresh ladder). */
    val isFresh: Boolean get() = clearedRung < 0

    /** The next rung to attempt (0-based). Equals cleared+1; the UI clamps it to the ladder size. */
    val targetRung: Int get() = clearedRung + 1

    /** Number of rungs cleared so far (0..ladderSize). */
    val clearedCount: Int get() = clearedRung + 1

    /** True once [clearedRung] reaches [ladderLastIndex] — the whole ladder is conquered. */
    fun isConquered(ladderLastIndex: Int): Boolean = clearedRung >= ladderLastIndex
}
