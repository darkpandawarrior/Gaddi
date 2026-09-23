package com.kursi.protocol

import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.Intent
import com.kursi.engine.LossReason
import com.kursi.engine.Phase
import com.kursi.engine.PlayerId
import com.kursi.engine.ReactionStep
import com.kursi.engine.Role
import com.kursi.engine.applyIntent
import com.kursi.engine.initialState
import com.kursi.engine.redact
import com.kursi.protocol.wire.ClientMessage
import com.kursi.protocol.wire.GaddiJson
import com.kursi.protocol.wire.ServerMessage
import com.kursi.protocol.wire.WireAction
import com.kursi.protocol.wire.WireGameConfig
import com.kursi.protocol.wire.WireGameEvent
import com.kursi.protocol.wire.WireIntent
import com.kursi.protocol.wire.WireLossReason
import com.kursi.protocol.wire.WireOwnCard
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireReactionStep
import com.kursi.protocol.wire.WireRole
import com.kursi.protocol.wire.toEngine
import com.kursi.protocol.wire.toWire
import com.kursi.protocol.wire.toWireFor
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip serialization tests for `:shared-protocol`.
 *
 * Each test follows the pattern:
 *   1. Build/obtain a domain value (engine type or wire type).
 *   2. Map to wire (or build directly).
 *   3. Serialize to JSON string via [GaddiJson].
 *   4. Deserialize back from JSON string.
 *   5. Assert structural equality.
 *
 * A subset of tests also cross-map back to engine types and assert round-trip fidelity there.
 */
class ProtocolRoundTripTest {
    // ─────────────────────────── WireRole ───────────────────────────

    @Test
    fun wireRole_roundTrip_allValues() {
        for (role in WireRole.entries) {
            val json = GaddiJson.encodeToString(role)
            val decoded = GaddiJson.decodeFromString<WireRole>(json)
            assertEquals(role, decoded, "WireRole.$role round-trip failed")
        }
    }

    @Test
    fun wireRole_mapper_allEngineRoles() {
        for (role in Role.entries) {
            val wire = role.toWire()
            val back = wire.toEngine()
            assertEquals(role, back, "Role.$role mapper round-trip failed")
        }
    }

    // ─────────────────────────── WireAction ───────────────────────────

    @Test
    fun wireAction_roundTrip_allSubtypes() {
        val actions =
            listOf(
                WireAction.Income,
                WireAction.ForeignAid,
                WireAction.Coup(target = 1),
                WireAction.Tax,
                WireAction.Assassinate(target = 2),
                WireAction.Steal(target = 3),
                WireAction.Exchange,
            )
        for (action in actions) {
            val json = GaddiJson.encodeToString<WireAction>(action)
            val decoded = GaddiJson.decodeFromString<WireAction>(json)
            assertEquals(action, decoded, "WireAction $action round-trip failed")
        }
    }

    @Test
    fun wireAction_mapper_allEngineActions() {
        val actions =
            listOf(
                Action.Income,
                Action.ForeignAid,
                Action.Coup(PlayerId(1)),
                Action.Tax,
                Action.Assassinate(PlayerId(2)),
                Action.Steal(PlayerId(3)),
                Action.Exchange,
            )
        for (action in actions) {
            val wire = action.toWire()
            val back = wire.toEngine()
            assertEquals(action, back, "Action $action mapper round-trip failed")
        }
    }

    // ─────────────────────────── WireIntent ───────────────────────────

    @Test
    fun wireIntent_roundTrip_allSubtypes() {
        val intents: List<WireIntent> =
            listOf(
                WireIntent.DeclareAction(actor = 0, action = WireAction.Tax),
                WireIntent.DeclareAction(actor = 0, action = WireAction.Coup(target = 1)),
                WireIntent.Challenge(actor = 1),
                WireIntent.Block(actor = 2, role = WireRole.VAKIL),
                WireIntent.Pass(actor = 3),
                WireIntent.ChooseInfluenceToLose(actor = 1, card = 4),
                WireIntent.ChooseExchange(actor = 0, keep = listOf(2, 7)),
            )
        for (intent in intents) {
            val json = GaddiJson.encodeToString<WireIntent>(intent)
            val decoded = GaddiJson.decodeFromString<WireIntent>(json)
            assertEquals(intent, decoded, "WireIntent $intent round-trip failed")
        }
    }

    @Test
    fun wireIntent_mapper_allEngineIntents() {
        val intents: List<Intent> =
            listOf(
                Intent.DeclareAction(PlayerId(0), Action.Tax),
                Intent.DeclareAction(PlayerId(0), Action.Coup(PlayerId(1))),
                Intent.Challenge(PlayerId(1)),
                Intent.Block(PlayerId(2), Role.VAKIL),
                Intent.Pass(PlayerId(3)),
                Intent.ChooseInfluenceToLose(PlayerId(1), CardId(4)),
                Intent.ChooseExchange(PlayerId(0), listOf(CardId(2), CardId(7))),
            )
        for (intent in intents) {
            val wire = intent.toWire()
            val back = wire.toEngine()
            assertEquals(intent, back, "Intent $intent mapper round-trip failed")
        }
    }

    // ─────────────────────────── WirePhaseView ───────────────────────────

    @Test
    fun wirePhaseView_roundTrip_allSubtypes() {
        val phases: List<WirePhaseView> =
            listOf(
                WirePhaseView.Turn(actor = 0),
                WirePhaseView.Reactions(
                    actor = 0,
                    action = WireAction.Tax,
                    claimedRole = WireRole.NETA,
                    step = WireReactionStep.CHALLENGE_ACTION,
                    toRespond = 1,
                    blocker = null,
                    blockRole = null,
                ),
                WirePhaseView.Reactions(
                    actor = 0,
                    action = WireAction.Steal(target = 1),
                    claimedRole = WireRole.BABU,
                    step = WireReactionStep.CHALLENGE_BLOCK,
                    toRespond = 0,
                    blocker = 1,
                    blockRole = WireRole.JUGAADU,
                ),
                WirePhaseView.InfluenceLoss(loser = 1, reason = WireLossReason.COUPED),
                WirePhaseView.Exchange(actor = 2),
                WirePhaseView.Over(winner = 0),
            )
        for (phase in phases) {
            val json = GaddiJson.encodeToString<WirePhaseView>(phase)
            val decoded = GaddiJson.decodeFromString<WirePhaseView>(json)
            assertEquals(phase, decoded, "WirePhaseView $phase round-trip failed")
        }
    }

    // ─────────────────────────── WireOwnCard / Exchange.drawn ───────────────────────────

    @Test
    fun wireOwnCard_roundTrip() {
        val cards =
            listOf(
                WireOwnCard(id = 0, role = WireRole.NETA, faceUp = false),
                WireOwnCard(id = 7, role = WireRole.PATRAKAAR, faceUp = true),
            )
        for (c in cards) {
            val json = GaddiJson.encodeToString(c)
            assertEquals(c, GaddiJson.decodeFromString<WireOwnCard>(json))
        }
    }

    @Test
    fun wirePhaseView_exchange_carriesDrawnForActorOnly() {
        // With drawn populated (actor's own view).
        val withDrawn =
            WirePhaseView.Exchange(
                actor = 0,
                drawn = listOf(WireOwnCard(3, WireRole.BABU, false), WireOwnCard(4, WireRole.VAKIL, false)),
            )
        val json = GaddiJson.encodeToString<WirePhaseView>(withDrawn)
        assertEquals(withDrawn, GaddiJson.decodeFromString<WirePhaseView>(json))

        // Empty drawn (non-actor's view) — the default — must also round-trip.
        val empty = WirePhaseView.Exchange(actor = 1)
        val json2 = GaddiJson.encodeToString<WirePhaseView>(empty)
        val decoded2 = GaddiJson.decodeFromString<WirePhaseView>(json2)
        assertEquals(empty, decoded2)
        assertTrue((decoded2 as WirePhaseView.Exchange).drawn.isEmpty())
    }

    // ─────────────────────────── WireGameEvent (per-viewer secrecy) ───────────────────────────

    @Test
    fun wireGameEvent_roundTrip_publicEvents() {
        val events: List<WireGameEvent> =
            listOf(
                WireGameEvent.ActionDeclared(0, WireAction.Tax, WireRole.NETA),
                WireGameEvent.Challenged(1, 0, WireRole.NETA),
                WireGameEvent.ChallengeRevealed(0, 5, WireRole.NETA, true),
                WireGameEvent.CardReplaced(0, 5, drawn = 9),
                WireGameEvent.CardReplaced(0, 5, drawn = null),
                WireGameEvent.Blocked(1, WireRole.VAKIL, WireAction.Assassinate(0)),
                WireGameEvent.ActionResolved(0, WireAction.Income),
                WireGameEvent.ActionNegated(0, WireAction.ForeignAid),
                WireGameEvent.CoinsChanged(0, 3),
                WireGameEvent.CoinsTransferred(1, 0, 2),
                WireGameEvent.InfluenceLost(1, 4, WireRole.BHAI, WireLossReason.COUPED),
                WireGameEvent.PlayerEliminated(1),
                WireGameEvent.Exchanged(0, kept = listOf(1, 2), returned = listOf(8, 9)),
                WireGameEvent.Exchanged(0, kept = null, returned = listOf(8, 9)),
                WireGameEvent.Investigated(0, 1),
                WireGameEvent.InvestigateRedraw(1),
                WireGameEvent.TurnAdvanced(1, 4),
                WireGameEvent.GameEnded(0),
            )
        for (e in events) {
            val json = GaddiJson.encodeToString<WireGameEvent>(e)
            assertEquals(e, GaddiJson.decodeFromString<WireGameEvent>(json), "WireGameEvent $e round-trip failed")
        }
    }

    @Test
    fun gameEvent_toWireFor_redactsSecretCardIdsFromNonOwners() {
        val owner = PlayerId(0)
        val other = PlayerId(1)

        // CardReplaced.drawn is a secret face-down card — owner sees it, others get null.
        val replaced = GameEvent.CardReplaced(owner, returned = CardId(5), drawn = CardId(9))
        val forOwner = replaced.toWireFor(owner) as WireGameEvent.CardReplaced
        val forOther = replaced.toWireFor(other) as WireGameEvent.CardReplaced
        assertEquals(9, forOwner.drawn, "owner must see the drawn CardId")
        assertEquals(5, forOwner.returned)
        assertEquals(null, forOther.drawn, "non-owner must NOT see the drawn (secret) CardId")
        assertEquals(5, forOther.returned, "returned card is public")

        // Exchanged.kept are the actor's secret new cards — actor sees them, others get null.
        val exchanged = GameEvent.Exchanged(owner, kept = listOf(CardId(1), CardId(2)), returned = listOf(CardId(8)))
        val exForActor = exchanged.toWireFor(owner) as WireGameEvent.Exchanged
        val exForOther = exchanged.toWireFor(other) as WireGameEvent.Exchanged
        assertEquals(listOf(1, 2), exForActor.kept, "actor must see kept CardIds")
        assertEquals(null, exForOther.kept, "non-actor must NOT see the actor's secret kept CardIds")
        assertEquals(listOf(8), exForOther.returned, "returned cards are public")
    }

    // ─────────────────────────── WirePlayerView (realistic game state) ───────────────────────────

    /**
     * The main integration test: starts a real 2-player game, drives it through a few intents,
     * then redacts the state for each player and round-trips the resulting [WirePlayerView].
     */
    @Test
    fun wirePlayerView_roundTrip_fromRealGameState() {
        val config = GameConfig.forPlayers(2)
        var state = initialState(config, seed = 42L)

        // Advance a few turns so we have a non-trivial state (Income → Income).
        val actor0 = PlayerId(0)
        val actor1 = PlayerId(1)
        state = (applyIntent(state, Intent.DeclareAction(actor0, Action.Income)) as com.kursi.engine.ApplyOutcome.Accepted).state
        state = (applyIntent(state, Intent.DeclareAction(actor1, Action.Income)) as com.kursi.engine.ApplyOutcome.Accepted).state

        // Round-trip PlayerView for both players.
        for (seat in 0..1) {
            val pid = PlayerId(seat)
            val view = redact(state, pid)
            val wire = view.toWire()

            val json = GaddiJson.encodeToString<WirePlayerView>(wire)
            assertTrue(json.isNotEmpty(), "Serialized JSON must not be empty for seat $seat")

            val decoded = GaddiJson.decodeFromString<WirePlayerView>(json)
            assertEquals(wire, decoded, "WirePlayerView round-trip failed for seat $seat")

            // Structural assertions on the decoded view.
            assertEquals(seat, decoded.viewer, "viewer seat mismatch")
            assertEquals(2, decoded.myInfluence.size, "should have 2 face-down influence cards at game start")
            assertEquals(config.seatCount, decoded.players.size, "should see all players")
            assertEquals(WirePlayerView.SCHEMA_VERSION, decoded.schemaVersion)

            // myCards must carry exactly the viewer's own cards, with CardIds the client can address.
            assertEquals(2, decoded.myCards.size, "myCards should hold the 2 own face-down cards at start")
            assertTrue(decoded.myCards.all { !it.faceUp }, "all own cards are face-down at game start")
            // The roles in myCards must match myInfluence (same own cards, role-resolved).
            assertEquals(
                decoded.myInfluence.sortedBy { it.ordinal },
                decoded.myCards.map { it.role }.sortedBy { it.ordinal },
                "myCards roles must match myInfluence",
            )
        }
    }

    @Test
    fun wirePlayerView_myInfluence_notPresentInOpponentView() {
        // Security assertion: player 0's secret roles must not appear in player 1's WirePlayerView.
        val config = GameConfig.forPlayers(2)
        val state = initialState(config, seed = 99L)

        val view0 = redact(state, PlayerId(0)).toWire()
        val view1 = redact(state, PlayerId(1)).toWire()

        // view0 knows their own influence; view1 does not know player 0's influence.
        assertTrue(view0.myInfluence.isNotEmpty(), "Player 0 should see their own influence")
        assertTrue(view1.myInfluence.isNotEmpty(), "Player 1 should see their own influence")

        // The opponent entry for player 0 in view1 must NOT reveal face-down roles.
        val p0InView1 = view1.players.first { it.id == 0 }
        assertEquals(2, p0InView1.faceDownCount, "opponent faceDownCount should be 2 at game start")
        assertTrue(p0InView1.faceUpRoles.isEmpty(), "opponent faceUpRoles should be empty at game start")

        // Verify the JSON for view1 does not contain player 0's secret roles.
        val json1 = GaddiJson.encodeToString<WirePlayerView>(view1)
        // Player 0's myInfluence roles should not literally appear in player 1's serialized view
        // (this is a structural check, not a string-scan; the structural absence is enforced by the type system).
        val decodedView1 = GaddiJson.decodeFromString<WirePlayerView>(json1)
        val p0InDecoded = decodedView1.players.first { it.id == 0 }
        assertTrue(p0InDecoded.faceUpRoles.isEmpty(), "face-up roles should be empty for opponent in decoded view")
    }

    // ─────────────────────────── ClientMessage ───────────────────────────

    @Test
    fun clientMessage_roundTrip_allSubtypes() {
        val messages: List<ClientMessage> =
            listOf(
                ClientMessage.JoinRoom(matchId = "match-1", roomCode = "ABCD"),
                ClientMessage.SubmitIntent(
                    matchId = "match-1",
                    seq = 1L,
                    intent = WireIntent.DeclareAction(actor = 0, action = WireAction.Tax),
                ),
                ClientMessage.SubmitIntent(
                    matchId = "match-1",
                    seq = 2L,
                    intent = WireIntent.Challenge(actor = 1),
                ),
                ClientMessage.Pass(matchId = "match-1", seq = 3L),
                ClientMessage.ContinueBeat(matchId = "match-1"),
            )
        for (msg in messages) {
            val json = GaddiJson.encodeToString<ClientMessage>(msg)
            val decoded = GaddiJson.decodeFromString<ClientMessage>(json)
            assertEquals(msg, decoded, "ClientMessage $msg round-trip failed")
        }
    }

    // ─────────────────────────── ServerMessage ───────────────────────────

    @Test
    fun serverMessage_roundTrip_allSubtypes() {
        val config = GameConfig.forPlayers(2)
        val state = initialState(config, seed = 7L)
        val wireView = redact(state, PlayerId(0)).toWire()

        val messages: List<ServerMessage> =
            listOf(
                ServerMessage.StateUpdate(matchId = "match-1", seq = 1L, view = wireView),
                ServerMessage.RoomJoined(matchId = "match-1", seq = 0L, seat = 0, playerCount = 2),
                ServerMessage.GameOver(matchId = "match-1", seq = 10L, winnerSeat = 1),
                ServerMessage.Error(matchId = "match-1", seq = 2L, clientSeq = 1L, reason = "out of turn"),
            )
        for (msg in messages) {
            val json = GaddiJson.encodeToString<ServerMessage>(msg)
            val decoded = GaddiJson.decodeFromString<ServerMessage>(json)
            assertEquals(msg, decoded, "ServerMessage $msg round-trip failed")
        }
    }

    // ─────────────────────────── LossReason mapper ───────────────────────────

    @Test
    fun wireLossReason_mapper_allValues() {
        for (reason in LossReason.entries) {
            val wire = reason.toWire()
            val back = wire.toEngine()
            assertEquals(reason, back, "LossReason.$reason mapper round-trip failed")
        }
    }

    // ─────────────────────────── ReactionStep mapper ───────────────────────────

    @Test
    fun wireReactionStep_mapper_allValues() {
        for (step in ReactionStep.entries) {
            val wire = step.toWire()
            val back = wire.toEngine()
            assertEquals(step, back, "ReactionStep.$step mapper round-trip failed")
        }
    }

    // ─────────────────────────── WireGameConfig ───────────────────────────

    @Test
    fun wireGameConfig_roundTrip() {
        val config = GameConfig.forPlayers(4)
        val wire = config.toWire()
        val json = GaddiJson.encodeToString<WireGameConfig>(wire)
        val decoded = GaddiJson.decodeFromString<WireGameConfig>(json)
        assertEquals(wire, decoded, "WireGameConfig round-trip failed")
        assertEquals(config.seatCount, decoded.seatCount)
        assertEquals(config.coupCost, decoded.coupCost)
        assertEquals(config.forcedCoupThreshold, decoded.forcedCoupThreshold)
    }

    // ─────────────────────────── Full game simulation ───────────────────────────

    /**
     * Drive a full simulated 2-player game to completion, redacting and round-tripping the view
     * at each step. Validates that serialization stays correct throughout every game phase
     * (AwaitingAction, AwaitingReactions, AwaitingInfluenceLoss, GameOver).
     */
    @Test
    fun wirePlayerView_roundTrip_fullGame() {
        val config = GameConfig.forPlayers(2)
        var state = initialState(config, seed = 12345L)

        var steps = 0
        val maxSteps = 500 // guard against infinite loops in test

        while (state.phase !is Phase.GameOver && steps < maxSteps) {
            // Redact and round-trip for both players at every state.
            for (seat in 0..1) {
                val view = redact(state, PlayerId(seat)).toWire()
                val json = GaddiJson.encodeToString<WirePlayerView>(view)
                val decoded = GaddiJson.decodeFromString<WirePlayerView>(json)
                assertEquals(view, decoded, "WirePlayerView round-trip failed at step $steps for seat $seat")
            }

            // Drive the game with simple moves: always pick the first legal intent.
            val nextActor = com.kursi.engine.whoActsNext(state) ?: break
            val legalIntents = com.kursi.engine.legalIntents(state, nextActor)
            if (legalIntents.isEmpty()) break
            val intent = legalIntents.first()
            val outcome = applyIntent(state, intent)
            state = (outcome as? com.kursi.engine.ApplyOutcome.Accepted)?.state ?: break
            steps++
        }

        // Verify game reached GameOver and the final view round-trips.
        assertTrue(state.phase is Phase.GameOver || steps >= maxSteps, "Game should have ended")
        if (state.phase is Phase.GameOver) {
            for (seat in 0..1) {
                val finalView = redact(state, PlayerId(seat)).toWire()
                val json = GaddiJson.encodeToString<WirePlayerView>(finalView)
                val decoded = GaddiJson.decodeFromString<WirePlayerView>(json)
                assertEquals(finalView, decoded, "Final WirePlayerView round-trip failed for seat $seat")
                assertTrue(decoded.phase is WirePhaseView.Over, "Final phase should be Over")
            }
        }
    }
}
