package com.kursi.core.network

import com.kursi.protocol.wire.WireGameEvent
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WirePlayerView

/**
 * The connection lifecycle of an [OnlineGaddiClient], surfaced so the UI can show a banner
 * ("Reconnecting…"), disable inputs while dropped, etc.
 *
 * Transitions (driven by [OnlineGaddiClient]):
 * ```
 *   Idle ──connect()──▶ Connecting ──socket open + RoomJoined──▶ Connected
 *     ▲                     │                                        │
 *     │                     └────────── connect failed ─────────────┤
 *     │                                                              ▼ socket dropped
 *   Closed ◀──close()────────────────────────────── Reconnecting ◀─ Dropped
 *                                                        │  ▲           │
 *                                                        └──┘  (retry) ─┘
 * ```
 */
sealed interface ConnectionState {
    /** Never connected yet (initial). */
    data object Idle : ConnectionState

    /** A connection attempt is in flight (socket opening / awaiting [com.kursi.protocol.wire.ServerMessage.RoomJoined]). */
    data object Connecting : ConnectionState

    /** Socket is open and the seat has been assigned. [seat] is this client's seat index. */
    data class Connected(
        val seat: Int,
    ) : ConnectionState

    /** The socket dropped unexpectedly. Auto-reconnect (if enabled) moves to [Reconnecting]. */
    data class Dropped(
        val cause: String?,
    ) : ConnectionState

    /** A reconnect attempt is in flight. [attempt] is the 1-based retry counter. */
    data class Reconnecting(
        val attempt: Int,
    ) : ConnectionState

    /** The client was closed by the caller; no further reconnects. */
    data object Closed : ConnectionState
}

/**
 * Online mirror of the offline `feature.game.GameUiState`, expressed in WIRE types.
 *
 * DESIGN — why wire types, not engine [com.kursi.engine.PlayerView]:
 * A client can NEVER reconstruct an engine `PlayerView` (let alone a `GameState`) from the wire — there
 * is deliberately no `WirePlayerView.toEngine()` (the secrecy boundary, see Mappers.kt). So this state
 * carries the [WirePlayerView] the server sent, plus the same derived fields the offline `GameUiState`
 * exposes ([legalIntents], [recentEvents], [isHumanTurn], [isGameOver], [winnerSeat]). A later wave wires
 * this into the existing `GameScreen` by adapting these wire fields to whatever the screen reads — the
 * SHAPE matches field-for-field so that adaptation is mechanical, not a redesign.
 *
 * [view] is null until the first `StateUpdate` arrives (during [ConnectionState.Connecting]).
 */
data class OnlineUiState(
    /** The latest redacted view the server sent THIS seat. Null before the first StateUpdate. */
    val view: WirePlayerView? = null,
    /**
     * Concrete legal intents for this seat's current turn, derived from [view] via
     * [WirePlayerView.legalIntents]. Empty when it is NOT this seat's turn (or [view] is null).
     */
    val legalIntents: List<WireIntent> = emptyList(),
    /** Recent game events for the history panel (capped at [MAX_EVENTS], most-recent last). */
    val recentEvents: List<WireGameEvent> = emptyList(),
    /** True when it is currently THIS seat's turn to act. */
    val isHumanTurn: Boolean = false,
    /** True when the game has ended. */
    val isGameOver: Boolean = false,
    /** The winning seat index, or null while the game is still running. */
    val winnerSeat: Int? = null,
    /** This client's assigned seat index, or null before [com.kursi.protocol.wire.ServerMessage.RoomJoined]. */
    val mySeat: Int? = null,
    /** The live connection lifecycle state. */
    val connection: ConnectionState = ConnectionState.Idle,
    /** The most recent rejection reason the server sent THIS client (out-of-turn, illegal move), or null. */
    val lastError: String? = null,
) {
    companion object {
        /** Maximum number of events kept in [recentEvents] (matches the offline GameUiState cap). */
        const val MAX_EVENTS = 30
    }
}
