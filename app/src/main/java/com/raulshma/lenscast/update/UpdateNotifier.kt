package com.raulshma.lenscast.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.raulshma.lenscast.MainActivity

class UpdateNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "update_channel"
        private const val NOTIFICATION_ID = 1003
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showUpdateAvailable(version: String) {
        createChannel()
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, "app-settings")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(com.raulshma.lenscast.R.string.update_notification_title))
            .setContentText(context.getString(com.raulshma.lenscast.R.string.update_notification_text, version))
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(com.raulshma.lenscast.R.string.update_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(com.raulshma.lenscast.R.string.update_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
