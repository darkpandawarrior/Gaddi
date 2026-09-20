@file:OptIn(ExperimentalForeignApi::class)

package com.kursi.gameservices

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.GameKit.GKAchievement
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLocalPlayer
import platform.GameKit.GKSavedGame
import platform.GameKit.fetchSavedGamesWithCompletionHandler
import platform.GameKit.saveGameData
import platform.GameKit.setAuthenticateHandler
import platform.UIKit.UIViewController
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The seam GameKit needs and a suspend function cannot supply on its own.
 *
 * `GKLocalPlayer.authenticateHandler` hands back a `UIViewController` that the APP must present —
 * GameKit will not present it for you, and if nobody does, authentication never completes and
 * never errors. It just hangs. So the host that owns the window has to be injectable, exactly as
 * `IosPaymentHost` does for `PKPaymentAuthorizationController`.
 *
 * [retainForAuthentication] exists for the same reason its payments counterpart does. GameKit
 * copies the authenticate block but nothing else holds this object, and an instance constructed
 * inside a suspend fun is collectable while the sign-in sheet is on screen. The host keeps a
 * strong reference for the lifetime of the flow.
 */
interface IosGameHost {
    /** The view controller to present Game Center UI from, or null if no window is up. */
    fun presentationViewController(): UIViewController?

    /** Hold [owner] strongly until authentication settles. Release is the host's business. */
    fun retainForAuthentication(owner: Any)
}

/**
 * Game Center: identity, leaderboards, achievements, saved games. NOT matchmaking — see
 * [GameServices] for why `GKMatchmaker` stays unused while the Ktor/Bonjour transport stays.
 */
class GameCenterServices(
    private val host: IosGameHost,
) : GameServices {
    private val _currentPlayer = MutableStateFlow<GamePlayer?>(null)
    override val currentPlayer: StateFlow<GamePlayer?> = _currentPlayer.asStateFlow()

    private val local get() = GKLocalPlayer.localPlayer

    /**
     * iOS never reports NOT_CONFIGURED: Game Center ids are author-chosen (no provisioning
     * sentinel exists to be unreplaced) and a missing Game Center entitlement surfaces as an
     * authentication failure, not as a device capability. So the only honest answers here are
     * "signed in" and "not signed in".
     */
    override val availability: GameServicesAvailability
        get() =
            if (local.isAuthenticated()) {
                GameServicesAvailability.AVAILABLE
            } else {
                GameServicesAvailability.SIGNED_OUT
            }

    override suspend fun authenticate(): AuthResult {
        _currentPlayer.value?.let { return AuthResult.Success(it) }
        if (local.isAuthenticated()) return AuthResult.Success(capture())

        host.retainForAuthentication(this)
        val ok =
            suspendCoroutine { cont ->
                // GameKit may invoke this handler again later (sign-out, fast user switch), so the
                // continuation is resumed at most once and subsequent calls only refresh the flow.
                var resumed = false
                local.setAuthenticateHandler { viewController: UIViewController?, _: NSError? ->
                    when {
                        viewController != null ->
                            host.presentationViewController()
                                ?.presentViewController(viewController, animated = true, completion = null)
                                // No window to present from: GameKit would otherwise hang forever
                                // with no error. Fail loudly instead.
                                ?: run {
                                    if (!resumed) { resumed = true; cont.resume(false) }
                                }

                        else -> {
                            val authed = local.isAuthenticated()
                            _currentPlayer.value = if (authed) capture() else null
                            if (!resumed) { resumed = true; cont.resume(authed) }
                        }
                    }
                }
            }
        return if (ok) AuthResult.Success(capture()) else AuthResult.Unavailable(GameServicesAvailability.SIGNED_OUT)
    }

    private fun capture(): GamePlayer =
        GamePlayer(
            id = local.gamePlayerID,
            // displayName is what Apple says to render (alias is the raw handle, and deprecated).
            // GKPlayer redeclares displayName nonnull, but K/N keeps GKBasePlayer's nullable type.
            displayName = local.displayName.orEmpty(),
        ).also { _currentPlayer.value = it }

    override suspend fun submitScore(
        board: Leaderboard,
        score: Long,
    ): Boolean {
        if (!local.isAuthenticated()) return false
        return suspendCoroutine { cont ->
            GKLeaderboard.submitScore(
                score = score,
                context = 0u,
                player = local,
                leaderboardIDs = listOf(board.gameCenterId),
            ) { error: NSError? -> cont.resume(error == null) }
        }
    }

    override suspend fun unlock(achievement: Achievement): Boolean = report(achievement, 100.0)

    override suspend fun setProgress(
        achievement: Achievement,
        steps: Int,
    ): Boolean {
        // Game Center models every achievement as a percentage, so an absolute step count maps
        // straight onto it — no read-modify-write, and re-reporting the same total is a no-op.
        val total = maxOf(1, achievement.totalSteps)
        return report(achievement, (100.0 * steps / total).coerceIn(0.0, 100.0))
    }

    private suspend fun report(
        achievement: Achievement,
        percent: Double,
    ): Boolean {
        if (!local.isAuthenticated()) return false
        val entry =
            GKAchievement(identifier = achievement.gameCenterId).apply {
                percentComplete = percent
                showsCompletionBanner = true
            }
        return suspendCoroutine { cont ->
            GKAchievement.reportAchievements(listOf(entry)) { error: NSError? -> cont.resume(error == null) }
        }
    }

    override suspend fun saveSnapshot(
        slot: SavedGameSlot,
        bytes: ByteArray,
    ): Boolean {
        if (!local.isAuthenticated()) return false
        return suspendCoroutine { cont ->
            local.saveGameData(bytes.toNSData(), withName = slot.platformName) { _, error ->
                cont.resume(error == null)
            }
        }
    }

    override suspend fun loadSnapshot(slot: SavedGameSlot): ByteArray? {
        if (!local.isAuthenticated()) return null
        val saved =
            suspendCoroutine { cont ->
                local.fetchSavedGamesWithCompletionHandler { games, _ ->
                    // Game Center keeps EVERY device's copy under the same name and hands back all
                    // of them; it does not merge. Newest-wins is the honest single-player rule here
                    // — the career ledger is monotonic, so the latest write is a superset.
                    // ponytail: newest-wins; swap for a real merge if a non-monotonic slot appears.
                    cont.resume(
                        games.orEmpty()
                            .filterIsInstance<GKSavedGame>()
                            .filter { it.name == slot.platformName }
                            .maxByOrNull { it.modificationDate?.timeIntervalSince1970 ?: 0.0 },
                    )
                }
            } ?: return null
        return suspendCoroutine { cont ->
            saved.loadDataWithCompletionHandler { data, _ -> cont.resume(data?.toByteArray()) }
        }
    }
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    }

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), this@toByteArray.bytes, this@toByteArray.length) } }
}
