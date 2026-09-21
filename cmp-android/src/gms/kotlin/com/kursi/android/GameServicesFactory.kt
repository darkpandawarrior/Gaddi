package com.kursi.android

import android.app.Activity
import androidx.activity.ComponentActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.kursi.gameservices.Achievement
import com.kursi.gameservices.AuthResult
import com.kursi.gameservices.GamePlayer
import com.kursi.gameservices.GameServices
import com.kursi.gameservices.GameServicesAvailability
import com.kursi.gameservices.Leaderboard
import com.kursi.gameservices.PlayGamesProvisioning
import com.kursi.gameservices.SavedGameSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Why the Android implementation lives in the app's `gms` flavor and not in `:game-services`.
 *
 * `:game-services` is built for every target the shared UI is, and F-Droid's `noGms` flavor must
 * not link Play Services at all. Putting `play-services-games-v2` in the module's `androidMain`
 * would drag it into the F-Droid build, which is the one thing that flavor exists to prevent. So
 * the module owns the contract, the ids and the room-code boundary; the two flavors own the
 * Android body, exactly as `PlayFeatures` already does for in-app review and update.
 */
object GameServicesFactory {
    /** Null when this build genuinely has no Play Games — callers treat null as "hide the surface". */
    fun create(activity: ComponentActivity): GameServices? {
        if (!PlayGamesProvisioning.isConfigured(BuildConfig.DEBUG)) {
            // Sentinel still in place: initializing the SDK against the literal string throws, and
            // a half-wired surface is worse than no surface. Refuse, loudly and early.
            return UnconfiguredGameServices
        }
        PlayGamesSdk.initialize(activity)
        return PlayGamesServices(activity)
    }
}

/** Answers NOT_CONFIGURED to everything so the UI hides the surface instead of failing on tap. */
private object UnconfiguredGameServices : GameServices {
    override val availability = GameServicesAvailability.NOT_CONFIGURED
    override val currentPlayer: StateFlow<GamePlayer?> = MutableStateFlow(null).asStateFlow()

    override suspend fun authenticate() = AuthResult.Unavailable(GameServicesAvailability.NOT_CONFIGURED)

    override suspend fun submitScore(
        board: Leaderboard,
        score: Long,
    ) = false

    override suspend fun unlock(achievement: Achievement) = false

    override suspend fun setProgress(
        achievement: Achievement,
        steps: Int,
    ) = false

    override suspend fun saveSnapshot(
        slot: SavedGameSlot,
        bytes: ByteArray,
    ) = false

    override suspend fun loadSnapshot(slot: SavedGameSlot): ByteArray? = null
}

/**
 * Play Games Services v2: identity, leaderboards, achievements, snapshots. NOT matchmaking —
 * Gaddi's authoritative Ktor/WebSocket server and its `_kursi._tcp` LAN path keep the transport.
 */
private class PlayGamesServices(
    private val activity: Activity,
) : GameServices {
    private val _currentPlayer = MutableStateFlow<GamePlayer?>(null)
    override val currentPlayer: StateFlow<GamePlayer?> = _currentPlayer.asStateFlow()

    @Volatile private var authenticated = false

    override val availability: GameServicesAvailability
        get() =
            when {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) !=
                    ConnectionResult.SUCCESS -> GameServicesAvailability.UNSUPPORTED_DEVICE
                authenticated -> GameServicesAvailability.AVAILABLE
                else -> GameServicesAvailability.SIGNED_OUT
            }

    override suspend fun authenticate(): AuthResult {
        _currentPlayer.value?.let { return AuthResult.Success(it) }
        val signIn = PlayGames.getGamesSignInClient(activity)
        return runCatching {
            // isAuthenticated() first: it never shows UI. signIn() is only reached when the silent
            // path has already said no, so a returning player is never interrupted.
            val silent = signIn.isAuthenticated().await().isAuthenticated
            val ok = silent || signIn.signIn().await().isAuthenticated
            if (!ok) return@runCatching AuthResult.Unavailable(GameServicesAvailability.SIGNED_OUT)
            authenticated = true
            val player = PlayGames.getPlayersClient(activity).currentPlayer.await()
            AuthResult.Success(
                GamePlayer(id = player.playerId, displayName = player.displayName)
                    .also { _currentPlayer.value = it },
            )
        }.getOrElse { AuthResult.Unavailable(GameServicesAvailability.SIGNED_OUT) }
    }

    /**
     * Play-assigned ids (`CgkI…`) arrive as the `res/values/games-ids.xml` the Play Console hands
     * you; nothing in this repo can know them ahead of that paste. A missing resource is therefore
     * "not configured yet", and every call below returns false rather than posting to a board that
     * does not exist.
     *
     * ponytail: getIdentifier is the right tool for a resource that is genuinely optional at
     * build time — swap for generated constants if the ids ever become part of the repo.
     */
    @Suppress("DiscouragedApi")
    private fun resolveId(resourceName: String): String? =
        activity.resources
            .getIdentifier(resourceName, "string", activity.packageName)
            .takeIf { it != 0 }
            ?.let(activity::getString)

    override suspend fun submitScore(
        board: Leaderboard,
        score: Long,
    ): Boolean {
        if (!authenticated) return false
        val id = resolveId(board.androidResourceName) ?: return false
        return runCatching { PlayGames.getLeaderboardsClient(activity).submitScore(id, score) }.isSuccess
    }

    override suspend fun unlock(achievement: Achievement): Boolean {
        if (!authenticated) return false
        val id = resolveId(achievement.androidResourceName) ?: return false
        return runCatching { PlayGames.getAchievementsClient(activity).unlock(id) }.isSuccess
    }

    override suspend fun setProgress(
        achievement: Achievement,
        steps: Int,
    ): Boolean {
        if (!authenticated) return false
        val id = resolveId(achievement.androidResourceName) ?: return false
        // setSteps, not increment: this mirrors a running TOTAL, and increment() would add that
        // whole total again on every re-emission of the ledger StateFlow. Play Games also rejects
        // either call on a one-shot achievement — it errors rather than no-opping — so the step
        // count in the enum is what tells the two shapes apart without a round trip.
        return runCatching {
            val client = PlayGames.getAchievementsClient(activity)
            if (achievement.totalSteps <= 1) client.unlock(id) else client.setSteps(id, steps)
        }.isSuccess
    }

    override suspend fun saveSnapshot(
        slot: SavedGameSlot,
        bytes: ByteArray,
    ): Boolean {
        if (!authenticated) return false
        val client = PlayGames.getSnapshotsClient(activity)
        return runCatching {
            val snapshot = client.open(slot.platformName, true).await().data ?: return false
            snapshot.snapshotContents.writeBytes(bytes)
            client.commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE).await()
            true
        }.getOrElse { false }
    }

    override suspend fun loadSnapshot(slot: SavedGameSlot): ByteArray? {
        if (!authenticated) return null
        val client: SnapshotsClient = PlayGames.getSnapshotsClient(activity)
        return runCatching {
            val snapshot = client.open(slot.platformName, false).await().data ?: return null
            val bytes = snapshot.snapshotContents.readFully()
            // A snapshot left open blocks every later open() on the same name with a
            // SnapshotContentUnavailableException, on that device, until the process dies.
            client.discardAndClose(snapshot)
            bytes
        }.getOrNull()
    }
}
