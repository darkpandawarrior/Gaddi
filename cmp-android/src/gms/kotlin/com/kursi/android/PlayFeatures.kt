package com.kursi.android

import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.kursi.core.prefs.AppPrefs

/** Gms-flavor implementation: Play Core in-app review + in-app update. Stubbed out in noGms. */
object PlayFeatures {
    /**
     * Only force an IMMEDIATE (blocking) update once the installed build is this far behind.
     * Google's own guidance is 30+ days for immediate updates; below that it is rude.
     */
    private const val IMMEDIATE_UPDATE_STALE_DAYS = 30

    fun launchInAppReview(
        activity: ComponentActivity,
        appPrefs: AppPrefs,
    ) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result).addOnCompleteListener {
                    appPrefs.markReviewShown(BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    fun checkForUpdate(activity: ComponentActivity) {
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val staleDays = info.clientVersionStalenessDays() ?: 0
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                staleDays >= IMMEDIATE_UPDATE_STALE_DAYS &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                manager.startUpdateFlow(
                    info,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }
        }
    }
}
