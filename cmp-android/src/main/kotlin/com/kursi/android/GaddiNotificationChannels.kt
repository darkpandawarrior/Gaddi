package com.kursi.android

import android.app.NotificationManager
import com.siddharth.kmp.feedback.NotificationChannelSpec

/**
 * Gaddi's notification channels. Ids stay stable — they are referenced by the FCM service and created
 * at launch. Since :feedback was genericized (backlog #13), the app owns these specs, not the toolkit.
 */
object GaddiNotificationChannels {
    const val GAME_INVITES = "game_invites"
    const val SYSTEM = "system"

    val specs: List<NotificationChannelSpec> =
        listOf(
            NotificationChannelSpec(
                id = GAME_INVITES,
                name = "Game Invites",
                description = "Notifications for game invitations and match alerts",
                importance = NotificationManager.IMPORTANCE_HIGH,
                vibrate = true,
            ),
            NotificationChannelSpec(
                id = SYSTEM,
                name = "System",
                description = "General app notifications",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
}
