package com.kursi.core.network

import com.siddharth.kmp.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Thin REST client over the server's room-lifecycle HTTP endpoints (see :server `Routing.kt`):
 *
 *  - `POST /rooms/{playerCount}`      → creates a PRIVATE room, returns its short code (plain text).
 *  - `POST /quickmatch/{playerCount}` → returns the code of a WAITING public room of that size,
 *                                       creating one if none is open (public matchmaking pairing).
 *
 * The WebSocket `/play` join (and the whole match lifecycle) is owned by [OnlineKursiClient]; this is
 * only the pre-match handshake that turns a player's intent ("host a private room" / "find a public
 * game") into a room code the client can then connect to. Joining a private room BY code needs no REST
 * call at all — the code is shared out-of-band and passed straight to [OnlineKursiClient.connect].
 *
 * Errors are surfaced as a sealed [RoomResult] rather than thrown, so the UI can render an honest,
 * in-identity failure banner (the server unreachable, an invalid size) without a try/catch at every
 * call site.
 *
 * @param host server host (loopback for desktop testing, a LAN IP, or the cloud host).
 * @param port server port (default 8080 per :server `App.kt`).
 * @param clientFactory builds the [HttpClient] used for the two POSTs. Defaults to the shared
 *                      client with expectSuccess/retry/timeout disabled — this method already does
 *                      its own manual status-code handling (see [postForCode]) and can be long-lived
 *                      like a socket, so the library defaults (throw-on-non-2xx, retry, 30s timeout)
 *                      would fight that. A fresh client is created per [RoomApi] and closed by [close].
 */
class RoomApi(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    clientFactory: () -> HttpClient = {
        createHttpClient(expectSuccess = false, retry = false, requestTimeoutMillis = null)
    },
) : AutoCloseable {
    private val http: HttpClient = clientFactory()

    /** Creates a PRIVATE room of [playerCount] seats and returns its short code to share. */
    suspend fun createPrivateRoom(playerCount: Int): RoomResult = postForCode("/rooms/$playerCount")

    /**
     * Joins the public quick-match queue for [playerCount]-seat games: returns the code of a waiting
     * public room (a fresh one if none is open). Two callers asking for the same size land in the same
     * room, so connecting to this code with [OnlineKursiClient.connect] pairs them.
     */
    suspend fun quickMatch(playerCount: Int): RoomResult = postForCode("/quickmatch/$playerCount")

    // TooGenericExceptionCaught: Ktor's engines throw platform-specific I/O types (IOException on
    // JVM/Android, a Darwin NSError wrapper on iOS, a DOM exception on wasmJs) with no common
    // supertype below Exception that commonMain can name. "Unreachable" is exactly the outcome for
    // all of them, and e.message is carried into the result rather than swallowed.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun postForCode(path: String): RoomResult =
        try {
            val response = http.post("http://$host:$port$path")
            val body = response.bodyAsText().trim()
            when {
                response.status.isSuccess() && body.isNotEmpty() -> RoomResult.Success(body.uppercase())
                response.status == HttpStatusCode.BadRequest ->
                    RoomResult.Rejected(body.ifEmpty { "Invalid request" })
                else -> RoomResult.Rejected("Server returned ${response.status.value}")
            }
        } catch (e: Exception) {
            RoomResult.Unreachable(e.message)
        }

    override fun close() {
        http.close()
    }

    companion object {
        /** Default Kursi server port (matches :server `App.kt`). */
        const val DEFAULT_PORT: Int = 8080
    }
}

/** The outcome of a room-lifecycle REST call, modelled so the UI can render an honest banner. */
sealed interface RoomResult {
    /** Got a room code. [code] is uppercased (the server matches case-insensitively). */
    data class Success(
        val code: String,
    ) : RoomResult

    /** The server answered but refused (bad player count, etc.). [reason] is server-supplied. */
    data class Rejected(
        val reason: String,
    ) : RoomResult

    /** The server could not be reached (offline, wrong host/port, refused connection). */
    data class Unreachable(
        val cause: String?,
    ) : RoomResult
}
