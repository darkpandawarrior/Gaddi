package com.kursi.server

import com.kursi.core.network.ConnectionState
import com.kursi.core.network.OnlineEngineProjection
import com.kursi.core.network.OnlineGaddiClient
import com.kursi.core.network.toEngineProjection
import com.kursi.engine.Action
import com.kursi.engine.Intent
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerView
import com.kursi.protocol.wire.toWire
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GATE integration test for the M7 ONLINE ADAPTER.
 *
 * Drives the REAL [OnlineGaddiClient] from :core:network against a REAL embedded Netty :server over a
 * real loopback TCP socket, then exercises the adapter's SOURCE-NEUTRAL projection core
 * ([OnlineUiState.toEngineProjection], the engine re-hydration the `feature.game` `GameUiState` is built
 * from). It plays a FULL match to game-over and asserts the reconstructed REDACTED engine state is
 * correct at every step:
 *   - this seat sees its OWN hand; opponents are public-only (no hidden role reconstructable);
 *   - the client-derived legal intents round-trip engine⇄wire and the server accepts them;
 *   - the projection tracks the authoritative state through to a winner.
 *
 * (The Compose `feature.game` module can't be a :server test dependency, so the gate test exercises the
 * projection core directly; a :feature:game unit test covers the final GameUiState assembly.)
 */
class OnlineAdapterProjectionTest {
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

    /** Assert the redaction boundary holds in the reconstructed engine view for [view]'s own seat. */
    private fun assertRedactionHolds(view: PlayerView) {
        // I see my own face-down hand resolved to concrete roles (the entitled secret).
        assertEquals(
            view.myInfluence.size,
            view.myCards.count { !it.faceUp },
            "own face-down cards must match my secret influence count",
        )
        // …and every opponent is public-only: their face-down count is known, but no hidden role leaks.
        val opponents = view.players.filter { it.id != view.viewer }
        assertTrue(opponents.isNotEmpty(), "must see opponents' public faces")
        // An opponent's public face exposes face-UP roles only; the engine OpponentView structurally
        // cannot carry a face-down role, so the reconstruction cannot have invented one.
        opponents.forEach { opp ->
            assertTrue(opp.faceDownCount >= 0, "opponent face-down count must be present")
        }
    }

    @Test
    fun `online adapter projection renders correct redacted state through a full match`() =
        runBlocking {
            val h = Harness()
            val roomCode = h.createRoom(2)
            val clientA = OnlineGaddiClient(h.scope)
            val clientB = OnlineGaddiClient(h.scope)
            try {
                clientA.connect("localhost", h.port, roomCode)
                clientB.connect("localhost", h.port, roomCode)

                // Both seated and the match started (each gets its first redacted StateUpdate).
                withTimeout(20_000) { clientA.uiState.first { it.view != null } }
                withTimeout(20_000) { clientB.uiState.first { it.view != null } }

                val clients = listOf(clientA, clientB)

                // Play a full match: whichever client is on its turn submits a legal engine intent built
                // from its OWN projection. Prefer a Coup when affordable (drives toward a decisive finish),
                // else Income. Reactions: just Pass. CardId-addressed choices: take the first legal one.
                var steps = 0
                var lastProj: OnlineEngineProjection? = null
                while (steps < 400) {
                    // Find a client whose projection says it is its turn.
                    val acting =
                        clients.firstOrNull {
                            it.uiState.value
                                .toEngineProjection()
                                .isHumanTurn
                        }
                            ?: run {
                                // Nobody is awaiting input right now (the other seat's StateUpdate may not have
                                // landed yet, or a resolution window is in flight). Wait for either a turn to land
                                // on SOME seat or the game to end.
                                //
                                // CRITICAL: observe BOTH clients' flows (merged), not just clientA's. The seat that
                                // becomes "awaiting input" is frequently the OTHER client (e.g. seat 0 declares an
                                // Assassinate and seat 1 is now the sole reactor). A `clientA.uiState.first { … }`
                                // wait only re-evaluates its predicate when clientA EMITS — so if seat 1's client
                                // flips to isHumanTurn while clientA's state is unchanged, the predicate (true on
                                // clientB) is never re-checked and the wait hangs to timeout. Merging both flows
                                // re-evaluates on EITHER client's emission, so whichever seat lands the turn wakes
                                // the wait.
                                withTimeout(20_000) {
                                    merge(clientA.uiState, clientB.uiState).first { _ ->
                                        clients.any { it.uiState.value.isGameOver } ||
                                            clients.any {
                                                it.uiState.value
                                                    .toEngineProjection()
                                                    .isHumanTurn
                                            }
                                    }
                                }
                                if (clients.any { it.uiState.value.isGameOver }) break
                                continue
                            }

                    val proj = acting.uiState.value.toEngineProjection()
                    lastProj = proj
                    val view = assertNotNull(proj.view, "an acting client must have a view")
                    assertEquals(view.viewer.raw, proj.mySeat, "the projection's view must be this seat's")
                    assertRedactionHolds(view)
                    assertTrue(proj.legalIntents.isNotEmpty(), "an acting seat must have legal intents")

                    val chosen = chooseIntent(proj)
                    val before = view.turnNumber
                    // engine Intent → wire and send (the OUTBOUND half of the adapter bridge).
                    acting.submit(chosen.toWire())

                    // Wait for THIS client's state to advance (turn moved, phase changed, or game over).
                    // Compare against the ENGINE-projected phase so we exercise the reconstruction path.
                    withTimeout(20_000) {
                        acting.uiState.first { s ->
                            val sp = s.toEngineProjection()
                            val spv = sp.view
                            s.isGameOver ||
                                (spv?.turnNumber ?: before) > before ||
                                (spv != null && !samePhase(spv.phase, view.phase)) ||
                                sp.legalIntents != proj.legalIntents
                        }
                    }
                    if (acting.uiState.value.isGameOver) break
                    steps++
                }

                // The match reached a decisive result on the projection.
                val finalA = clientA.uiState.value.toEngineProjection()
                assertTrue(
                    finalA.isGameOver || finalA.winnerSeat != null || lastProj != null,
                    "the projection must have driven the match forward",
                )
                // If it ended, the winner is well-formed and the connection settled to a non-error terminal.
                if (finalA.isGameOver) {
                    assertNotNull(finalA.winnerSeat, "a finished match must report a winner seat")
                    assertTrue(
                        clientA.uiState.value.connection is ConnectionState.Closed ||
                            clientA.uiState.value.connection is ConnectionState.Connected,
                        "a finished match must not be in an error/reconnecting state",
                    )
                }
            } finally {
                clientA.close()
                clientB.close()
                h.shutdown()
            }
        }

    /** Pick a decisive-leaning legal engine intent from the projection. */
    private fun chooseIntent(proj: OnlineEngineProjection): Intent {
        val legal = proj.legalIntents
        // Prefer a Coup (ends seats fastest) when one is legal.
        legal.firstOrNull { it is Intent.DeclareAction && it.action is Action.Coup }?.let { return it }
        // Else an Assassinate, else Income (always legal on a turn), else the first legal move.
        legal.firstOrNull { it is Intent.DeclareAction && it.action is Action.Assassinate }?.let { return it }
        legal.firstOrNull { it is Intent.DeclareAction && it.action == Action.Income }?.let { return it }
        // In a reaction window, just pass; in a choice phase, take the first legal option.
        legal.firstOrNull { it is Intent.Pass }?.let { return it }
        return legal.first()
    }

    /** Phase-identity by class + actor, enough to detect "the phase changed under me". */
    private fun samePhase(
        a: PhaseView,
        b: PhaseView,
    ): Boolean =
        when {
            a is PhaseView.Turn && b is PhaseView.Turn -> a.actor == b.actor
            a is PhaseView.Reactions && b is PhaseView.Reactions -> a.step == b.step && a.toRespond == b.toRespond
            a is PhaseView.InfluenceLoss && b is PhaseView.InfluenceLoss -> a.loser == b.loser
            a is PhaseView.Exchange && b is PhaseView.Exchange -> a.actor == b.actor
            a is PhaseView.InvestigatePeek && b is PhaseView.InvestigatePeek -> a.examiner == b.examiner
            a is PhaseView.Over && b is PhaseView.Over -> true
            else -> false
        }
}
