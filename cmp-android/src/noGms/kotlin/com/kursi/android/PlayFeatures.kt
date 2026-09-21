package com.kursi.android

import androidx.activity.ComponentActivity
import com.kursi.core.prefs.AppPrefs

/**
 * F-Droid (noGms) flavor: no Play Core dependency, so review/update prompts are no-ops.
 *
 * UnusedParameter: the parameters are the point. This object exists only to give the shared
 * `:cmp-android` call sites the same signature the gms flavor's PlayFeatures has, so the same
 * code compiles into an F-Droid build with the Play dependency absent. Dropping them would
 * break flavor parity, which is the one thing this file is for.
 */
@Suppress("UnusedParameter")
object PlayFeatures {
    fun launchInAppReview(
        activity: ComponentActivity,
        appPrefs: AppPrefs,
    ) = Unit

    fun checkForUpdate(activity: ComponentActivity) = Unit
}
