package com.kursi.server

import com.kursi.protocol.wire.GaddiJson
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor application configuration. Installs all plugins and wires the [RoomRegistry].
 *
 * Not bound to a real port here — the entrypoint [main] binds Netty.
 * Tests use [testApplication { }] without a bound port.
 */
fun Application.module() {
    // Use the application's own coroutine scope so all match actors are cancelled on shutdown/test-teardown.
    val registry = RoomRegistry(this)

    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        json(GaddiJson)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, "Server error: ${cause.message}")
        }
    }

    configureRouting(registry)
}
