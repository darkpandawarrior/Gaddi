package com.kursi.core.network

import com.siddharth.kmp.network.httpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire model for one row from the server's /standings endpoint. */
@Serializable
data class RemoteStanding(
    val position: Int,
    val name: String,
    val rating: Int,
)

/**
 * Fetches the online leaderboard from the Gaddi server.
 *
 * GETs `http://host:port/standings` and decodes the JSON array into a [List<RemoteStanding>]
 * sorted best-first (as the server delivers them).
 *
 * NEVER throws: any network or parse failure is swallowed and an empty list is returned, so
 * callers that are offline or talking to an older server simply see no standings rather than
 * crashing. The [HttpClient] is created and closed within this call.
 *
 * @param host Hostname or IP of the Gaddi server (e.g. "localhost", "192.168.1.5").
 * @param port Port the server listens on (default 8080 per :server/App.kt).
 * @return Standings sorted best-first, or an empty list on any failure.
 */
suspend fun fetchStandings(
    host: String,
    port: Int,
): List<RemoteStanding> {
    val client =
        HttpClient(httpClientEngine()) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    return try {
        client.get("http://$host:$port/standings").body()
    } catch (_: Exception) {
        emptyList()
    } finally {
        client.close()
    }
}
