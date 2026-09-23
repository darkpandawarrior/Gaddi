package com.kursi.core.network

import com.kursi.protocol.wire.ClientMessage
import com.kursi.protocol.wire.ServerMessage
import com.kursi.protocol.wire.WireIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The high-level online client the UI layer talks to. It owns a single match connection's full
 * lifecycle and projects every server frame into one [OnlineUiState] flow whose shape mirrors the
 * offline `feature.game.GameUiState`, so a later wave can render an online match through the existing
 * `GameScreen` largely unchanged.
 *
 * Responsibilities:
 * 1. Connect by (host, port, roomCode) — a quick-match/private-room code OR a LAN-discovered address.
 * 2. Maintain the [ConnectionState] lifecycle (Connecting → Connected → Dropped → Reconnecting).
 * 3. AUTO-RECONNECT with capped exponential backoff, RESUMING the same seat (so the player keeps their
 *    hand) via [ClientMessage.JoinRoom.reconnectSeat].
 * 4. Expose [submit] / [pass] to send the human's intents.
 *
 * Threading: everything runs inside [scope] (typically a ViewModel scope). [uiState] is a hot
 * [StateFlow] safe to `collectAsStateWithLifecycle()`.
 *
 * This class builds ONLY on the public client API ([GaddiClient] / [GaddiSession]) and the wire
 * protocol — it does not touch the engine, the AI, or any UI module.
 *
 * @param transportFactory Creates a fresh low-level [GaddiClient] for EACH connection attempt. A factory
 *                         (not a single shared instance) is required because a dropped connection's
 *                         transport may be unusable for redialing — every reconnect needs a clean client,
 *                         and the old one is closed. Defaults to `::GaddiClient`.
 * @param scope            The coroutine scope owning the connection loop.
 * @param config           Reconnect tuning (attempts, backoff). Defaults suit mobile networks.
 */
class OnlineGaddiClient(
    private val scope: CoroutineScope,
    private val config: ReconnectConfig = ReconnectConfig(),
    private val transportFactory: () -> GaddiClient = ::GaddiClient,
) {
    /** Reconnect tuning. */
    data class ReconnectConfig(
        /** Max consecutive reconnect attempts before giving up and staying [ConnectionState.Dropped]. */
        val maxAttempts: Int = 6,
        /** Backoff for attempt n is min(initialBackoffMs * 2^(n-1), maxBackoffMs). */
        val initialBackoffMs: Long = 250,
        val maxBackoffMs: Long = 5_000,
        /** When false, a drop is terminal (used by tests that assert a single clean run). */
        val autoReconnect: Boolean = true,
    )

    private val _uiState = MutableStateFlow(OnlineUiState())

    /** The single source of truth for the online match UI. Mirrors the offline GameUiState shape. */
    val uiState: StateFlow<OnlineUiState> = _uiState.asStateFlow()

    // Connection coordinates — captured on connect(), reused for reconnect.
    private var host: String = ""
    private var port: Int = 0
    private var roomCode: String = ""
    private var matchId: String = ""

    // The seat assigned by the server. Used to RESUME the same seat on reconnect.
    private var mySeat: Int? = null

    // Monotonic client seq for SubmitIntent/Pass frames.
    private var seqCounter: Long = 0L

    // The currently-live session (null between connections). send() routes through it.
    private var liveSession: GaddiSession? = null

    // The transport backing the currently-live session — closed when the connection ends or close() runs.
    private var liveTransport: GaddiClient? = null

    // True once the current socket lifetime reached Connected — used to reset the reconnect backoff budget.
    private var connectedThisAttempt: Boolean = false

    private var connectionJob: Job? = null

    /**
     * Opens the connection and starts the lifecycle loop. Idempotent-ish: a second call cancels the
     * previous loop and starts fresh.
     *
     * @param reconnectSeat optional seat to resume on the FIRST connect (e.g. resuming a saved match).
     *                      Subsequent automatic reconnects always reuse the last-assigned seat.
     */
    fun connect(
        host: String,
        port: Int,
        roomCode: String,
        matchId: String = roomCode,
        reconnectSeat: Int? = null,
    ) {
        this.host = host
        this.port = port
        this.roomCode = roomCode
        this.matchId = matchId
        this.mySeat = reconnectSeat
        connectionJob?.cancel()
        connectionJob = scope.launch { runConnectionLoop() }
    }

    // TooGenericExceptionCaught: same reason as RoomApi.postForCode — a dropped socket surfaces as
    // a different platform type on every target, and this loop's whole job is to treat all of them
    // as "reconnect". CancellationException is rethrown above the catch so caller cancellation is
    // never mistaken for a drop, and e.message goes into ConnectionState.Dropped.

    /**
     * The connect → run → drop → backoff → reconnect loop. Each iteration runs ONE socket lifetime.
     * On normal completion (server closed the socket) or failure, it either reconnects (resuming the
     * seat) or settles into a terminal [ConnectionState].
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun runConnectionLoop() {
        var attempt = 0
        while (true) {
            _uiState.update {
                it.copy(
                    connection =
                        if (attempt == 0) {
                            ConnectionState.Connecting
                        } else {
                            ConnectionState.Reconnecting(attempt)
                        },
                    lastError = null,
                )
            }

            connectedThisAttempt = false
            val cleanFinish =
                try {
                    runSingleConnection()
                    true // the server closed the socket normally (e.g. match over, or graceful close)
                } catch (e: CancellationException) {
                    throw e // caller cancelled — propagate, do NOT reconnect
                } catch (e: Exception) {
                    _uiState.update { it.copy(connection = ConnectionState.Dropped(e.message)) }
                    false
                } finally {
                    liveSession = null
                    liveTransport?.close()
                    liveTransport = null
                }

            // A connection that actually reached Connected resets the backoff budget, so a long-lived
            // match that suffers several SEPARATE blips doesn't exhaust maxAttempts on the cumulative count.
            if (connectedThisAttempt) attempt = 0

            // Stop reconnecting once the game is over — the match is done, nothing to resume.
            if (_uiState.value.isGameOver) {
                _uiState.update { it.copy(connection = ConnectionState.Closed) }
                return
            }

            if (cleanFinish) {
                // Socket closed without an exception and game not over: treat as a drop and retry
                // (the server only closes mid-match on an abnormal condition).
                _uiState.update {
                    if (it.connection is ConnectionState.Connected) {
                        it.copy(connection = ConnectionState.Dropped(cause = "socket closed"))
                    } else {
                        it
                    }
                }
            }

            if (!config.autoReconnect) {
                _uiState.update { it.copy(connection = ConnectionState.Closed) }
                return
            }

            attempt += 1
            if (attempt > config.maxAttempts) {
                _uiState.update {
                    it.copy(connection = ConnectionState.Dropped(cause = "gave up after ${config.maxAttempts} attempts"))
                }
                return
            }

            val backoff =
                minOf(
                    config.initialBackoffMs shl (attempt - 1),
                    config.maxBackoffMs,
                )
            delay(backoff)
        }
    }

    /**
     * Runs a single socket lifetime: connect (resuming [mySeat] if known), then collect every
     * [ServerMessage] into [uiState] until the flow completes (socket closed) or throws.
     */
    private suspend fun runSingleConnection() {
        val transport = transportFactory()
        liveTransport = transport
        val session =
            transport.connect(
                host = host,
                port = port,
                roomCode = roomCode,
                matchId = matchId,
                reconnectSeat = mySeat,
            )
        liveSession = session
        session.incoming.collect { msg -> onServerMessage(msg) }
    }

    /** Fold one server frame into [uiState] — the single projection point. */
    private fun onServerMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.RoomJoined -> {
                mySeat = msg.seat
                connectedThisAttempt = true
                _uiState.update {
                    it.copy(
                        mySeat = msg.seat,
                        connection = ConnectionState.Connected(msg.seat),
                    )
                }
            }

            is ServerMessage.StateUpdate -> {
                val view = msg.view
                _uiState.update { prev ->
                    val merged = (prev.recentEvents + msg.events).takeLast(OnlineUiState.MAX_EVENTS)
                    prev.copy(
                        view = view,
                        legalIntents = view.legalIntents(),
                        recentEvents = merged,
                        isHumanTurn = view.isMyTurn(),
                        // A late StateUpdate confirms we are live again after a reconnect resync.
                        connection =
                            (prev.connection as? ConnectionState.Connected)
                                ?: ConnectionState.Connected(mySeat ?: view.viewer),
                        mySeat = prev.mySeat ?: view.viewer,
                    )
                }
            }

            is ServerMessage.GameOver -> {
                _uiState.update {
                    it.copy(isGameOver = true, winnerSeat = msg.winnerSeat, isHumanTurn = false, legalIntents = emptyList())
                }
            }

            is ServerMessage.Error -> {
                _uiState.update { it.copy(lastError = msg.reason) }
            }
        }
    }

    /**
     * Submits a player [WireIntent]. No-op (logs nothing) if there is no live session — the UI should
     * only enable inputs while [uiState] reports [ConnectionState.Connected] and [OnlineUiState.isHumanTurn].
     */
    suspend fun submit(intent: WireIntent) {
        val session = liveSession ?: return
        session.send(
            ClientMessage.SubmitIntent(matchId = matchId, seq = ++seqCounter, intent = intent),
        )
    }

    /** Fast-path Pass (the common reaction-window tap). */
    suspend fun pass() {
        val session = liveSession ?: return
        session.send(ClientMessage.Pass(matchId = matchId, seq = ++seqCounter))
    }

    /** Closes the connection permanently — cancels the lifecycle loop, no further reconnects. */
    fun close() {
        connectionJob?.cancel()
        connectionJob = null
        liveSession = null
        liveTransport?.close()
        liveTransport = null
        _uiState.update { it.copy(connection = ConnectionState.Closed) }
    }
}
