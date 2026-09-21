package com.kursi.server

import com.kursi.ai.EasyPolicy
import com.kursi.ai.Policy
import com.kursi.engine.*
import com.kursi.protocol.wire.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.encodeToString

/**
 * Seat-to-seed stride for the filler bots. An odd prime so two seats in the same match never share
 * a policy stream, while the match seed still reproduces the whole table.
 */
private const val BOT_SEED_STRIDE = 37L

/**
 * Commands sent into a [MatchActor]'s mailbox. Processed serially — no locks, no races.
 */
sealed interface MatchCommand {
    /**
     * A human player's WebSocket connection arrived; assign (or RE-assign on reconnect) a seat.
     *
     * @param reconnectSeat if non-null, the connection is asking to RESUME a specific seat (a seat it
     *   previously held and dropped). The actor honours it only if that seat exists and is currently
     *   vacant (held by a bot/empty); otherwise it falls back to a fresh seat or rejects when full.
     */
    data class PlayerJoined(
        val connectionId: String,
        val session: WebSocketSession,
        val reconnectSeat: Int?,
        val replyChannel: CompletableDeferred<PlayerJoinedResult>,
    ) : MatchCommand

    /** A human player's WebSocket disconnected. */
    data class PlayerLeft(
        val connectionId: String,
    ) : MatchCommand

    /** A human player submitted an intent. */
    data class IntentSubmitted(
        val connectionId: String,
        val clientSeq: Long,
        val wireIntent: WireIntent,
    ) : MatchCommand

    /**
     * A connected human sent [com.kursi.protocol.wire.ClientMessage.ContinueBeat] — release the currently
     * pending [MatchActor.beatGate] wait now instead of waiting out its bounded timeout. Ignored if the
     * sender is not a seated human or no beat is pending (stray/late ack).
     */
    data class BeatAckReceived(
        val connectionId: String,
    ) : MatchCommand

    /**
     * Internal self-post: [MatchActor.beatGate]'s bounded wait elapsed with no ack. Never sent by a
     * client — [BeatAckGate] posts this to the actor's own mailbox so the resulting bot cascade still
     * runs inside the actor's single serial loop (no locks, no races).
     */
    data object BeatTimedOut : MatchCommand
}

/**
 * True for a non-trivial event batch worth pacing the table for. Mirrors the DRAMATIC/ROUTINE half of
 * [com.kursi.feature.game.tierFor] (`feature/game/BeatGate.kt`) — duplicated here because :server (a
 * plain JVM Ktor app) cannot depend on :feature:game (a Compose UI module). Pure bookkeeping batches
 * (income ticks, coin deltas, redraws, …) stay false and are never paced, exactly as offline.
 */
internal fun isMeaningfulBeat(events: List<GameEvent>): Boolean =
    events.any {
        it is GameEvent.Challenged ||
            it is GameEvent.ChallengeRevealed ||
            it is GameEvent.Blocked ||
            it is GameEvent.InfluenceLost ||
            it is GameEvent.PlayerEliminated ||
            it is GameEvent.GameEnded ||
            it is GameEvent.ActionDeclared ||
            it is GameEvent.ActionResolved ||
            it is GameEvent.ActionNegated ||
            it is GameEvent.Exchanged ||
            it is GameEvent.CoinsTransferred
    }

data class PlayerJoinedResult(
    val seat: Int,
    val matchId: String,
    val playerCount: Int,
    /** True when this join resumed a previously-held seat rather than taking a fresh one. */
    val reconnected: Boolean,
)

/**
 * Per-match actor: owns the authoritative [GameState] and all seat→connection mappings.
 *
 * Concurrency model: one coroutine consumes the [mailbox] channel in a serial `for (cmd in mailbox)`
 * loop — [state] is a plain `var` touched only inside that loop. No locks, no CAS, no races.
 * Mutation happens only here; outsiders post [MatchCommand]s and never touch state directly.
 *
 * Redaction: after every accepted intent (including bot moves) the actor calls
 * `redact(state, seat).toWire()` *per human seat* before sending — each player receives
 * only their own private projection; hidden card roles are structurally absent from frames
 * destined for other seats. The descriptive events for the step are projected per seat via
 * [toWireFor] so a secret drawn/kept CardId never reaches a seat that does not own it.
 */
class MatchActor(
    val matchId: String,
    private val config: GameConfig,
    private val seed: Long,
    scope: CoroutineScope,
) {
    // ── Mailbox (bounded — backpressure rather than OOM) ────────────────────
    private val mailbox = Channel<MatchCommand>(capacity = 128)

    // Retained so timers (Track 6 beat-ack gate) can be launched outside a property initializer —
    // a plain constructor param isn't reachable from ordinary member functions.
    private val actorScope = scope

    // Track 6: bounded wait for a human "continue" ack before driving the next bot cascade — see
    // [BeatAckGate] kdoc. Self-posts [MatchCommand.BeatTimedOut] on timeout; never mutates state directly.
    private val beatGate = BeatAckGate(actorScope) { mailbox.trySend(MatchCommand.BeatTimedOut) }

    // ── State — confined to the actor loop ──────────────────────────────────
    private var state: GameState = initialState(config, seed)
    private var serverSeq: Long = 0L
    private var started: Boolean = false

    // seat index → (connectionId, WebSocketSession) for the CURRENTLY connected humans
    private val humanSeats = mutableMapOf<Int, Pair<String, WebSocketSession>>()

    // connectionId → seat index (reverse map for fast lookup)
    private val connToSeat = mutableMapOf<String, Int>()

    // bot seats: seat index → Policy (a seat is bot-driven when empty OR after a human drops it)
    private val botPolicies = mutableMapOf<Int, Policy>()

    // Seats that were once held by a human and are now vacant — eligible for reconnection.
    private val reconnectableSeats = mutableSetOf<Int>()

    // How many seats this match expects before it starts
    val seatCount: Int get() = config.seatCount

    // Monotonically increasing server seq
    private fun nextSeq() = ++serverSeq

    // ── Actor loop ──────────────────────────────────────────────────────────
    // TooGenericExceptionCaught: this is the actor's supervision boundary. One malformed command
    // from one client must not kill the mailbox loop and take every other seat in the match down
    // with it. CancellationException is rethrown above the catch so shutdown still works, and the
    // exception is logged rather than swallowed.
    @Suppress("TooGenericExceptionCaught")
    private val job: Job =
        scope.launch {
            for (cmd in mailbox) {
                try {
                    when (cmd) {
                        is MatchCommand.PlayerJoined -> handleJoin(cmd)
                        is MatchCommand.PlayerLeft -> handleLeft(cmd)
                        is MatchCommand.IntentSubmitted -> handleIntent(cmd)
                        is MatchCommand.BeatAckReceived -> handleBeatAck(cmd)
                        MatchCommand.BeatTimedOut -> forceAdvanceNow()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Log and continue — one bad command must not crash the actor
                    println("[MatchActor $matchId] Unhandled exception: ${e.message}")
                }
            }
        }

    // DelicateCoroutinesApi: Channel.isClosedForSend is delicate because the answer can go stale
    // the instant it is read. That is fine here and nowhere else — this is a liveness hint the
    // registry uses to reap dead matches, never a guard before a send(). post() still suspends on
    // send() and lets a ClosedSendChannelException surface if the race does happen.
    @OptIn(DelicateCoroutinesApi::class)
    fun isFinished(): Boolean = mailbox.isClosedForSend || job.isCompleted

    // ── Public API (WebSocket handlers post here; never access state directly) ─
    suspend fun post(cmd: MatchCommand) = mailbox.send(cmd)

    fun close() {
        beatGate.cancel()
        mailbox.close()
    }

    // ── Command handlers ────────────────────────────────────────────────────

    private suspend fun handleJoin(cmd: MatchCommand.PlayerJoined) {
        // Reconnection path: caller asks to resume a specific seat it previously held.
        val resumeSeat =
            cmd.reconnectSeat?.takeIf {
                it in 0 until config.seatCount && it !in humanSeats
            }
        val reconnected = resumeSeat != null
        val seat =
            resumeSeat ?: (0 until config.seatCount).firstOrNull { it !in humanSeats && it !in reconnectableSeats }
                // If every seat is either occupied or reserved-for-reconnect, fall back to any vacant seat.
                ?: (0 until config.seatCount).firstOrNull { it !in humanSeats }
        if (seat == null) {
            cmd.replyChannel.completeExceptionally(IllegalStateException("Match $matchId is full"))
            return
        }

        humanSeats[seat] = cmd.connectionId to cmd.session
        connToSeat[cmd.connectionId] = seat
        // The seat is now human-driven again: drop any bot stand-in and clear its reconnect reservation.
        botPolicies.remove(seat)
        reconnectableSeats.remove(seat)

        // The actor sends RoomJoined ITSELF (before any StateUpdate) so frame ordering is guaranteed by
        // the serial actor loop — never racing the routing coroutine. The reply only carries metadata.
        val result =
            PlayerJoinedResult(
                seat = seat,
                matchId = matchId,
                playerCount = humanSeats.size,
                reconnected = reconnected,
            )
        trySend(
            cmd.session,
            ServerMessage.RoomJoined(
                matchId = matchId,
                seq = nextSeq(),
                seat = seat,
                playerCount = humanSeats.size,
                reconnected = reconnected,
            ),
        )
        cmd.replyChannel.complete(result)

        when {
            // First time all seats are accounted for → start the match (vacant seats become bots).
            !started && humanSeats.size == config.seatCount -> startGameWithBots()
            // A reconnect (or a join into an already-running match) → resync THIS seat with current state
            // (sent AFTER the RoomJoined above), then keep any pending bot turns moving. Track 6: routes
            // through [forceAdvanceNow] so a returning client is never left staring at a beat the table
            // is quietly still pacing — the reconnect always gets the table moving NOW.
            started -> {
                sendStateTo(seat, emptyList())
                forceAdvanceNow()
            }
        }
    }

    private suspend fun handleLeft(cmd: MatchCommand.PlayerLeft) {
        val seat = connToSeat.remove(cmd.connectionId) ?: return
        // Only forget the seat if THIS connection still owns it (guards against a stale leave after a
        // reconnect handed the seat to a new connection).
        if (humanSeats[seat]?.first != cmd.connectionId) return
        humanSeats.remove(seat)
        // The seat becomes bot-driven AND eligible for reconnection by the same player.
        if (state.phase !is Phase.GameOver) {
            botPolicies.getOrPut(seat) { EasyPolicy(seed + seat * BOT_SEED_STRIDE) }
            reconnectableSeats.add(seat)
        }
        // If the actor seat is now a bot, drive bots forward and broadcast the result.
        forceAdvanceNow()
    }

    /** A seated human asked to release the currently-pending beat wait now (see [BeatAckGate]). */
    private suspend fun handleBeatAck(cmd: MatchCommand.BeatAckReceived) {
        if (cmd.connectionId !in connToSeat) return // not a seated human — ignore
        if (!beatGate.isPending) return // no beat pending — stray/late ack, ignore
        forceAdvanceNow()
    }

    /** Cancels any pending beat wait and drives the bot cascade immediately. */
    private suspend fun forceAdvanceNow() {
        beatGate.cancel()
        advanceAndBroadcast()
    }

    private suspend fun handleIntent(cmd: MatchCommand.IntentSubmitted) {
        val seat = connToSeat[cmd.connectionId]
        if (seat == null) {
            sendError(cmd.connectionId, cmd.clientSeq, "Not registered in any seat")
            return
        }

        val intent = cmd.wireIntent.toEngine()

        // Anti-cheat #1: a connection may only act for ITS OWN seat — never spoof another seat's intent.
        if (intent.actor.raw != seat) {
            sendError(cmd.connectionId, cmd.clientSeq, "Intent actor ${intent.actor.raw} does not match your seat $seat")
            return
        }

        // Anti-cheat #2: it must actually be this seat's turn to act.
        val expected = whoActsNext(state)
        if (expected == null || expected.raw != seat) {
            sendError(cmd.connectionId, cmd.clientSeq, "Not your turn (expected seat ${expected?.raw}, got $seat)")
            return
        }

        // Anti-cheat #3: the engine is the final authority — it re-validates full legality and rejects.
        when (val outcome = applyIntent(state, intent)) {
            is ApplyOutcome.Rejected -> {
                sendError(cmd.connectionId, cmd.clientSeq, outcome.reason)
            }
            is ApplyOutcome.Accepted -> {
                state = outcome.state
                broadcastStateToAll(outcome.events)
                // Track 6: pace the table for connected humans ONLY when the next actor is bot-driven —
                // otherwise advanceAndBroadcast() is a no-op anyway (it stops the moment a human is next),
                // so arming a wait would just burn a timer for nothing.
                if (humanSeats.isNotEmpty() && isMeaningfulBeat(outcome.events) && nextActorIsBot()) {
                    beatGate.arm()
                } else {
                    forceAdvanceNow()
                }
            }
        }
    }

    /** True when [whoActsNext] is a seat the actor itself drives (empty seat or a bot stand-in). */
    private fun nextActorIsBot(): Boolean {
        val who = whoActsNext(state) ?: return false
        return who.raw in botPolicies || who.raw !in humanSeats
    }

    // ── Game start / bot logic ───────────────────────────────────────────────

    private suspend fun startGameWithBots() {
        started = true
        broadcastStateToAll(emptyList())
        forceAdvanceNow()
    }

    /**
     * Auto-drives bot seats until the next actor is a human (or the game is over), then broadcasts.
     *
     * Returns the accumulated events so the caller could chain further work; the broadcast of the
     * post-bot state happens HERE so bot moves are never invisible to clients (the previous version
     * advanced bot state silently and only broadcast the pre-bot state — a correctness bug).
     */
    private suspend fun advanceAndBroadcast() {
        if (state.phase is Phase.GameOver) return
        val accumulated = ArrayList<GameEvent>()
        var advanced = false
        var limit = 10_000 // safety cap
        while (--limit > 0) {
            val who = whoActsNext(state) ?: break // game over
            val isBot = who.raw in botPolicies || who.raw !in humanSeats
            if (!isBot) break // next actor is a connected human — stop and let them act

            val policy = botPolicies.getOrPut(who.raw) { EasyPolicy(seed + who.raw * 37L) }
            val legal = legalIntents(state, who)
            if (legal.isEmpty()) break

            val intent = policy.decide(redact(state, who), legal)
            when (val outcome = applyIntent(state, intent)) {
                is ApplyOutcome.Rejected -> {
                    println("[MatchActor $matchId] Bot ${who.raw} produced illegal intent: ${outcome.reason}")
                    break
                }
                is ApplyOutcome.Accepted -> {
                    state = outcome.state
                    accumulated += outcome.events
                    advanced = true
                }
            }
            if (state.phase is Phase.GameOver) break
        }
        // Broadcast once after the bot run (avoid flooding one frame per bot move).
        if (advanced) broadcastStateToAll(accumulated)
    }

    // ── Broadcasting ─────────────────────────────────────────────────────────

    /**
     * Send each connected human their own redacted [WirePlayerView] + per-seat-projected events —
     * seats never see each other's face-down cards (redact) nor each other's secret drawn/kept
     * CardIds (toWireFor).
     */
    private suspend fun broadcastStateToAll(events: List<GameEvent>) {
        val seq = nextSeq()
        for (seat in humanSeats.keys.toList()) {
            sendStateTo(seat, events, seq)
        }

        // If game is over, also send GameOver message to everyone.
        if (state.phase is Phase.GameOver) {
            val winner = (state.phase as Phase.GameOver).winner
            val seq2 = nextSeq()
            val gameOver: ServerMessage =
                ServerMessage.GameOver(
                    matchId = matchId,
                    seq = seq2,
                    winnerSeat = winner.raw,
                )
            for ((_, pair) in humanSeats) {
                trySend(pair.second, gameOver)
            }
        }
    }

    /** Send a single seat its current redacted view + per-seat-projected events. */
    private suspend fun sendStateTo(
        seat: Int,
        events: List<GameEvent>,
        seq: Long = nextSeq(),
    ) {
        val session = humanSeats[seat]?.second ?: return
        val pid = PlayerId(seat)
        val view = redact(state, pid).toWire()
        val msg: ServerMessage =
            ServerMessage.StateUpdate(
                matchId = matchId,
                seq = seq,
                view = view,
                events = events.map { it.toWireFor(pid) },
            )
        trySend(session, msg)
    }

    private suspend fun sendError(
        connectionId: String,
        clientSeq: Long,
        reason: String,
    ) {
        val seat = connToSeat[connectionId] ?: return
        val session = humanSeats[seat]?.second ?: return
        val msg: ServerMessage =
            ServerMessage.Error(
                matchId = matchId,
                seq = nextSeq(),
                clientSeq = clientSeq,
                reason = reason,
            )
        trySend(session, msg)
    }

    private suspend fun trySend(
        session: WebSocketSession,
        msg: ServerMessage,
    ) {
        try {
            val json = KursiJson.encodeToString(msg)
            session.send(Frame.Text(json))
        } catch (_: Exception) {
            // Session closed or errored — ignore; PlayerLeft will clean up
        }
    }
}
