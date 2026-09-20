package com.kursi.android

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.kursi.core.prefs.AppPrefs
import com.kursi.designsystem.audio.KursiSoundAndroid
import com.kursi.shared.KursiApp
import com.siddharth.kmp.feedback.FeedbackAndroid
import com.siddharth.kmp.feedback.NotificationChannelManager
import com.siddharth.kmp.feedback.NotificationPermission
import com.siddharth.kmp.feedback.NotificationPermissionState
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
