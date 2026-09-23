package com.kursi.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GaddiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""

        val channelId =
            when (message.data["type"]) {
                "game_invite" -> GaddiNotificationChannels.GAME_INVITES
                else -> GaddiNotificationChannels.SYSTEM
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        // Upload token to backend when one exists.
    }
}
