package com.kursi.core.network

import com.kursi.protocol.wire.ClientMessage
import com.kursi.protocol.wire.GaddiJson
import com.kursi.protocol.wire.ServerMessage
import com.siddharth.kmp.network.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Ktor WebSocket client for the Gaddi protocol.
 *
 * Usage:
 * ```
 * val client = GaddiClient()
 * // connect returns a cold Flow of ServerMessages:
 * val session = client.connect(host = "localhost", port = 8080, roomCode = "ABCD")
 * // collect incoming messages while sending outgoing ones:
 * session.incoming.collect { msg -> ... }
 * session.send(ClientMessage.SubmitIntent(...))
 * ```
 *
 * The [HttpClient] is created lazily using the platform-appropriate engine
 * supplied by [httpClientEngine].
 */
class GaddiClient : AutoCloseable {
    private val http: HttpClient =
        HttpClient(httpClientEngine()) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(GaddiJson)
            }
        }

    /**
     * Opens a WebSocket connection to the Gaddi server and returns a [GaddiSession].
     *
     * The caller must:
     * 1. Collect [GaddiSession.incoming] to receive [ServerMessage]s (the [ClientMessage.JoinRoom]
     *    handshake is sent internally as the first frame).
     * 2. Call [GaddiSession.send] to submit [ClientMessage]s.
     *
     * The session terminates when [GaddiSession.incoming] completes (server closes) or
     * the caller cancels the collecting coroutine. The underlying socket is then closed.
     *
     * IMPLEMENTATION NOTE — concurrent duplex pump:
     * The previous version drained the outgoing channel only when an inbound frame arrived
     * (it sat blocked in `for (frame in incoming)`). That stalled every client-initiated message
     * whenever the server had nothing to push — fatal for a turn-based game where the client must
     * be able to send an intent at any time. We now run the send loop and the receive loop as two
     * independent children of the [channelFlow]'s producer scope, so outbound frames flush
     * immediately regardless of inbound traffic.
     *
     * @param host          Hostname or IP of the Gaddi server (e.g. "localhost", "10.0.0.1").
     * @param port          Port the server listens on (default 8080 per :server/App.kt).
     * @param roomCode      Short room code to join (e.g. "ABCD"). Matched case-insensitively by the server.
     * @param matchId       Stable match identifier to embed in every [ClientMessage]. Defaults to [roomCode].
     * @param reconnectSeat When non-null, asks the server to RESUME this seat (a seat previously held and
     *                      dropped). The server honours it if the seat is currently vacant. Null = fresh seat.
     */
    suspend fun connect(
        host: String,
        port: Int,
        roomCode: String,
        matchId: String = roomCode,
        reconnectSeat: Int? = null,
    ): GaddiSession {
        // Outgoing channel: caller pushes ClientMessages here; the WS send loop drains it.
        val outgoing = Channel<ClientMessage>(capacity = Channel.BUFFERED)

        // Incoming flow: wraps the WS duplex loop. channelFlow gives us a producer CoroutineScope so
        // the send loop and the receive loop can run concurrently as sibling children.
        val incoming: Flow<ServerMessage> =
            channelFlow {
                http.webSocket(host = host, port = port, path = "/play") {
                    // 1. Send JoinRoom as the first frame (protocol requirement).
                    val joinMsg =
                        ClientMessage.JoinRoom(
                            matchId = matchId,
                            roomCode = roomCode,
                            reconnectSeat = reconnectSeat,
                        )
                    send(Frame.Text(GaddiJson.encodeToString<ClientMessage>(joinMsg)))

                    // 2. Send loop — drains the outgoing channel into the socket as fast as the caller
                    //    enqueues, independent of inbound traffic.
                    val sender =
                        launch {
                            for (msg in outgoing) {
                                if (!isActive) break
                                send(Frame.Text(GaddiJson.encodeToString<ClientMessage>(msg)))
                            }
                        }

                    // 3. Receive loop — decode inbound frames and forward to the flow collector.
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val text = frame.readText()
                            val serverMsg: ServerMessage =
                                try {
                                    GaddiJson.decodeFromString(text)
                                } catch (_: Exception) {
                                    // Malformed frame: skip (server may have sent a future schema version).
                                    continue
                                }
                            send(serverMsg)
                        }
                    } finally {
                        // Socket closed (normally or abnormally) → stop the send loop and the channel.
                        sender.cancel()
                        outgoing.close()
                    }
                }
            }

        return GaddiSession(incoming = incoming, outgoing = outgoing)
    }

    override fun close() {
        http.close()
    }
}

/**
 * A live Gaddi WebSocket session returned by [GaddiClient.connect].
 *
 * @property incoming Cold [Flow] of [ServerMessage]s received from the server.
 *                    Completes when the WebSocket is closed (normally or abnormally).
 *                    Collecting it opens the socket; cancelling the collector closes it.
 * @property outgoing The internal [SendChannel] used by [send]. Exposed as [SendChannel] so
 *                    callers use the type-safe [send] function rather than pushing raw frames.
 */
class GaddiSession internal constructor(
    val incoming: Flow<ServerMessage>,
    private val outgoing: SendChannel<ClientMessage>,
) {
    /**
     * Enqueues a [ClientMessage] for delivery to the server.
     * Suspends only if the outgoing buffer is full (backpressure).
     *
     * Safe to call before the socket is open: the message buffers in the channel and flushes once
     * the send loop starts. Throws if the session has already closed (channel closed-for-send).
     */
    suspend fun send(message: ClientMessage) {
        outgoing.send(message)
    }
}
