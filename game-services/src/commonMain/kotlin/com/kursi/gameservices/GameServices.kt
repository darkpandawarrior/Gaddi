package com.kursi.gameservices

import kotlinx.coroutines.flow.StateFlow

/**
 * Why the capability flag is a four-state enum and not a Boolean.
 *
 * A Boolean collapses "the player is signed out" (recoverable — show a sign-in button), "this
 * device has no Game Center / Play Games at all" (hide the whole surface), and "nobody has pasted
 * the Play Console project id yet" (a DEVELOPER bug, not a player one) into one indistinguishable
 * `false`. Each of those wants a different UI, and the third must never render a working-looking
 * button: an unreplaced `__PROVISION_…__` sentinel makes [NOT_CONFIGURED] the answer, so the app
 * refuses the surface rather than drawing a leaderboard entry that fails when the player taps it.
 */
enum class GameServicesAvailability {
    /** Signed in; every call below is live. */
    AVAILABLE,

    /** Platform is present but the player is not signed in. Recoverable — offer sign-in. */
    SIGNED_OUT,

    /** No Game Center / no Play Games on this device or target. Hide the surface entirely. */
    UNSUPPORTED_DEVICE,

    /** A provisioning sentinel is still in place, or the id resource was never pasted in. */
    NOT_CONFIGURED,
}

/** The platform's identity for the local player: stable id plus the name it wants shown. */
data class GamePlayer(
    val id: String,
    val displayName: String,
)

sealed interface AuthResult {
    data class Success(
        val player: GamePlayer,
    ) : AuthResult

    /** Carries WHY, so the caller can tell "sign in" from "hide this" from "you forgot to provision". */
    data class Unavailable(
        val reason: GameServicesAvailability,
    ) : AuthResult
}

/**
 * IDENTITY, LEADERBOARDS, ACHIEVEMENTS and SAVED GAMES — deliberately NOT matchmaking.
 *
 * Gaddi already has working multiplayer: an authoritative Ktor WebSocket server with a
 * Channel-actor per match (`:server` RoomRegistry/MatchActor) and LAN play over Bonjour
 * `_kursi._tcp`. Game Center's `GKMatchmaker` and Play Games' real-time multiplayer would each
 * insist on owning the transport, the room lifecycle and the authority model — all three of which
 * this game already owns and tests. So this seam takes only what the platforms are genuinely
 * better at:
 *
 *  - **Identity** — a signed-in, cross-device player id and display name, fed into the existing
 *    lobby as a nameplate instead of a locally typed one. No socket, no room, is replaced.
 *  - **Leaderboards / achievements** — global standing the local `StatsLedger` cannot provide.
 *  - **Saved games** — cloud-backed progress, again over the platform's own store, not the match
 *    socket.
 *
 * The room code stays the server's: [RoomCode] formats it for the platform at the boundary.
 *
 * There is intentionally no `expect fun gameServices()`. jvm/desktop and wasm/browser genuinely
 * have no Game Center and no Play Games, and an empty actual there is the exact bug this design
 * removes — a seam that looks wired and silently does nothing. Those targets get the TYPES (so
 * shared UI can compile against a nullable [GameServices]) and no factory at all. The Android
 * factory lives in the app's `gms` flavor because F-Droid's `noGms` flavor must not link Play
 * Services; iOS builds [GameCenterServices] directly.
 */
interface GameServices {
    /** Cheap and synchronous: safe to read while composing. Never blocks on the network. */
    val availability: GameServicesAvailability

    /** Null until [authenticate] succeeds. Hot, safe to collect. */
    val currentPlayer: StateFlow<GamePlayer?>

    /**
     * Sign the local player in. Idempotent: a second call on an already-authenticated session
     * returns the cached player without presenting UI.
     */
    suspend fun authenticate(): AuthResult

    /** Post [score] to [board]. False when unavailable or the platform rejected the post. */
    suspend fun submitScore(
        board: Leaderboard,
        score: Long,
    ): Boolean

    /** Unlock a one-shot achievement. */
    suspend fun unlock(achievement: Achievement): Boolean

    /**
     * Set an incremental achievement's progress to an ABSOLUTE [steps] out of
     * [Achievement.totalSteps]. Absolute, not relative, because every caller in this app mirrors a
     * running total (`StatsLedger.wins`, `games`) rather than observing an event stream — a
     * relative `increment` would need a read-modify-write and would double-count on every
     * re-collection of the same StateFlow. Reaching [Achievement.totalSteps] unlocks it. Calling
     * this on a one-shot achievement unlocks it outright.
     */
    suspend fun setProgress(
        achievement: Achievement,
        steps: Int,
    ): Boolean

    /** Write a cloud snapshot under [slot]. */
    suspend fun saveSnapshot(
        slot: SavedGameSlot,
        bytes: ByteArray,
    ): Boolean

    /** Read a cloud snapshot, or null when absent/unavailable. */
    suspend fun loadSnapshot(slot: SavedGameSlot): ByteArray?
}
