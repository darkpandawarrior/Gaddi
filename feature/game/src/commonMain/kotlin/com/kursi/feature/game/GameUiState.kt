package com.kursi.feature.game

import com.kursi.ai.OpponentInsight
import com.kursi.ai.advisor.MoveAdvice
import com.kursi.engine.GameEvent
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.feature.game.narrative.ArcId
import com.kursi.feature.game.narrative.ChatMessage
import com.kursi.feature.game.narrative.ChatSuggestion

/**
 * Per-opponent persona info surfaced to the UI so the screen can show real names
 * and monograms instead of generic "P1/P2/P3" labels.
 *
 * [seatColorArgb] is the persona's locked Okabe-Ito hue packed as a Long ARGB.
 */
data class OpponentPersona(
    val playerId: PlayerId,
    val name: String,
    val monogram: String,
    /** ARGB packed Long — e.g. 0xFF0072B2L. Convert to Color in the UI layer. */
    val seatColorArgb: Long,
)

/**
 * Immutable MVI state derived from the human player's [PlayerView].
 *
 * This is the single source of truth surfaced to the UI. It is produced exclusively by
 * [session.GameSession] (which calls [com.kursi.engine.redact] before building this) so the
 * composable layer never touches [com.kursi.engine.GameState].
 */
data class GameUiState(
    /** The redacted view — only what the human player is allowed to see. */
    val view: PlayerView,
    /** Concrete legal intents for the human's current turn. Empty when it is NOT the human's turn. */
    val legalIntents: List<Intent>,
    /** Recent game events for the history panel (capped, most-recent last). */
    val recentEvents: List<GameEvent>,
    /** True when it is currently the human player's turn to act. */
    val isHumanTurn: Boolean,
    /** True when the game has ended. */
    val isGameOver: Boolean,
    /** The winner's player id (raw int seat), or null if the game is still running. */
    val winnerSeat: Int?,
    /**
     * Persona info for each bot seat — keyed by [PlayerId].
     * Empty map means no persona data (e.g. online game, or pre-persona test fixture).
     */
    val opponentPersonas: Map<PlayerId, OpponentPersona> = emptyMap(),
    /**
     * DECISION-COACH advice for the human's current decision, ranked best-first, with exactly
     * one entry flagged [MoveAdvice.recommended]. Empty when it is not the human's turn, or
     * while the advice is still being computed asynchronously (the screen renders fine without
     * it and lights up the badges/odds once it arrives). Produced by [com.kursi.ai.advisor.MoveAdvisor].
     */
    val advice: List<MoveAdvice> = emptyList(),
    /**
     * PUBLIC-info opponent dossiers — one per opponent of the human, in seat order, derived by the
     * decision-coach's belief from table-visible claims/blocks/reveals ONLY. Carries the real read
     * the UI surfaces: posterior over likely roles, per-role claim counts, bluff-caught tallies and
     * the inferred bluffRate. SECRECY: never contains an opponent's hidden cards. Empty until the
     * session has run the coach (e.g. mid-compute, or a pre-insight test fixture). Keyed for lookup
     * via [insightFor]. Produced by [com.kursi.ai.OpponentInsight.from].
     */
    val opponentInsights: List<OpponentInsight> = emptyList(),
    /**
     * DECISION-COACH TOGGLE — mirrors [AppPrefs.coachEnabled].
     *
     * When true (default), the full M2 proactive guidance is shown: recommended-move stars,
     * per-option success odds/odds pills, safe-vs-bluff REAL/BLUFF badges, the coach-explains
     * line, and the PickTarget weakest-target star.
     *
     * When false, all proactive guidance is HIDDEN — the player reads the table unaided.
     * Observational, player-initiated info (long-press dossier/inspect chits, opponent claim
     * tiles, suspicion pips, the Roznamcha log) is ALWAYS shown regardless of this flag.
     */
    val coachEnabled: Boolean = true,
    /**
     * M5 PASS-AND-PLAY. The raw seat index of the human who must act NOW (whose redacted [view] is
     * shown), or null when it is not a human's turn. In single-human vs-AI this is always 0 on the
     * human's turn. In pass-and-play it changes between hot-seat players — the handoff guard watches
     * it to know when to blank the screen and prompt "pass the device".
     */
    val activeSeat: Int? = null,
    /** True when this is a multi-human pass-and-play match (drives the handoff guard). */
    val isPassAndPlay: Boolean = false,
    /**
     * DARBAR / NARRATIVE MODE — when true, bots chat freely, the four story arcs are playable, and
     * bot targeting can be socially nudged (conspiracy + flaw-baiting). Off = the pristine, tuned AI
     * with no chat (ranked / normal play is byte-for-byte unchanged).
     */
    val narrativeEnabled: Boolean = false,
    /** The Darbar's running chat (most-recent last), capped. Empty outside narrative mode. */
    val chatFeed: List<ChatMessage> = emptyList(),
    /** What the player can SAY right now — tappable arc-start / arc-reply / table-talk chips. */
    val chatSuggestions: List<ChatSuggestion> = emptyList(),
    /** The arcs currently in flight (drives a small "live kissa" indicator). */
    val activeArcs: List<ArcId> = emptyList(),
    /** Count of chat lines the player hasn't opened yet — drives the Darbar toggle's unread badge. */
    val unreadChat: Int = 0,
    /**
     * Lifetime coins earned per player — populated only when KhazanaRaj or Emergency variant is active.
     * Public information: everyone can see each other's progress toward the coin milestone.
     * Key = PlayerId; value = total coins earned (never decremented by spending).
     */
    val lifetimeCoins: Map<PlayerId, Int> = emptyMap(),
    /**
     * PROGRESSIVE-DISCLOSURE layer (spec §3). Mirrors [AppPrefs.densityLayer]. Default ANALYST =
     * today's full-instrument screen. Overlays migrate to gate on this in Wave 1 Track 4; adding it
     * here changes no rendering yet.
     */
    val densityLayer: DensityLayer = DensityLayer.ANALYST,
    /**
     * BEAT GATE (spec §5). Non-null when the paced bot round has shown a meaningful beat and is
     * waiting for the player to tap to continue (FOCUS/GUIDED). Null while flowing (ANALYST/AUTO) or
     * on the human's own turn. The UI shows a "tap to continue" affordance and dispatches
     * [GameAction.ContinueBeat].
     */
    val pendingBeat: PendingBeat? = null,
    /**
     * MUNSHI NARRATION (spec §8.1, §8.5, §8.6) — the AI-generated line for the current beat, once it
     * lands. Null until the async narration job (if any provider tier is available) resolves, or
     * whenever nothing beats the templated floor. [com.kursi.feature.game.overlays.headlineFor]
     * always renders first regardless — this is a display-only, non-blocking UPGRADE-IN-PLACE on
     * top of it, never a replacement source of truth: it is never folded into `humanIntentLog`,
     * never mutates `GameState`, never gates a legal action, and is never persisted into a replay
     * record (a fresh replay simply shows the templated line, or regenerates its own narration).
     */
    val narrationText: String? = null,
    /**
     * MUNSHI STREAMING (spec §8.1, §8.6) — true while [narrationText] may still grow for THIS beat
     * (tokens are still arriving from the provider), false once the line has settled (the provider
     * finished, or [narrationText] is null because nothing ever upgraded the templated floor).
     * Display-only, same non-authoritative status as [narrationText] itself — see its own kdoc.
     */
    val narrationStreaming: Boolean = false,
) {
    /**
     * DENSITY GATE (spec §3) — whether coach guidance (recommended-move stars, REAL/BLUFF badges,
     * odds pills) should render right now. FOCUS hides guidance unconditionally (it isn't in the
     * FOCUS whitelist, regardless of the [coachEnabled] toggle); GUIDED and ANALYST defer to
     * [coachEnabled] exactly as today — so this is a no-op in ANALYST (today's behavior, byte
     * identical) and simply forces the toggle off while in FOCUS.
     */
    val coachGuidanceVisible: Boolean
        get() = densityLayer != DensityLayer.FOCUS && coachEnabled

    /** The PUBLIC-info dossier for [id], or null if none has been computed yet. */
    fun insightFor(id: PlayerId): OpponentInsight? = opponentInsights.firstOrNull { it.opponentId == id }

    /** KHAZANA RAJ — Darja (corruption level) for [id] based on lifetime coins (0=none, 4=Sarkar). */
    fun darjaLevelFor(id: PlayerId): Int {
        val coins = (lifetimeCoins[id] ?: 0)
        return when {
            coins >= 20 -> 4 // Sarkar
            coins >= 16 -> 3 // Mantri
            coins >= 12 -> 2 // Sahib
            coins >= 8 -> 1 // Mukhiya
            else -> 0
        }
    }

    /** KHAZANA RAJ — progress fraction [0.0..1.0] toward [GameConfig.khazanaTarget] for [id]. */
    fun khazanaProgressFor(
        id: PlayerId,
        target: Int,
    ): Float = if (target <= 0) 0f else ((lifetimeCoins[id] ?: 0).toFloat() / target).coerceIn(0f, 1f)

    companion object {
        /** Maximum number of events kept in [recentEvents]. */
        const val MAX_EVENTS = 30

        /** Darja level names (index = level 0-4). */
        val DARJA_NAMES = listOf("—", "Mukhiya", "Sahib", "Mantri", "Sarkar")
    }
}
