package com.kursi.server

import com.kursi.engine.GameConfig
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** Room codes are read out loud over a call, so they stay short. */
private const val ROOM_CODE_LENGTH = 6

/**
 * Thread-safe registry of active rooms (invite-code → [MatchActor]).
 *
 * Room creation and join are the only entry points that modify the map.
 * All game mutations flow through [MatchActor]'s single-consumer mailbox.
 *
 * [scope] is the [Application]'s own coroutine scope so all actors are cancelled on shutdown/test-teardown.
 */
class RoomRegistry(
    private val scope: CoroutineScope,
) {
    private val rooms = ConcurrentHashMap<String, MatchActor>()
    private val seedCounter = AtomicLong(System.currentTimeMillis())

    /**
     * The public quick-match queue: a code → expected-seat-count map of rooms that are OPEN for any
     * waiting player to drop into. [quickMatch] pairs players by reusing the first open room of the
     * requested size, only creating a new one when none is waiting. Guarded by [queueLock] so the
     * check-then-fill is atomic across concurrent quick-match requests.
     */
    private val openQuickMatchRooms = ConcurrentHashMap<String, Int>() // code → seatCount
    private val queueLock = Any()

    /**
     * Creates a new PRIVATE room (host shares the short code out-of-band) and returns its invite code.
     */
    fun createRoom(playerCount: Int): String {
        val code = generateRoomCode()
        newActor(code, playerCount)
        return code
    }

    /**
     * Public quick-match: returns the code of a room of the requested size that is WAITING for players,
     * creating a fresh one only if none is open. The caller then joins it like any other room; once the
     * room fills (the [MatchActor] starts the game) it is removed from the open queue by [markFilled].
     *
     * Atomic so two simultaneous quick-match requests for the same size land in the SAME room (and pair)
     * rather than each spawning its own half-empty room.
     */
    fun quickMatch(playerCount: Int): String =
        synchronized(queueLock) {
            val waiting = openQuickMatchRooms.entries.firstOrNull { it.value == playerCount }?.key
            if (waiting != null && rooms.containsKey(waiting)) return waiting
            val code = generateRoomCode()
            newActor(code, playerCount)
            openQuickMatchRooms[code] = playerCount
            code
        }

    /** Called by the routing layer once a quick-match room fills, so later requests open a new room. */
    fun markFilled(roomCode: String) {
        openQuickMatchRooms.remove(roomCode.uppercase())
    }

    private fun newActor(
        code: String,
        playerCount: Int,
    ): MatchActor {
        val config = GameConfig.forPlayers(playerCount)
        val seed = seedCounter.getAndIncrement()
        val actor = MatchActor(matchId = code, config = config, seed = seed, scope = scope)
        rooms[code] = actor
        return actor
    }

    /**
     * Joins a player to an existing room. Returns the [MatchActor] for the room,
     * or null if the room does not exist.
     */
    fun findRoom(roomCode: String): MatchActor? = rooms[roomCode.uppercase()]

    /** Remove a finished/empty room from the registry. */
    fun removeRoom(roomCode: String) {
        val key = roomCode.uppercase()
        rooms.remove(key)?.close()
        openQuickMatchRooms.remove(key)
    }

    fun activeRoomCount(): Int = rooms.size

    private fun generateRoomCode(): String {
        // Uppercase alphanumeric, no ambiguous chars like 0/O/I/1 — these get read aloud.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        var code: String
        do {
            code = (1..ROOM_CODE_LENGTH).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
        } while (rooms.containsKey(code))
        return code
    }
}
