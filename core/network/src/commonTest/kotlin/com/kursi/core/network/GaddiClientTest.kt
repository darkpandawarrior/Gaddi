package com.kursi.core.network

import com.kursi.engine.Action
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import com.kursi.protocol.wire.ClientMessage
import com.kursi.protocol.wire.GaddiJson
import com.kursi.protocol.wire.ServerMessage
import com.kursi.protocol.wire.WireGameConfig
import com.kursi.protocol.wire.WireOpponentView
import com.kursi.protocol.wire.WireOwnCard
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireRole
import com.kursi.protocol.wire.toWire
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for :core:network.
 *
 * These tests exercise the encode/decode round-trips that [GaddiClient] performs,
 * plus the [OnlineGameSession] mapping logic — no live server required.
 */
class GaddiClientTest {
    // ─────────────────────────── helpers ───────────────────────────

    private fun sampleWireConfig() =
        WireGameConfig(
            seatCount = 2,
            copiesPerRole = 3,
            roleCount = 5,
            influencePerPlayer = 2,
            startingCoins = 2,
            coupCost = 7,
            assassinateCost = 3,
            taxAmount = 3,
            foreignAidAmount = 2,
            stealAmount = 2,
            incomeAmount = 1,
            exchangeDrawCount = 2,
            forcedCoupThreshold = 10,
            coinSupply = 50,
        )

    private fun samplePlayerView(viewer: Int = 0) =
        WirePlayerView(
            viewer = viewer,
            config = sampleWireConfig(),
            treasury = 30,
            deckCount = 10,
            turnNumber = 1,
            myCoins = 2,
            myInfluence = listOf(WireRole.NETA, WireRole.BHAI),
            myFaceUp = emptyList(),
            myCards =
                listOf(
                    WireOwnCard(id = 0, role = WireRole.NETA, faceUp = false),
                    WireOwnCard(id = 1, role = WireRole.BHAI, faceUp = false),
                ),
            players =
                listOf(
                    WireOpponentView(
                        id = viewer,
                        seatIndex = viewer,
                        coins = 2,
                        faceUpRoles = emptyList(),
                        faceDownCount = 2,
                        eliminated = false,
                    ),
                ),
            phase = WirePhaseView.Turn(actor = viewer),
        )

    // ─────────────────────────── encode/decode round-trips ───────────────────────────

    @Test
    fun clientMessage_joinRoom_roundTrip() {
        val original: ClientMessage = ClientMessage.JoinRoom(matchId = "ABCD", roomCode = "ABCD")
        val json = GaddiJson.encodeToString<ClientMessage>(original)
        val decoded: ClientMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
        assertIs<ClientMessage.JoinRoom>(decoded)
    }

    @Test
    fun clientMessage_submitIntent_roundTrip() {
        val intent =
            com.kursi.protocol.wire.WireIntent.DeclareAction(
                actor = 0,
                action = com.kursi.protocol.wire.WireAction.Tax,
            )
        val original: ClientMessage =
            ClientMessage.SubmitIntent(
                matchId = "ABCD",
                seq = 1L,
                intent = intent,
            )
        val json = GaddiJson.encodeToString<ClientMessage>(original)
        val decoded: ClientMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
        val submitDecoded = assertIs<ClientMessage.SubmitIntent>(decoded)
        assertEquals(1L, submitDecoded.seq)
    }

    @Test
    fun clientMessage_pass_roundTrip() {
        val original: ClientMessage = ClientMessage.Pass(matchId = "ABCD", seq = 5L)
        val json = GaddiJson.encodeToString<ClientMessage>(original)
        val decoded: ClientMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
    }

    @Test
    fun serverMessage_stateUpdate_roundTrip() {
        val view = samplePlayerView(viewer = 0)
        val original: ServerMessage =
            ServerMessage.StateUpdate(
                matchId = "ABCD",
                seq = 1L,
                view = view,
            )
        val json = GaddiJson.encodeToString<ServerMessage>(original)
        val decoded: ServerMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
        val stateDecoded = assertIs<ServerMessage.StateUpdate>(decoded)
        assertEquals(0, stateDecoded.view.viewer)
    }

    @Test
    fun serverMessage_roomJoined_roundTrip() {
        val original: ServerMessage =
            ServerMessage.RoomJoined(
                matchId = "ABCD",
                seq = 0L,
                seat = 1,
                playerCount = 2,
            )
        val json = GaddiJson.encodeToString<ServerMessage>(original)
        val decoded: ServerMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
        assertIs<ServerMessage.RoomJoined>(decoded)
    }

    @Test
    fun serverMessage_error_roundTrip() {
        val original: ServerMessage =
            ServerMessage.Error(
                matchId = "ABCD",
                seq = 0L,
                clientSeq = 3L,
                reason = "Out of turn",
            )
        val json = GaddiJson.encodeToString<ServerMessage>(original)
        val decoded: ServerMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
    }

    @Test
    fun serverMessage_gameOver_roundTrip() {
        val original: ServerMessage =
            ServerMessage.GameOver(
                matchId = "ABCD",
                seq = 42L,
                winnerSeat = 1,
            )
        val json = GaddiJson.encodeToString<ServerMessage>(original)
        val decoded: ServerMessage = GaddiJson.decodeFromString(json)
        assertEquals(original, decoded)
        val gameOverDecoded = assertIs<ServerMessage.GameOver>(decoded)
        assertEquals(1, gameOverDecoded.winnerSeat)
    }

    // ─────────────────────────── Intent → WireIntent mapping ───────────────────────────

    @Test
    fun intent_toWire_declareAction_tax() {
        val intent = Intent.DeclareAction(actor = PlayerId(0), action = Action.Tax)
        val wire = intent.toWire()
        val declareWire = assertIs<com.kursi.protocol.wire.WireIntent.DeclareAction>(wire)
        assertEquals(0, declareWire.actor)
        assertIs<com.kursi.protocol.wire.WireAction.Tax>(declareWire.action)
    }

    @Test
    fun intent_toWire_challenge() {
        val intent = Intent.Challenge(actor = PlayerId(1))
        val wire = intent.toWire()
        assertIs<com.kursi.protocol.wire.WireIntent.Challenge>(wire)
        assertEquals(1, wire.actor)
    }

    // ─────────────────────────── OnlineGameSession state mapping ───────────────────────────

    @Test
    fun onlineGameSession_stateUpdate_updatesPlayerView() =
        runTest {
            val view = samplePlayerView(viewer = 0)
            val stateUpdate = ServerMessage.StateUpdate(matchId = "ABCD", seq = 1L, view = view)

            // Build a fake session that emits one StateUpdate then completes
            val fakeSession =
                GaddiSession(
                    incoming = flowOf(stateUpdate),
                    outgoing = kotlinx.coroutines.channels.Channel(),
                )

            val gameSession =
                OnlineGameSession(
                    session = fakeSession,
                    scope = this,
                    mySeat = 0,
                )

            // Before start: playerView is null
            assertNull(gameSession.playerView.value)

            val job = gameSession.start()
            job.join()

            // After StateUpdate is processed: playerView reflects the view
            val receivedView = gameSession.playerView.value
            assertNotNull(receivedView)
            assertEquals(0, receivedView.viewer)
            assertEquals(2, receivedView.myCoins)
            assertEquals(listOf(WireRole.NETA, WireRole.BHAI), receivedView.myInfluence)
        }

    @Test
    fun onlineGameSession_roomJoined_updatesRoomJoined() =
        runTest {
            val roomJoined = ServerMessage.RoomJoined(matchId = "ABCD", seq = 0L, seat = 0, playerCount = 2)

            val fakeSession =
                GaddiSession(
                    incoming = flowOf(roomJoined),
                    outgoing = kotlinx.coroutines.channels.Channel(),
                )

            val gameSession =
                OnlineGameSession(
                    session = fakeSession,
                    scope = this,
                    mySeat = 0,
                )

            val job = gameSession.start()
            job.join()

            val result = gameSession.roomJoined.value
            assertNotNull(result)
            assertEquals(0, result.seat)
            assertEquals(2, result.playerCount)
        }

    @Test
    fun onlineGameSession_gameOver_updatesGameOver() =
        runTest {
            val gameOver = ServerMessage.GameOver(matchId = "ABCD", seq = 99L, winnerSeat = 1)

            val fakeSession =
                GaddiSession(
                    incoming = flowOf(gameOver),
                    outgoing = kotlinx.coroutines.channels.Channel(),
                )

            val gameSession =
                OnlineGameSession(
                    session = fakeSession,
                    scope = this,
                    mySeat = 0,
                )

            val job = gameSession.start()
            job.join()

            val result = gameSession.gameOver.value
            assertNotNull(result)
            assertEquals(1, result.winnerSeat)
        }

    @Test
    fun onlineGameSession_error_updatesLastError() =
        runTest {
            val error = ServerMessage.Error(matchId = "ABCD", seq = 0L, clientSeq = 1L, reason = "Illegal move")

            val fakeSession =
                GaddiSession(
                    incoming = flowOf(error),
                    outgoing = kotlinx.coroutines.channels.Channel(),
                )

            val gameSession =
                OnlineGameSession(
                    session = fakeSession,
                    scope = this,
                    mySeat = 0,
                )

            val job = gameSession.start()
            job.join()

            val result = gameSession.lastError.value
            assertNotNull(result)
            assertEquals("Illegal move", result.reason)
        }

    @Test
    fun onlineGameSession_multipleUpdates_playerViewReflectsLatest() =
        runTest {
            val view1 = samplePlayerView(viewer = 0).copy(myCoins = 3)
            val view2 = samplePlayerView(viewer = 0).copy(myCoins = 5)

            val fakeSession =
                GaddiSession(
                    incoming =
                        flowOf(
                            ServerMessage.StateUpdate(matchId = "ABCD", seq = 1L, view = view1),
                            ServerMessage.StateUpdate(matchId = "ABCD", seq = 2L, view = view2),
                        ),
                    outgoing = kotlinx.coroutines.channels.Channel(),
                )

            val gameSession =
                OnlineGameSession(
                    session = fakeSession,
                    scope = this,
                    mySeat = 0,
                )

            val job = gameSession.start()
            job.join()

            // Only the latest StateUpdate should be visible
            assertEquals(5, gameSession.playerView.value?.myCoins)
        }

    // ─────────────────────────── schema-version field ───────────────────────────

    @Test
    fun wirePlayerView_schemaVersion_isPresentInJson() {
        val view = samplePlayerView()
        val json = GaddiJson.encodeToString(view)
        // encodeDefaults = true on GaddiJson ensures schemaVersion is always emitted
        assertTrue(
            actual = json.contains("\"schemaVersion\""),
            message = "schemaVersion field must be present in serialized WirePlayerView. Got: $json",
        )
        assertTrue(
            actual = json.contains("\"schemaVersion\":1"),
            message = "schemaVersion must equal 1. Got: $json",
        )
    }

    @Test
    fun wirePlayerView_unknownField_isIgnoredByDecoder() {
        // Simulate a newer server adding an unknown field
        val jsonWithExtra =
            """
            {
              "viewer": 0,
              "config": {
                "seatCount": 2, "copiesPerRole": 3, "roleCount": 5,
                "influencePerPlayer": 2, "startingCoins": 2, "coupCost": 7,
                "assassinateCost": 3, "taxAmount": 3, "foreignAidAmount": 2,
                "stealAmount": 2, "incomeAmount": 1, "exchangeDrawCount": 2,
                "forcedCoupThreshold": 10, "coinSupply": 50
              },
              "treasury": 30, "deckCount": 10, "turnNumber": 1,
              "myCoins": 2,
              "myInfluence": [],
              "myFaceUp": [],
              "players": [],
              "phase": {"type": "com.kursi.protocol.wire.WirePhaseView.Turn", "actor": 0},
              "schemaVersion": 99,
              "futureField": "ignored"
            }
            """.trimIndent()
        // Should not throw — ignoreUnknownKeys = true
        val decoded = GaddiJson.decodeFromString<WirePlayerView>(jsonWithExtra)
        assertEquals(0, decoded.viewer)
        assertEquals(99, decoded.schemaVersion)
    }
}
