package com.kursi.protocol.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─────────────────────────── Json instance ───────────────────────────

/**
 * The canonical [Json] instance for all Gaddi wire messages.
 *
 * Configuration rationale:
 * - [ignoreUnknownKeys] = true — enables additive server-to-client evolution: a client built against an
 *   older schema safely ignores new fields added by a newer server. Critical for rolling updates.
 * - [classDiscriminator] = "type" — the polymorphic type key on every sealed-class frame. Stable name
 *   matches what clients log, trace, and display in network panels.
 * - [encodeDefaults] = false — keeps frames compact; default-valued fields (e.g. [WirePlayerView.schemaVersion])
 *   are omitted from frames produced by the current schema version and must be handled gracefully when absent.
 */
val GaddiJson: Json =
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true // schemaVersion must always be present so clients can gate on it
    }

// ─────────────────────────── Client → Server ───────────────────────────

/**
 * Every message a client may send to the server.
 *
 * All variants carry [matchId] so the server can route the message to the correct game session
 * without inspecting [intent].
 */
@Serializable
sealed interface ClientMessage {
    val matchId: String

    /**
     * Request to join a game room by its short room code (e.g. "ABCD").
     * The server responds with [ServerMessage.RoomJoined] on success or [ServerMessage.Error] on failure.
     *
     * [reconnectSeat]: when non-null, the client is RESUMING a seat it previously held and dropped
     * (e.g. after a network blip). The server honours it if that seat is currently vacant, then
     * immediately re-sends the seat's current [ServerMessage.StateUpdate]. When null, a fresh seat is
     * assigned. [ServerMessage.RoomJoined.reconnected] tells the client which path was taken.
     */
    @Serializable
    data class JoinRoom(
        override val matchId: String,
        val roomCode: String,
        val reconnectSeat: Int? = null,
    ) : ClientMessage

    /**
     * Submit a game intent (action declaration, challenge, block, pass, or exchange choice).
     * The server validates the intent via [com.kursi.engine.applyIntent] and responds with a
     * [ServerMessage.StateUpdate] to all players if accepted, or [ServerMessage.Error] to the
     * sender if rejected.
     */
    @Serializable
    data class SubmitIntent(
        override val matchId: String,
        /** Monotonically-increasing client sequence number. The server echoes this in its reply so
         *  the client can correlate responses to requests and detect dropped frames. */
        val seq: Long,
        val intent: WireIntent,
    ) : ClientMessage

    /**
     * Explicit pass — client passes their reaction window without challenging or blocking.
     * Semantically equivalent to [SubmitIntent] with [WireIntent.Pass], but used as a fast-path
     * for the common case where the client simply taps "Pass" without building a full intent.
     */
    @Serializable
    data class Pass(
        override val matchId: String,
        val seq: Long,
    ) : ClientMessage

    /**
     * Online mirror of the offline [com.kursi.feature.game.GameAction.ContinueBeat] tap: a connected
     * human asks the server to release a paced beat NOW instead of waiting out the bounded server-side
     * ack timeout (Track 6 — see `MatchActor.BeatAckGate` in `:server`). Any seated human's ack is
     * sufficient to unblock the table; the beat is a shared, table-wide pace, not a per-seat one.
     * A no-op if no beat is currently pending.
     */
    @Serializable
    data class ContinueBeat(
        override val matchId: String,
    ) : ClientMessage
}

// ─────────────────────────── Server → Client ───────────────────────────

/**
 * Every message the server may push to a connected client.
 *
 * [matchId] is present on every variant so clients can ignore messages destined for a different
 * tab/session without inspecting the payload. [seq] is a monotonically-increasing server sequence
 * number; gaps indicate dropped frames and should trigger a [ClientMessage.JoinRoom] re-sync.
 */
@Serializable
sealed interface ServerMessage {
    val matchId: String
    val seq: Long

    /**
     * The primary message: the server has accepted an intent and the state has advanced.
     * [view] is the per-viewer redacted state — each player receives a *different* [WirePlayerView].
     *
     * Security note: the server calls `redact(state, viewer)` then `.toWire()` per connected player,
     * never broadcasting a single shared payload. Hidden card roles are structurally absent from [view].
     */
    @Serializable
    data class StateUpdate(
        override val matchId: String,
        override val seq: Long,
        val view: WirePlayerView,
        /**
         * The descriptive [WireGameEvent]s produced by the step that led to this state, ALREADY
         * projected for this recipient seat via [com.kursi.protocol.wire.toWireFor]. Optional and
         * additive (defaults empty): a client may ignore it and rely on [view] alone, or use it to
         * drive history/animation. Secret CardIds inside these events are nulled for non-owners, so
         * this list is safe to send to [view]'s viewer and only that viewer.
         */
        val events: List<WireGameEvent> = emptyList(),
    ) : ServerMessage

    /**
     * Sent to all players when a room is ready (enough players have joined and the game has started).
     * [seat] is the zero-based seat index assigned to this specific client.
     */
    @Serializable
    data class RoomJoined(
        override val matchId: String,
        override val seq: Long,
        val seat: Int,
        val playerCount: Int,
        /** True when this join RESUMED a previously-held seat (reconnect) rather than taking a fresh one. */
        val reconnected: Boolean = false,
    ) : ServerMessage

    /**
     * Sent when the game ends. [winnerSeat] is the seat index of the winning player.
     * Followed by a final [StateUpdate] whose [WirePlayerView.phase] is [WirePhaseView.Over].
     */
    @Serializable
    data class GameOver(
        override val matchId: String,
        override val seq: Long,
        val winnerSeat: Int,
    ) : ServerMessage

    /**
     * Sent to the triggering client when an intent is rejected (e.g. out-of-turn, illegal move).
     * Other players do not receive this message; only the sender's intent was invalid.
     */
    @Serializable
    data class Error(
        override val matchId: String,
        override val seq: Long,
        /** Echo of the client's [ClientMessage.SubmitIntent.seq] that caused this error, or -1 if not applicable. */
        val clientSeq: Long,
        val reason: String,
    ) : ServerMessage
}
