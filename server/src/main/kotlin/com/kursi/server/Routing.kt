package com.kursi.server

import com.kursi.protocol.wire.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

/** Wire DTO for the /standings endpoint. Sorted best-first by position. */
@Serializable
data class StandingDto(
    val position: Int,
    val name: String,
    val rating: Int,
)

/** Demo ladder — replaced by a persistent store once ratings are tracked. */
private val demoStandings: List<StandingDto> =
    listOf(
        StandingDto(1, "Pappu Bhai", 2_850),
        StandingDto(2, "Chhota Don", 2_740),
        StandingDto(3, "Sarkari Babu", 2_680),
        StandingDto(4, "Gali ka Neta", 2_610),
        StandingDto(5, "Chamcha No. 1", 2_540),
        StandingDto(6, "Afwaah Queen", 2_470),
        StandingDto(7, "Sting Operator", 2_390),
        StandingDto(8, "Badla Bhai", 2_310),
        StandingDto(9, "Alliance Uncle", 2_230),
        StandingDto(10, "Jugaadu Ji", 2_150),
    )

/**
 * All routes: WebSocket `/play` game endpoint + HTTP `/health` liveness probe.
 */
fun Application.configureRouting(registry: RoomRegistry) {
    routing {
        // ── Liveness probe ──────────────────────────────────────────────────
        get("/health") {
            call.respondText("OK")
        }

        // ── Build info ───────────────────────────────────────────────────────
        get("/version") {
            call.respondText(BuildInfo.FINGERPRINT)
        }

        // ── Online leaderboard ──────────────────────────────────────────────
        get("/standings") {
            call.respond(demoStandings)
        }

        // ── Main game WebSocket endpoint ────────────────────────────────────
        // The handshake and the message pump live in their own functions below. Inlining them here
        // put configureRouting at 153 lines and cyclomatic complexity 25 — a route table that
        // nobody could read past the first endpoint.
        webSocket("/play") {
            val connectionId = UUID.randomUUID().toString()
            val joined = handshakeJoin(registry, connectionId) ?: return@webSocket
            try {
                pumpClientMessages(joined, connectionId)
            } finally {
                // Notify the actor the connection dropped.
                joined.actor.post(MatchCommand.PlayerLeft(connectionId))
            }
        }
    }
}


/** What a successful `/play` handshake yields: the match actor, the room code and the seat taken. */
private data class JoinedRoom(
    val actor: MatchActor,
    val roomCode: String,
    val result: PlayerJoinedResult,
)

/**
 * Steps 1-4 of the `/play` handshake: read the opening JoinRoom frame, resolve the room, register
 * with its actor and await the seat assignment. Returns null once it has already closed the socket
 * with the reason, so the caller just returns.
 *
 * TooGenericExceptionCaught: the join awaits a CompletableDeferred the ACTOR completes, so the
 * failure type is whatever the actor threw — there is no narrower supertype to name here. The
 * message is forwarded to the client in a ServerMessage.Error rather than dropped.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun DefaultWebSocketServerSession.handshakeJoin(
    registry: RoomRegistry,
    connectionId: String,
): JoinedRoom? {
    // 1. Read the first frame — must be a JoinRoom ClientMessage
    val firstText =
        (incoming.receive() as? Frame.Text)?.readText()
            ?: run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected JoinRoom"))
                return null
            }

    val joinMsg: ClientMessage =
        try {
            KursiJson.decodeFromString(firstText)
        } catch (e: SerializationException) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid ClientMessage: ${e.message}"))
            return null
        }

    if (joinMsg !is ClientMessage.JoinRoom) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "First message must be JoinRoom"))
        return null
    }

    // 2. Find the room. The registry generates its own codes, so a code we do not hold cannot be
    //    honoured by creating one — the client must create a room first.
    val roomCode = joinMsg.roomCode.uppercase()
    val actor =
        registry.findRoom(roomCode) ?: run {
            sendServerError(roomCode, seq = 0, reason = "Room '$roomCode' not found. Create a room first.")
            close(CloseReason(CloseReason.Codes.NORMAL, "Room not found"))
            return null
        }

    // 3. Register this connection with the match actor (honouring a reconnect-seat request)
    val replyDeferred = CompletableDeferred<PlayerJoinedResult>()
    actor.post(
        MatchCommand.PlayerJoined(
            connectionId = connectionId,
            session = this,
            reconnectSeat = joinMsg.reconnectSeat,
            replyChannel = replyDeferred,
        ),
    )

    val joinResult =
        try {
            replyDeferred.await()
        } catch (e: Exception) {
            sendServerError(roomCode, seq = 0, reason = "Could not join: ${e.message}")
            close(CloseReason(CloseReason.Codes.NORMAL, "Join failed"))
            return null
        }

    // 4. RoomJoined is sent by the actor itself (in the serial actor loop) so it always precedes
    //    any StateUpdate — see MatchActor.handleJoin. Here we only act on metadata.
    //
    // Once a quick-match room is full, retire it from the open queue so the next quick-match
    // request opens a fresh room rather than landing in a started game.
    if (joinResult.playerCount >= actor.seatCount) {
        registry.markFilled(roomCode)
    }
    return JoinedRoom(actor, roomCode, joinResult)
}

/**
 * Step 5: read client frames and forward them to the match actor until the socket closes.
 *
 * TooGenericExceptionCaught: a malformed frame from one client must not drop that client's socket,
 * let alone the match. The decode failure is reported back as a ServerMessage.Error and the loop
 * continues; `Exception` is the boundary because the frame is attacker-controlled input.
 */
@Suppress("TooGenericExceptionCaught")
private suspend fun DefaultWebSocketServerSession.pumpClientMessages(
    joined: JoinedRoom,
    connectionId: String,
) {
    for (frame in incoming) {
        if (frame !is Frame.Text) continue
        val msg: ClientMessage =
            try {
                KursiJson.decodeFromString(frame.readText())
            } catch (e: Exception) {
                sendServerError(joined.roomCode, seq = -1, reason = "Invalid message: ${e.message}")
                continue
            }

        when (msg) {
            is ClientMessage.SubmitIntent ->
                joined.actor.post(
                    MatchCommand.IntentSubmitted(
                        connectionId = connectionId,
                        clientSeq = msg.seq,
                        wireIntent = msg.intent,
                    ),
                )
            is ClientMessage.Pass ->
                // The Pass fast-path: encode a WireIntent.Pass for the seat assigned on join.
                joined.actor.post(
                    MatchCommand.IntentSubmitted(
                        connectionId = connectionId,
                        clientSeq = msg.seq,
                        wireIntent = WireIntent.Pass(actor = joined.result.seat),
                    ),
                )
            is ClientMessage.ContinueBeat ->
                // Track 6: release a currently-pending beat wait now instead of waiting out the
                // server's bounded ack timeout (see BeatAckGate). No-op if none is pending.
                joined.actor.post(MatchCommand.BeatAckReceived(connectionId))
            is ClientMessage.JoinRoom -> Unit // Ignore subsequent JoinRoom frames
        }
    }
}

/** Sends a [ServerMessage.Error] frame. The three call sites above differed only in seq and reason. */
private suspend fun DefaultWebSocketServerSession.sendServerError(
    matchId: String,
    seq: Long,
    reason: String,
) {
    val errMsg: ServerMessage = ServerMessage.Error(matchId = matchId, seq = seq, clientSeq = -1, reason = reason)
    send(Frame.Text(KursiJson.encodeToString(errMsg)))
}
