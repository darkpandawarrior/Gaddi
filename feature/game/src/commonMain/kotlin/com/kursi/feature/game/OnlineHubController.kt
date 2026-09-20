package com.kursi.feature.game

import com.kursi.core.network.ConnectionState
import com.kursi.core.network.OnlineKursiClient
import com.kursi.core.network.RoomApi
import com.kursi.core.network.RoomResult
import com.kursi.gameservices.RoomCode
import com.siddharth.kmp.network.LanDiscoverer
import com.siddharth.kmp.network.LanHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kursi's DNS-SD / beacon service type — the `serviceType` :network's LAN discovery is parameterized on. */
private const val KURSI_LAN_SERVICE_TYPE = "_kursi._tcp"

/**
 * ONLINE-HUB CONTROLLER — the pre-match orchestration the [com.kursi.feature.game] module owns so the
 * UI layer (`:cmp-shared`'s OnlineHubScreen) stays a pure renderer of [OnlineHubUiState].
 *
 * It turns a player's intent — host a PRIVATE room, JOIN by code, find a public QUICK-MATCH, or BROWSE
 * the LAN — into a live waiting-room and, once seats fill, an authoritative [OnlineGameAdapter] driving
 * the existing in-game table (the Bridge). Three collaborators, each from `:core:network`:
 *
 *  - [RoomApi]        — the REST handshake (`POST /rooms/{n}` / `POST /quickmatch/{n}`) that mints a code.
 *  - [LanDiscoverer]  — the LAN browse flow (mDNS/UDP-beacon) yielding [LanHost]s to join directly.
 *  - [OnlineKursiClient] — the WebSocket connection + lobby/seat lifecycle once a code is in hand.
 *
 * THE LOBBY: after a code is obtained (or a LAN host picked), [connect] opens the socket and projects
 * the connection lifecycle into [OnlineHubUiState.lobby]. While [ConnectionState.Connecting] /
 * Connected-without-a-view, the player sits in the waiting room (who has joined, the share code). The
 * moment the server sends the first `StateUpdate` (room full → match started), [OnlineHubUiState.started]
 * flips true and the UI routes into the online table built on [adapter].
 *
 * Threading: all work runs in [scope] (a ViewModel scope in the app, a test scope in tests). Every
 * exposed field is a hot [StateFlow] safe to `collectAsStateWithLifecycle()`. Call [close] to tear down.
 *
 * @param scope          coroutine scope owning every job here and the underlying client.
 * @param clientFactory  builds the [OnlineKursiClient] (defaults to one bound to [scope]).
 * @param roomApiFactory builds a [RoomApi] for a (host, port) — overridable in tests.
 * @param lanDiscoverer  the LAN browser (defaults to the platform actual).
 */
class OnlineHubController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clientFactory: (CoroutineScope) -> OnlineKursiClient = { OnlineKursiClient(scope = it) },
    private val roomApiFactory: (host: String, port: Int) -> RoomApi = { host, port -> RoomApi(host, port) },
    private val lanDiscovererFactory: () -> LanDiscoverer = { LanDiscoverer(KURSI_LAN_SERVICE_TYPE) },
) {
    private val _uiState = MutableStateFlow(OnlineHubUiState())

    /** The single source of truth for the OnlineHub UI. */
    val uiState: StateFlow<OnlineHubUiState> = _uiState.asStateFlow()

    // The live online client + bridge adapter, created when a connection opens. The adapter is what the
    // online table renders; it is exposed so the app's NavHost can hand it to the GameScreen.
    private var client: OnlineKursiClient? = null
    private var _adapter: OnlineGameAdapter? = null

    /** The bridge adapter driving the in-game table once [connect] has opened a match. Null before then. */
    val adapter: OnlineGameAdapter? get() = _adapter

    private var lobbyJob: Job? = null
    private var lanJob: Job? = null

    // ─────────────────────────── REST: create / quickmatch ───────────────────────────

    /** Host a PRIVATE room of [playerCount] seats: mints a share-code, then opens the lobby on it. */
    fun createPrivateRoom(
        host: String,
        port: Int,
        playerCount: Int,
    ) {
        _uiState.update { it.copy(phase = HubPhase.Working, error = null) }
        scope.launch {
            roomApiFactory(host, port).use { api ->
                when (val r = api.createPrivateRoom(playerCount)) {
                    is RoomResult.Success -> openLobby(host, port, r.code, playerCount, LobbyKind.PrivateHost)
                    is RoomResult.Rejected -> fail(r.reason)
                    is RoomResult.Unreachable -> fail(unreachableMsg(host, port, r.cause))
                }
            }
        }
    }

    /** Find a public QUICK-MATCH of [playerCount] seats: gets a waiting room's code, then opens the lobby. */
    fun quickMatch(
        host: String,
        port: Int,
        playerCount: Int,
    ) {
        _uiState.update { it.copy(phase = HubPhase.Working, error = null) }
        scope.launch {
            roomApiFactory(host, port).use { api ->
                when (val r = api.quickMatch(playerCount)) {
                    is RoomResult.Success -> openLobby(host, port, r.code, playerCount, LobbyKind.QuickMatch)
                    is RoomResult.Rejected -> fail(r.reason)
                    is RoomResult.Unreachable -> fail(unreachableMsg(host, port, r.cause))
                }
            }
        }
    }

    /** JOIN a private room by its shared [code] — no REST call, straight to the lobby on that code. */
    fun joinByCode(
        host: String,
        port: Int,
        code: String,
        playerCount: Int = 0,
    ) {
        // Boundary parse. The same room reaches us as the server's bare "ABC234", as the dashed
        // "ABC-234" that iOS 26's GKGameActivity party code insists on, or as either of those
        // retyped by hand with a stray space. RoomCode collapses all of them to the one form
        // RoomRegistry.findRoom() looks up — nothing dashed ever reaches the wire, and the server
        // keeps minting exactly what it always minted.
        val trimmed = RoomCode.fromPartyCode(code)
        if (trimmed.isEmpty()) {
            fail("Enter a room code")
            return
        }
        _uiState.update { it.copy(phase = HubPhase.Working, error = null) }
        openLobby(host, port, trimmed, playerCount, LobbyKind.JoinByCode)
    }

    /** JOIN a LAN-discovered [host] directly — its [LanHost.payload] carries the room code to join. */
    fun joinLanHost(lanHost: LanHost) {
        _uiState.update { it.copy(phase = HubPhase.Working, error = null) }
        openLobby(lanHost.host, lanHost.port, lanHost.payload, playerCount = 0, LobbyKind.LanJoin)
    }

    // ─────────────────────────── LAN browse ───────────────────────────

    /** Start LAN discovery: streams discovered [LanHost]s into [OnlineHubUiState.lanHosts]. */
    fun startLanBrowse() {
        if (lanJob?.isActive == true) return
        _uiState.update { it.copy(lanBrowsing = true, lanHosts = emptyList()) }
        val discoverer = lanDiscovererFactory()
        lanJob =
            scope.launch {
                discoverer.discover().collect { found ->
                    _uiState.update { st ->
                        if (st.lanHosts.any { it.host == found.host && it.port == found.port && it.payload == found.payload }) {
                            st
                        } else {
                            st.copy(lanHosts = st.lanHosts + found)
                        }
                    }
                }
            }
    }

    /** Stop LAN discovery (e.g. leaving the LAN tab). */
    fun stopLanBrowse() {
        lanJob?.cancel()
        lanJob = null
        _uiState.update { it.copy(lanBrowsing = false) }
    }

    // ─────────────────────────── Lobby lifecycle ───────────────────────────

    /**
     * Open the WebSocket lobby on [code]: build the client + bridge adapter, connect, and project the
     * connection lifecycle into [OnlineHubUiState.lobby]. Flips [OnlineHubUiState.started] once the
     * server's first StateUpdate arrives (the match has begun).
     */
    private fun openLobby(
        host: String,
        port: Int,
        code: String,
        playerCount: Int,
        kind: LobbyKind,
    ) {
        lobbyJob?.cancel()
        client?.close()

        val c = clientFactory(scope)
        client = c
        _adapter = OnlineGameAdapter(client = c, scope = scope)

        _uiState.update {
            it.copy(
                phase = HubPhase.Lobby,
                error = null,
                lobby =
                    LobbyState(
                        host = host,
                        port = port,
                        code = code,
                        kind = kind,
                        seatCount = playerCount,
                    ),
                started = false,
            )
        }

        // Drive the connection through the bridge adapter so the SAME client backs both the lobby and
        // the table — no reconnect on hand-off.
        _adapter?.connect(host = host, port = port, roomCode = code)

        lobbyJob =
            scope.launch {
                c.uiState.collect { online ->
                    val started = online.view != null && !online.isGameOver
                    _uiState.update { st ->
                        val lob = st.lobby ?: return@update st
                        st.copy(
                            lobby =
                                lob.copy(
                                    connection = online.connection,
                                    mySeat = online.mySeat,
                                    joinedSeats = seatsKnown(online),
                                ),
                            started = st.started || started,
                        )
                    }
                }
            }
    }

    /** Best-effort count of seats the server has confirmed (own seat + any rendered in the first view). */
    private fun seatsKnown(online: com.kursi.core.network.OnlineUiState): Int {
        val viewSeats = online.view?.players?.size ?: 0
        val ownSeat = if (online.mySeat != null) 1 else 0
        return maxOf(viewSeats, ownSeat)
    }

    /** Leave the lobby / abandon the connection but stay in the hub (back to the mode picker). */
    fun leaveLobby() {
        lobbyJob?.cancel()
        lobbyJob = null
        client?.close()
        client = null
        _adapter?.close()
        _adapter = null
        _uiState.update { it.copy(phase = HubPhase.Idle, lobby = null, started = false, error = null) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(phase = HubPhase.Idle, error = message) }
    }

    private fun unreachableMsg(
        host: String,
        port: Int,
        cause: String?,
    ): String = "Daftar band hai — $host:$port se rabta nahi hua${cause?.let { " ($it)" } ?: ""}."

    /** Tear everything down: lobby, LAN browse, client, adapter. */
    fun close() {
        lobbyJob?.cancel()
        lanJob?.cancel()
        client?.close()
        _adapter?.close()
        lobbyJob = null
        lanJob = null
        client = null
        _adapter = null
    }
}

/** Which path opened the current lobby — drives the waiting-room copy (host vs join vs LAN). */
enum class LobbyKind { PrivateHost, JoinByCode, QuickMatch, LanJoin }

/** The hub's top-level phase. */
enum class HubPhase {
    /** At the mode picker (create / join / quick-match / LAN). */
    Idle,

    /** A REST call is in flight (creating a room / finding a quick-match). */
    Working,

    /** In a waiting room on a live socket; flips to the table once [OnlineHubUiState.started]. */
    Lobby,
}

/**
 * The full UI state of the OnlineHub, mirroring the single-immutable-state convention the rest of the
 * app uses. The screen renders straight off this; the controller is the only writer.
 */
data class OnlineHubUiState(
    val phase: HubPhase = HubPhase.Idle,
    /** A human-legible, in-identity error to surface as a banner (server down, bad code), or null. */
    val error: String? = null,
    /** The live waiting-room state once a connection has opened, or null at the mode picker. */
    val lobby: LobbyState? = null,
    /** True once the server has started the match (first StateUpdate) — the UI routes into the table. */
    val started: Boolean = false,
    /** Whether LAN discovery is actively browsing. */
    val lanBrowsing: Boolean = false,
    /** LAN-discovered hosts, de-duplicated, in discovery order. */
    val lanHosts: List<LanHost> = emptyList(),
)

/** The live waiting-room state for one opened connection. */
data class LobbyState(
    val host: String,
    val port: Int,
    /** The room code — shared for a private host, the matched code for quick-match. */
    val code: String,
    val kind: LobbyKind,
    /**
     * The same room code in the two-equal-halves dashed form `GKGameActivity` requires
     * (`ABC-234`), or null if it cannot be split evenly. Derived, never sent: [code] is what goes
     * on the wire and what a player reads aloud.
     */
    val partyCode: String? get() = RoomCode.toPartyCode(code)

    /** The requested seat count (0 when joining by code / LAN, where the host decided it). */
    val seatCount: Int,
    /** The live connection lifecycle from the underlying client. */
    val connection: ConnectionState = ConnectionState.Connecting,
    /** This client's assigned seat once the server replies, or null before. */
    val mySeat: Int? = null,
    /** Best-effort count of players the server has confirmed in the room so far. */
    val joinedSeats: Int = 0,
) {
    /** A short status the waiting room renders ("Connecting…", "Waiting for players…", "Lost"). */
    val isConnecting: Boolean get() = connection is ConnectionState.Connecting || connection is ConnectionState.Idle
    val isWaiting: Boolean get() = connection is ConnectionState.Connected
    val isLost: Boolean get() =
        connection is ConnectionState.Dropped ||
            (connection is ConnectionState.Closed)
}
