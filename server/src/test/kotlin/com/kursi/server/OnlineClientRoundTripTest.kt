package com.kursi.server

import com.kursi.core.network.ConnectionState
import com.kursi.core.network.GaddiClient
import com.kursi.core.network.OnlineGaddiClient
import com.kursi.protocol.wire.WireAction
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WirePhaseView
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * END-TO-END round-trip tests: the REAL [OnlineGaddiClient] / [GaddiClient] from :core:network plays
 * through a REAL embedded Netty :server over a real loopback TCP socket (not the in-memory test
 * engine). This is the integration proof that the client API, the wire protocol, and the server's
 * authoritative redaction all line up.
 *
 * Each test owns its own server + client scope (created in the body, torn down in a finally) so there
 * is no cross-test lifecycle coupling.
 */
class OnlineClientRoundTripTest {
    /** Spin up a real ephemeral-port Netty server running the production [module], plus an HTTP client. */
    private class Harness {
        val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
            embeddedServer(Netty, port = 0) { module() }
        val port: Int
        val http: HttpClient = HttpClient(OkHttp)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            server.start(wait = false)
            port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                }
        }

        suspend fun createRoom(playerCount: Int): String = http.post("http://localhost:$port/rooms/$playerCount").bodyAsText()

        fun shutdown() {
            scope.cancel()
            http.close()
            server.stop(100, 500)
        }
    }

    // ── Test 1: a client connects, receives a redacted view, and the UI-state flow is populated ──

    @Test
    fun `online client connects and receives its own redacted view`() =
        runBlocking {
            val h = Harness()
            val roomCode = h.createRoom(2)
            val clientA = OnlineGaddiClient(h.scope)
            val clientB = OnlineGaddiClient(h.scope)
            try {
                clientA.connect(host = "localhost", port = h.port, roomCode = roomCode)
                val joinedA =
                    withTimeout(15_000) {
                        clientA.uiState.first { it.connection is ConnectionState.Connected }
                    }
                assertNotNull(joinedA.mySeat, "client A must be assigned a seat")

                clientB.connect(host = "localhost", port = h.port, roomCode = roomCode)

                val withView = withTimeout(15_000) { clientA.uiState.first { it.view != null } }
                val view = assertNotNull(withView.view)
                assertEquals(joinedA.mySeat, view.viewer, "view must be projected for A's seat")
                assertTrue(view.myInfluence.isNotEmpty(), "A must see its own secret hand")
                assertTrue(view.players.any { it.id != view.viewer }, "A must see the opponent's public face")
            } finally {
                clientA.close()
                clientB.close()
                h.shutdown()
            }
        }

    // ── Test 2: a client PLAYS via the client API and the server advances the authoritative state ──

    @Test
    fun `online client submits an intent and the server advances state`() =
        runBlocking {
            val h = Harness()
            val roomCode = h.createRoom(2)
            val clientA = OnlineGaddiClient(h.scope)
            val clientB = OnlineGaddiClient(h.scope)
            try {
                clientA.connect("localhost", h.port, roomCode)
                clientB.connect("localhost", h.port, roomCode)

                // Wait until the acting client is on a Turn and it is its human turn.
                val started =
                    withTimeout(15_000) {
                        clientA.uiState.first { it.view?.phase is WirePhaseView.Turn }
                    }
                val actorClient =
                    if (started.isHumanTurn) {
                        clientA
                    } else {
                        withTimeout(15_000) { clientB.uiState.first { it.isHumanTurn } }
                        clientB
                    }
                val actorState = actorClient.uiState.value
                val turnBefore = actorState.view!!.turnNumber

                // Use the client-derived legal intents — proves WirePlayerView.legalIntents() works e2e.
                val income =
                    actorState.legalIntents.first {
                        it is WireIntent.DeclareAction && it.action == WireAction.Income
                    }
                actorClient.submit(income)

                val advanced =
                    withTimeout(15_000) {
                        actorClient.uiState.first {
                            (it.view?.turnNumber ?: 0) > turnBefore ||
                                (it.view?.phase != null && it.view?.phase !is WirePhaseView.Turn) ||
                                it.isGameOver
                        }
                    }
                assertTrue(advanced.view != null, "the actor must observe the state advance after its Income")
            } finally {
                clientA.close()
                clientB.close()
                h.shutdown()
            }
        }

    // ── Test 3: a dropped client AUTO-RECONNECTS, resuming the same seat with current state ──

    @Test
    fun `dropped client auto-reconnects and resumes its seat`() =
        runBlocking {
            val h = Harness()
            val roomCode = h.createRoom(2)

            // A factory that records the latest transport so the test can KILL it to simulate a drop;
            // the NEXT factory call hands the reconnect loop a fresh, working client.
            val transports = mutableListOf<GaddiClient>()
            val factory: () -> GaddiClient = { GaddiClient().also { synchronized(transports) { transports.add(it) } } }
            val clientA =
                OnlineGaddiClient(
                    scope = h.scope,
                    config = OnlineGaddiClient.ReconnectConfig(maxAttempts = 8, initialBackoffMs = 100),
                    transportFactory = factory,
                )
            val clientB = OnlineGaddiClient(h.scope)
            try {
                clientA.connect("localhost", h.port, roomCode)
                val joinedA =
                    withTimeout(15_000) {
                        clientA.uiState.first { it.connection is ConnectionState.Connected }
                    }
                val seatA = joinedA.mySeat!!
                clientB.connect("localhost", h.port, roomCode)

                // Let the match start so A holds real influence on its seat.
                withTimeout(15_000) { clientA.uiState.first { it.view != null } }

                // DROP A's socket by closing its CURRENT transport → the incoming flow throws/completes and
                // the lifecycle loop goes Dropped → Reconnecting → Connected with a fresh transport.
                synchronized(transports) { transports.last() }.close()

                val resumed =
                    withTimeout(25_000) {
                        clientA.uiState.first {
                            it.connection is ConnectionState.Connected && it.view != null && it.mySeat == seatA
                        }
                    }
                assertEquals(seatA, resumed.mySeat, "auto-reconnect must resume the same seat")
                assertEquals(seatA, resumed.view!!.viewer, "resync view must be for the resumed seat")
            } finally {
                clientA.close()
                clientB.close()
                h.shutdown()
            }
        }
}
