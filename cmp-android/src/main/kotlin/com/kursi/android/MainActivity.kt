package com.kursi.android

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.kursi.core.prefs.AppPrefs
import com.kursi.gameservices.Achievement
import com.kursi.gameservices.AuthResult
import com.kursi.gameservices.GameServices
import com.kursi.gameservices.Leaderboard
import com.kursi.designsystem.audio.KursiSoundAndroid
import com.kursi.shared.KursiApp
import com.siddharth.kmp.feedback.FeedbackAndroid
import com.siddharth.kmp.feedback.NotificationChannelManager
import com.siddharth.kmp.feedback.NotificationPermission
import com.siddharth.kmp.feedback.NotificationPermissionState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appPrefs = AppPrefs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeedbackAndroid.install(applicationContext)
        KursiSoundAndroid.install(applicationContext)
        NotificationChannelManager.createChannels(this, KursiNotificationChannels.specs)
        updateNotificationPermissionState()
        scheduleInAppReview()
        PlayFeatures.checkForUpdate(this)
        installGameServices()
        enableEdgeToEdge()
        setContent {
            KursiApp()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionState()
    }

    private fun updateNotificationPermissionState() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationPermission.GRANTED
                } else {
                    NotificationPermission.NOT_ASKED
                }
            } else {
                NotificationPermission.GRANTED
            }
        NotificationPermissionState.update(permission)
    }

    /**
     * Game Center / Play Games supply IDENTITY and standing. They do NOT supply matchmaking: the
     * authoritative Ktor WebSocket server (`:server`, a Channel-actor per match) and the
     * `_kursi._tcp` LAN path still own every match. Nothing here opens a socket.
     *
     * Null from the factory means this build has no game services at all (F-Droid's noGms flavor),
     * so the whole block is skipped rather than stubbed.
     */
    private fun installGameServices() {
        val services = GameServicesFactory.create(this) ?: return
        lifecycleScope.launch {
            val player = (services.authenticate() as? AuthResult.Success)?.player ?: return@launch
            // Seed only a BLANK name. Overwriting a nameplate the player typed would be the
            // platform quietly taking over something the app already owns.
            if (appPrefs.playerName.isBlank()) appPrefs.playerName = player.displayName
            publishStandings(services)
        }
    }

    /**
     * Mirror the local ledgers onto the platform. Each source is a StateFlow of a running TOTAL,
     * which is why the achievement calls are absolute ([GameServices.setProgress]) rather than
     * relative — a re-emission must be a no-op, not a double count.
     */
    private suspend fun publishStandings(services: GameServices) =
        coroutineScope {
            launch {
                appPrefs.ledgerFlow.collect { ledger ->
                    services.submitScore(Leaderboard.CAREER_WINS, ledger.wins.toLong())
                    if (ledger.wins >= 1) services.unlock(Achievement.FIRST_WIN)
                    services.setProgress(Achievement.TEN_WINS, ledger.wins)
                    services.setProgress(Achievement.HUNDRED_GAMES, ledger.games)
                }
            }
            launch {
                appPrefs.dailyFlow.collect { daily ->
                    services.submitScore(Leaderboard.DAILY_BEST_STREAK, daily.bestStreak.toLong())
                    if (daily.bestStreak >= 7) services.unlock(Achievement.WEEK_STREAK)
                }
            }
            launch {
                appPrefs.gauntletFlow.collect { gauntlet ->
                    // clearedRung is 0-based with -1 for "none"; the board shows rungs cleared.
                    services.submitScore(Leaderboard.GAUNTLET_RUNG, (gauntlet.clearedRung + 1).toLong())
                }
            }
        }

    private fun scheduleInAppReview() {
        lifecycleScope.launch {
            appPrefs.ledgerFlow.collect { ledger ->
                if (ledger.wins >= 3 && appPrefs.shouldShowReview(BuildConfig.VERSION_NAME)) {
                    PlayFeatures.launchInAppReview(this@MainActivity, appPrefs)
                }
            }
        }
    }
}
