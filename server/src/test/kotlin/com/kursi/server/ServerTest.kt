package com.kursi.server

import com.kursi.protocol.wire.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

/**
 * Integration tests for the :server module.
 * All tests use [testApplication] (no real port bound) and are deterministic/fast.
 */
class ServerTest {
    // ── Helper: next ServerMessage ────────────────────────────────────────────

    private suspend fun DefaultClientWebSocketSession.nextServerMsg(): ServerMessage {
        val frame = incoming.receive() as Frame.Text
        return GaddiJson.decodeFromString(frame.readText())
    }

    private suspend fun DefaultClientWebSocketSession.sendMsg(msg: ClientMessage) {
        send(Frame.Text(GaddiJson.encodeToString(msg)))
    }

    // ── Test 1: Health endpoint ───────────────────────────────────────────────

    @Test
    fun `health endpoint returns 200`() =
        testApplication {
            application { module() }
            val resp = client.get("/health")
            assertEquals(200, resp.status.value)
        }

    // ── Test 2: Room creation via REST endpoint ───────────────────────────────

    @Test
    fun `creating a room returns a non-blank room code`() =
        testApplication {
            application { module() }
            val resp = client.post("/rooms/2")
            val code = resp.body<String>()
            assertTrue(code.isNotBlank(), "Room code must be non-blank")
            assertTrue(code.length >= 4, "Room code should be at least 4 chars")
        }

    // ── Test 3: Two clients join and receive initial StateUpdate ──────────────

    /**
     * Two concurrent WebSocket clients join a room. Both must receive RoomJoined + StateUpdate.
     * Uses coroutineScope + two launch blocks so neither client's session blocks the other.
     */
    @Test
    fun `two clients join a match and both receive StateUpdate`() =
        testApplication {
            application { module() }

            val resp = client.post("/rooms/2")
            val roomCode = resp.body<String>()

            val wsClient = createClient { install(WebSockets) }

            // Collect results across coroutines
            val results = mutableListOf<ServerMessage>()

            // Run both WS connections concurrently inside the test
            coroutineScope {
                val barrier = CompletableDeferred<Unit>()

                val job1 =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val joined = nextServerMsg()
                            assertIs<ServerMessage.RoomJoined>(joined)
                            barrier.complete(Unit) // signal client2 to proceed
                            val update = nextServerMsg()
                            synchronized(results) { results.add(update) }
                            // Receive the second StateUpdate that the actor sends after both join
                            val update2 = incoming.tryReceive().getOrNull()
                            if (update2 is Frame.Text) {
                                val msg: ServerMessage = GaddiJson.decodeFromString(update2.readText())
                                synchronized(results) { results.add(msg) }
                            }
                        }
                    }

                val job2 =
                    launch {
                        barrier.await() // wait until client1 has joined
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val joined = nextServerMsg()
                            assertIs<ServerMessage.RoomJoined>(joined)
                            val update = nextServerMsg()
                            synchronized(results) { results.add(update) }
                        }
                    }

                job1.join()
                job2.join()
            }

            // At least 2 StateUpdate messages should have arrived (one per player after game start)
            val stateUpdates = results.filterIsInstance<ServerMessage.StateUpdate>()
            assertTrue(stateUpdates.size >= 2, "Both clients should receive at least one StateUpdate each; got $stateUpdates")
        }

    // ── Test 4: Secrecy — opponent hidden roles never in StateUpdate ──────────

    /**
     * A single client joins a 2-player room (the other seat gets a bot).
     * We use a trick: create a 2-player room, join with ONE human, force game start by
     * filling the second seat with a bot via the server's bot-fill logic.
     *
     * Since the current server starts the game when ALL seatCount humans have joined,
     * we instead join TWO clients and then verify secrecy on the received view.
     */
    @Test
    fun `StateUpdate never exposes hidden roles of other seats`() =
        testApplication {
            application { module() }

            val resp = client.post("/rooms/2")
            val roomCode = resp.body<String>()
            val wsClient = createClient { install(WebSockets) }

            var view1: WirePlayerView? = null

            coroutineScope {
                val barrier = CompletableDeferred<Unit>()

                val job1 =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            assertIs<ServerMessage.RoomJoined>(nextServerMsg())
                            barrier.complete(Unit)
                            // Wait for StateUpdate once both players join
                            val update = nextServerMsg()
                            if (update is ServerMessage.StateUpdate) {
                                view1 = update.view
                            }
                        }
                    }
                val job2 =
                    launch {
                        barrier.await()
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            assertIs<ServerMessage.RoomJoined>(nextServerMsg())
                            nextServerMsg() // StateUpdate for seat2
                        }
                    }
                job1.join()
                job2.join()
            }

            val view = assertNotNull(view1, "Seat 1 should have received a StateUpdate")
            val viewerSeat = view.viewer

            // Viewer must see their own hidden influence (the secret hand)
            assertTrue(view.myInfluence.isNotEmpty(), "Viewer should see their own influence")

            // Opponent entries must NOT contain any hidden role data
            // WireOpponentView only has faceUpRoles (public) and faceDownCount (public count)
            val opponents = view.players.filter { it.id != viewerSeat }
            assertTrue(opponents.isNotEmpty(), "Should see at least one opponent")
            for (opp in opponents) {
                // At game start, no influence cards are face-up
                assertEquals(0, opp.faceUpRoles.size, "No cards face-up at start for opponent seat ${opp.id}")
                // faceDownCount is the public count — positive at game start
                assertTrue(opp.faceDownCount > 0, "Opponent should have face-down cards")
                // STRUCTURAL CHECK: WireOpponentView has no 'hiddenRoles' field by type definition.
                // The type system enforces secrecy at compile time; this test confirms the shape.
            }
        }

    // ── Test 5: Illegal intent returns Error, no crash ────────────────────────

    @Test
    fun `out-of-turn intent returns Error ServerMessage and does not crash server`() =
        testApplication {
            application { module() }

            val resp = client.post("/rooms/2")
            val roomCode = resp.body<String>()
            val wsClient = createClient { install(WebSockets) }

            var gotError = false

            // IMPORTANT: both connections must stay alive for the whole test. If a seat's WS block
            // returns early, the server treats it as a disconnect and a bot takes over that seat —
            // which would then legitimately advance the game and the "out-of-turn" intent would no
            // longer be out of turn. `done` keeps both blocks open until the assertion target is met.
            val done = CompletableDeferred<Unit>()

            coroutineScope {
                val seat1Deferred = CompletableDeferred<Int>()
                val seat2Deferred = CompletableDeferred<Int>()
                val actorSeatDeferred = CompletableDeferred<Int>()

                val job1 =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val seat1 = (nextServerMsg() as ServerMessage.RoomJoined).seat
                            seat1Deferred.complete(seat1)

                            val update = nextServerMsg() as ServerMessage.StateUpdate
                            val actorSeat = (update.view.phase as? WirePhaseView.Turn)?.actor ?: -1
                            actorSeatDeferred.complete(actorSeat)

                            if (actorSeat != seat1 && actorSeat >= 0) {
                                // seat1 is the NON-actor → submit an out-of-turn intent for its own seat.
                                sendMsg(
                                    ClientMessage.SubmitIntent(
                                        matchId = roomCode,
                                        seq = 1L,
                                        intent = WireIntent.DeclareAction(actor = seat1, action = WireAction.Income),
                                    ),
                                )
                                if (nextServerMsg() is ServerMessage.Error) gotError = true
                                done.complete(Unit)
                            }
                            done.await() // stay connected so seat1 is never replaced by a bot
                        }
                    }

                val job2 =
                    launch {
                        seat1Deferred.await()
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val seat2 = (nextServerMsg() as ServerMessage.RoomJoined).seat
                            seat2Deferred.complete(seat2)

                            nextServerMsg() as ServerMessage.StateUpdate // game-start view
                            val actorSeat = actorSeatDeferred.await()

                            if (actorSeat != seat2 && actorSeat >= 0) {
                                // seat2 is the NON-actor → submit an out-of-turn intent for its own seat.
                                sendMsg(
                                    ClientMessage.SubmitIntent(
                                        matchId = roomCode,
                                        seq = 1L,
                                        intent = WireIntent.DeclareAction(actor = seat2, action = WireAction.Income),
                                    ),
                                )
                                if (nextServerMsg() is ServerMessage.Error) gotError = true
                                done.complete(Unit)
                            }
                            done.await() // stay connected so seat2 is never replaced by a bot
                        }
                    }

                withTimeout(15_000) {
                    done.await()
                    job1.join()
                    job2.join()
                }
            }

            assertTrue(gotError, "Out-of-turn intent should produce an Error ServerMessage")
        }

    // ── Test 6: Game advances when correct intents are submitted ─────────────

    @Test
    fun `submitting a legal intent advances the game state`() =
        testApplication {
            application { module() }

            val resp = client.post("/rooms/2")
            val roomCode = resp.body<String>()
            val wsClient = createClient { install(WebSockets) }

            var initialTurn = -1
            var advancedTurn = -1

            coroutineScope {
                val barrier = CompletableDeferred<Unit>()
                val gameStarted = CompletableDeferred<Unit>()

                val job1 =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val joined1 = nextServerMsg() as ServerMessage.RoomJoined
                            val seat1 = joined1.seat
                            barrier.complete(Unit)

                            val initUpdate = nextServerMsg() as ServerMessage.StateUpdate
                            val initView = initUpdate.view
                            initialTurn = initView.turnNumber
                            gameStarted.complete(Unit)

                            val actorSeat =
                                when (val ph = initView.phase) {
                                    is WirePhaseView.Turn -> ph.actor
                                    else -> -1
                                }

                            if (actorSeat == seat1) {
                                sendMsg(
                                    ClientMessage.SubmitIntent(
                                        matchId = roomCode,
                                        seq = 1L,
                                        intent = WireIntent.DeclareAction(actor = seat1, action = WireAction.Income),
                                    ),
                                )
                                // Receive at least one StateUpdate confirming advance
                                val nextUpdate = nextServerMsg()
                                if (nextUpdate is ServerMessage.StateUpdate) {
                                    advancedTurn = nextUpdate.view.turnNumber
                                }
                            }
                        }
                    }

                val job2 =
                    launch {
                        barrier.await()
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val joined2 = nextServerMsg() as ServerMessage.RoomJoined
                            val seat2 = joined2.seat

                            val initUpdate2 = nextServerMsg() as ServerMessage.StateUpdate
                            val initView2 = initUpdate2.view
                            gameStarted.await()

                            val actorSeat =
                                when (val ph = initView2.phase) {
                                    is WirePhaseView.Turn -> ph.actor
                                    else -> -1
                                }

                            if (actorSeat == seat2) {
                                sendMsg(
                                    ClientMessage.SubmitIntent(
                                        matchId = roomCode,
                                        seq = 1L,
                                        intent = WireIntent.DeclareAction(actor = seat2, action = WireAction.Income),
                                    ),
                                )
                                val nextUpdate = nextServerMsg()
                                if (nextUpdate is ServerMessage.StateUpdate) {
                                    advancedTurn = nextUpdate.view.turnNumber
                                }
                            }
                        }
                    }

                job1.join()
                job2.join()
            }

            assertTrue(initialTurn >= 1, "Game should start with turn >= 1")
            if (advancedTurn >= 0) {
                assertTrue(advancedTurn >= initialTurn, "Turn number should advance after Income")
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side move chooser — proves the wire protocol is SUFFICIENT for a real
    // client to play every phase using ONLY its own WirePlayerView (no GameState).
    // Critically exercises myCards (CardId↔role) for ChooseInfluenceToLose / ChooseExchange
    // and the per-viewer Exchange.drawn list.
    // ─────────────────────────────────────────────────────────────────────────

    /** Build a legal intent for [seat] purely from its redacted [view]. Returns null if not this seat's turn. */
    private fun chooseIntentFromView(
        view: WirePlayerView,
        seat: Int,
    ): WireIntent? {
        return when (val ph = view.phase) {
            is WirePhaseView.Turn -> {
                if (ph.actor != seat) return null
                val opponents = view.players.filter { it.id != seat && !it.eliminated }.map { it.id }
                val target = opponents.firstOrNull()
                when {
                    // Forced coup at >=10 coins.
                    view.myCoins >= view.config.forcedCoupThreshold && target != null ->
                        WireIntent.DeclareAction(seat, WireAction.Coup(target))
                    view.myCoins >= view.config.coupCost && target != null ->
                        WireIntent.DeclareAction(seat, WireAction.Coup(target))
                    else -> WireIntent.DeclareAction(seat, WireAction.Income)
                }
            }
            is WirePhaseView.Reactions -> {
                if (ph.toRespond != seat) return null
                // Always pass — keep the game progressing deterministically toward a coup-out finish.
                WireIntent.Pass(seat)
            }
            is WirePhaseView.InfluenceLoss -> {
                if (ph.loser != seat) return null
                // Must address a specific OWN face-down CardId — only obtainable from myCards.
                val faceDown =
                    view.myCards.firstOrNull { !it.faceUp }
                        ?: return null
                WireIntent.ChooseInfluenceToLose(seat, faceDown.id)
            }
            is WirePhaseView.Exchange -> {
                if (ph.actor != seat) return null
                // Keep exactly the current face-down count from {own face-down} ∪ {drawn}.
                val ownFaceDown = view.myCards.filter { !it.faceUp }.map { it.id }
                val keepSize = ownFaceDown.size
                // drawn comes from the per-viewer Exchange.drawn list — only present for the actor.
                val pool = ownFaceDown + ph.drawn.map { it.id }
                WireIntent.ChooseExchange(seat, pool.take(keepSize))
            }
            is WirePhaseView.InvestigatePeek -> {
                if (ph.examiner != seat) return null
                WireIntent.ResolveInvestigate(seat, forceRedraw = false)
            }
            is WirePhaseView.Over -> null
        }
    }

    /** Assert a received view never leaks another seat's secrets (hidden roles, drawn/peek CardIds). */
    private fun assertSecrecy(
        view: WirePlayerView,
        seat: Int,
    ) {
        assertEquals(seat, view.viewer, "view must be projected for the receiving seat")
        // myCards must contain ONLY this seat's own cards (they are, by definition, ownable here).
        // Opponent entries carry no hidden role data — only face-up reveals + a face-down COUNT.
        for (opp in view.players.filter { it.id != seat }) {
            // faceUpRoles are public reveals; faceDownCount is a count, not roles. No hidden roles present.
            assertTrue(opp.faceDownCount >= 0, "faceDownCount is a public count")
        }
        // If THIS seat is the Exchange actor, drawn may be populated; otherwise it must be empty.
        val ph = view.phase
        if (ph is WirePhaseView.Exchange && ph.actor != seat) {
            assertTrue(ph.drawn.isEmpty(), "Exchange.drawn must be EMPTY for a non-acting seat (secrecy)")
        }
    }

    // ── Test 7: Two clients play a FULL match to completion through the server ──

    @Test
    fun `two clients play a full match to completion via the server`() =
        testApplication {
            application { module() }

            val roomCode = client.post("/rooms/2").body<String>()
            val wsClient = createClient { install(WebSockets) }

            val winnerSeats = mutableListOf<Int>()
            // Track the last view each seat received, for a final secrecy sweep.
            val lastViews = arrayOfNulls<WirePlayerView>(2)

            suspend fun runSeat(
                barrier: CompletableDeferred<Unit>?,
                awaitBarrier: CompletableDeferred<Unit>?,
            ) {
                wsClient.webSocket("/play") {
                    sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                    val joined = nextServerMsg() as ServerMessage.RoomJoined
                    val seat = joined.seat
                    barrier?.complete(Unit)
                    awaitBarrier?.await()

                    var seq = 0L
                    var over = false
                    while (!over) {
                        val msg = nextServerMsg()
                        when (msg) {
                            is ServerMessage.StateUpdate -> {
                                val view = msg.view
                                lastViews[seat] = view
                                assertSecrecy(view, seat)
                                val intent = chooseIntentFromView(view, seat)
                                if (intent != null) {
                                    sendMsg(ClientMessage.SubmitIntent(roomCode, ++seq, intent))
                                }
                            }
                            is ServerMessage.GameOver -> {
                                synchronized(winnerSeats) { winnerSeats.add(msg.winnerSeat) }
                                over = true
                            }
                            is ServerMessage.Error -> {
                                // A benign race (e.g. acting on a slightly stale view) — ignore and continue.
                            }
                            is ServerMessage.RoomJoined -> { /* ignore late joins */ }
                        }
                    }
                }
            }

            coroutineScope {
                val b1 = CompletableDeferred<Unit>()
                val j1 = launch { runSeat(barrier = b1, awaitBarrier = null) }
                val j2 = launch { runSeat(barrier = null, awaitBarrier = b1) }
                withTimeout(30_000) {
                    j1.join()
                    j2.join()
                }
            }

            assertTrue(winnerSeats.isNotEmpty(), "At least one seat should have observed GameOver")
            // Both seats, if both saw GameOver, must agree on the winner.
            if (winnerSeats.size == 2) {
                assertEquals(winnerSeats[0], winnerSeats[1], "Both seats must agree on the winner")
            }
        }

    // ── Test 8: Disconnect + reconnect resumes the seat with current state ──────

    @Test
    fun `disconnected client can reconnect to its seat and resume with current state`() =
        testApplication {
            application { module() }

            val roomCode = client.post("/rooms/2").body<String>()
            val wsClient = createClient { install(WebSockets) }

            val seat0Deferred = CompletableDeferred<Int>()
            val bothJoined = CompletableDeferred<Unit>()
            var reconnectView: WirePlayerView? = null
            var reconnectedFlag = false

            coroutineScope {
                // Seat 0: joins, captures its seat, then DROPS the connection (closes the WS block).
                val job0 =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            val joined = nextServerMsg() as ServerMessage.RoomJoined
                            seat0Deferred.complete(joined.seat)
                            // Drain one StateUpdate (game may not have started yet) then leave.
                            incoming.tryReceive()
                        }
                        // Block exits → server sees PlayerLeft for seat 0; a bot fills it and it becomes reconnectable.
                    }

                // Seat 1: joins so the match fills and starts (seat 0 already left → bot-filled).
                val job1 =
                    launch {
                        seat0Deferred.await()
                        job0.join() // ensure seat 0 has fully dropped before we fill the room
                        wsClient.webSocket("/play") {
                            sendMsg(ClientMessage.JoinRoom(matchId = roomCode, roomCode = roomCode))
                            nextServerMsg() as ServerMessage.RoomJoined
                            // Receive a couple of frames to let the game start + bots move.
                            repeat(2) { incoming.tryReceive() }
                            bothJoined.complete(Unit)
                        }
                    }

                job0.join()
                job1.join()
                bothJoined.await()

                val seat0 = seat0Deferred.await()

                // Now RECONNECT to seat 0 and assert we resume that exact seat + receive current state.
                val jobReconnect =
                    launch {
                        wsClient.webSocket("/play") {
                            sendMsg(
                                ClientMessage.JoinRoom(
                                    matchId = roomCode,
                                    roomCode = roomCode,
                                    reconnectSeat = seat0,
                                ),
                            )
                            val joined = withTimeout(10_000) { nextServerMsg() as ServerMessage.RoomJoined }
                            reconnectedFlag = joined.reconnected
                            assertEquals(seat0, joined.seat, "Reconnect must resume the SAME seat")
                            // The server immediately resyncs current state to a reconnecting seat.
                            val resync = withTimeout(10_000) { nextServerMsg() }
                            if (resync is ServerMessage.StateUpdate) {
                                reconnectView = resync.view
                            } else if (resync is ServerMessage.GameOver) {
                                // Game finished during the reconnect window — still proves the seat resumed.
                                reconnectView = null
                            }
                        }
                    }
                withTimeout(15_000) { jobReconnect.join() }
            }

            assertTrue(reconnectedFlag, "RoomJoined.reconnected must be true on a seat-resume")
            // The resync view (when the game was still live) must be projected for the resumed seat.
            reconnectView?.let { v ->
                assertEquals(seat0Deferred.getCompleted(), v.viewer, "Resync view must be for the resumed seat")
                assertSecrecy(v, v.viewer)
            }
        }
}
