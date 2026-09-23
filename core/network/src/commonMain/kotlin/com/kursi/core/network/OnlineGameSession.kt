package com.kursi.core.network

import com.kursi.engine.Intent
import com.kursi.protocol.wire.ClientMessage
import com.kursi.protocol.wire.ServerMessage
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.toWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * High-level online game session that wraps a [GaddiSession].
 *
 * Drives the [playerView] StateFlow from incoming [ServerMessage.StateUpdate] frames and
 * exposes a [submit] function that converts an engine [Intent] to a [ClientMessage.SubmitIntent]
 * and sends it over the wire.
 *
 * Lifecycle: call [start] to begin collecting from the session, cancel the returned [Job]
 * (or the owning [scope]) to tear down. [close] cancels the job and closes the underlying client.
 *
 * Example (inside a ViewModel):
 * ```
 * val session = OnlineGameSession(gaddiSession, viewModelScope, mySeatIndex)
 * session.start()
 * // playerView updates arrive as StateUpdate frames:
 * session.playerView.collectAsStateWithLifecycle()
 * // Submit a player intent:
 * session.submit(Intent.DeclareAction(PlayerId(seat), Action.Tax))
 * ```
 *
 * @param session    The underlying [GaddiSession] (already connected and JoinRoom sent).
 * @param scope      [CoroutineScope] that owns the collection job (typically viewModelScope).
 * @param mySeat     The seat index assigned to this client (received in [ServerMessage.RoomJoined]).
 */
class OnlineGameSession(
    private val session: GaddiSession,
    private val scope: CoroutineScope,
    private val mySeat: Int,
) {
    private val _playerView = MutableStateFlow<WirePlayerView?>(null)

    /** The latest [WirePlayerView] broadcast from the server. Null until the first StateUpdate arrives. */
    val playerView: StateFlow<WirePlayerView?> = _playerView.asStateFlow()

    private val _roomJoined = MutableStateFlow<ServerMessage.RoomJoined?>(null)

    /** The [ServerMessage.RoomJoined] confirmation. Null until the server acknowledges the join. */
    val roomJoined: StateFlow<ServerMessage.RoomJoined?> = _roomJoined.asStateFlow()

    private val _lastError = MutableStateFlow<ServerMessage.Error?>(null)

    /** The most recent [ServerMessage.Error] received, or null if no errors yet. */
    val lastError: StateFlow<ServerMessage.Error?> = _lastError.asStateFlow()

    private val _gameOver = MutableStateFlow<ServerMessage.GameOver?>(null)

    /** Non-null once [ServerMessage.GameOver] is received. */
    val gameOver: StateFlow<ServerMessage.GameOver?> = _gameOver.asStateFlow()

    private var seqCounter: Long = 0L

    /**
     * Starts collecting [ServerMessage]s from the underlying [GaddiSession] inside [scope].
     * Returns the [Job] so the caller can cancel/join if needed.
     */
    fun start(): Job =
        scope.launch {
            session.incoming.collect { msg ->
                when (msg) {
                    is ServerMessage.StateUpdate -> _playerView.value = msg.view
                    is ServerMessage.RoomJoined -> _roomJoined.value = msg
                    is ServerMessage.GameOver -> _gameOver.value = msg
                    is ServerMessage.Error -> _lastError.value = msg
                }
            }
        }

    /**
     * Converts the engine [Intent] to [WireIntent] and sends it as a [ClientMessage.SubmitIntent].
     *
     * [matchId] defaults to the empty string; callers should supply the matchId received in
     * [ServerMessage.RoomJoined.matchId] for correct server-side routing.
     */
    suspend fun submit(
        intent: Intent,
        matchId: String = "",
    ) {
        val wireIntent = intent.toWire()
        val msg =
            ClientMessage.SubmitIntent(
                matchId = matchId,
                seq = ++seqCounter,
                intent = wireIntent,
            )
        session.send(msg)
    }

    /**
     * Sends a [ClientMessage.Pass] fast-path message (no need to construct a full [Intent]).
     */
    suspend fun pass(matchId: String = "") {
        val msg = ClientMessage.Pass(matchId = matchId, seq = ++seqCounter)
        session.send(msg)
    }
}
