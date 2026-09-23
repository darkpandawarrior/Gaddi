package com.kursi.feature.game

import com.kursi.core.network.OnlineGaddiClient
import com.kursi.core.network.OnlineUiState
import com.kursi.protocol.wire.toWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ONLINE SESSION ADAPTER — drives the existing offline [GameScreen] from an [OnlineGaddiClient].
 *
 * It is the online counterpart of [GameViewModel]: it exposes the SAME `StateFlow<GameUiState?>` the
 * screen already renders and accepts the SAME [GameAction] the screen already dispatches, but its source
 * of truth is a live server connection rather than a local engine reducer.
 *
 * THE BRIDGE, in two directions:
 *  - INBOUND  (server → screen): every [OnlineUiState] the client projects is mapped into a
 *    [GameUiState]. The wire-typed view/intents/events are re-hydrated into engine types via
 *    [OnlineUiState.toEngineProjection] (see :core:network), then dressed with the online-specific
 *    fields: synthesized opponent personas (the wire carries no names) and the connection [status].
 *  - OUTBOUND (screen → server): a [GameAction.Submit] carries an engine [com.kursi.engine.Intent]
 *    already built against the rendered engine view — including the CardId-addressed choices
 *    ([com.kursi.engine.Intent.ChooseInfluenceToLose] / [com.kursi.engine.Intent.ChooseExchange]),
 *    whose CardIds came from `WirePlayerView.myCards` / `Exchange.drawn` and therefore round-trip
 *    losslessly back to the server via [com.kursi.engine.Intent.toWire].
 *
 * CONNECTION-LIFECYCLE: [status] is a derived [OnlineConnectionStatus] the screen surfaces as an
 * always-legible banner (Connecting / Waiting-for-players / In-game / Reconnecting / Opponent-disconnected
 * / Server-lost). The ALWAYS-LEGIBLE tenet: the player always knows the connection state AND whose turn
 * it is. The decision coach / moments operate on [state] exactly as offline (they read the engine view).
 *
 * The DECISION COACH is intentionally NOT wired here: online advice would require the server-authoritative
 * search and is out of scope for the bridge. [GameUiState.advice] / [GameUiState.opponentInsights] stay
 * empty, which the screen renders gracefully (it lights coach surfaces up only when present). A later wave
 * may compute table-public-info coaching on the reconstructed engine view.
 *
 * Threading: everything runs in [scope] (typically a ViewModel scope). [state] / [status] are hot
 * StateFlows safe to `collectAsStateWithLifecycle()`. Call [connect] to open, [close] to tear down.
 *
 * @param client the connection lifecycle owner (already constructed with the same [scope]).
 * @param scope  the coroutine scope owning the projection collector.
 */
class OnlineGameAdapter(
    private val client: OnlineGaddiClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<GameUiState?>(null)

    /** Mirrors [GameViewModel.state]: the [GameUiState] the [GameScreen] renders, or null pre-first-frame. */
    val state: StateFlow<GameUiState?> = _state.asStateFlow()

    private val _status = MutableStateFlow<OnlineConnectionStatus>(OnlineConnectionStatus.Connecting)

    /** The derived, always-legible connection lifecycle the screen surfaces as a status banner. */
    val status: StateFlow<OnlineConnectionStatus> = _status.asStateFlow()

    private var collectJob: Job? = null

    /**
     * Opens the connection and starts projecting server frames into [state] / [status].
     *
     * @param host     server host (loopback, LAN-discovered address, or the cloud host).
     * @param port     server port.
     * @param roomCode private room code (`/rooms/{n}`) or the public quick-match code (`/quickmatch/{n}`).
     * @param matchId  stable match id; defaults to [roomCode].
     * @param reconnectSeat optional seat to resume on first connect (e.g. resuming a saved online match).
     */
    fun connect(
        host: String,
        port: Int,
        roomCode: String,
        matchId: String = roomCode,
        reconnectSeat: Int? = null,
    ) {
        collectJob?.cancel()
        collectJob =
            scope.launch {
                client.uiState.collect { online -> project(online) }
            }
        client.connect(host = host, port = port, roomCode = roomCode, matchId = matchId, reconnectSeat = reconnectSeat)
    }

    /**
     * Dispatch a [GameAction] — the SAME entry point the offline [GameViewModel] exposes, so the screen
     * is source-agnostic. Only [GameAction.Submit] is meaningful online; [GameAction.NewGame] and
     * [GameAction.PlayBestMove] are no-ops (the server owns match creation; online coaching is out of scope).
     */
    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.Submit -> submit(action)
            is GameAction.NewGame -> Unit // server creates rooms; the lobby flow handles this, not the table.
            GameAction.PlayBestMove -> Unit // no online coach to resolve a best move.
            is GameAction.SendChat, GameAction.MarkChatRead -> Unit // DARBAR is an offline narrative feature.
            GameAction.ContinueBeat -> Unit // no beat gate wired online yet (Track 6 adds the server ack-timeout).
        }
    }

    /**
     * Maps a human [GameAction.Submit] back onto the wire and sends it. Guards on the CURRENTLY-SHOWN
     * legal set (the same stale/double-tap guard the offline ViewModel applies) so a chip tapped after
     * the turn already advanced is silently dropped rather than rejected by the server.
     *
     * Routes a plain reaction-window [com.kursi.engine.Intent.Pass] through the client's fast-path
     * [OnlineGaddiClient.pass]; everything else through [OnlineGaddiClient.submit] with the wire intent.
     */
    private fun submit(action: GameAction.Submit) {
        val shown = _state.value ?: return
        if (action.intent !in shown.legalIntents) return
        val wire = action.intent.toWire()
        scope.launch {
            if (wire is com.kursi.protocol.wire.WireIntent.Pass) {
                client.pass()
            } else {
                client.submit(wire)
            }
        }
    }

    /** Closes the connection permanently and stops projecting. */
    fun close() {
        collectJob?.cancel()
        collectJob = null
        client.close()
    }

    // ─────────────────────────── projection ───────────────────────────

    /**
     * Fold one [OnlineUiState] into [state] + [status]. The single inbound projection point — mirrors
     * how [OnlineGaddiClient.onServerMessage] is the single fold for the wire layer.
     */
    private fun project(online: OnlineUiState) {
        _status.value = online.toConnectionStatus()
        // No StateUpdate yet (still connecting / waiting) ⇒ keep whatever the screen last showed, if any,
        // so a brief blip doesn't blank the table; the status banner conveys the live state.
        online.toGameUiStateOrNull()?.let { _state.value = it }
    }
}
