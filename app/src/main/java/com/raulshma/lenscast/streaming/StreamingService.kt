package com.raulshma.lenscast.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.raulshma.lenscast.MainActivity
import com.raulshma.lenscast.R

class StreamingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "StreamingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startStreamingForeground(
                url = intent.getStringExtra(EXTRA_URL),
                includeAudio = intent.getBooleanExtra(EXTRA_AUDIO_ACTIVE, false)
            )
            ACTION_PAUSE -> pauseStreamingForeground(intent.getStringExtra(EXTRA_URL))
            ACTION_STOP -> stopStreamingForeground()
        }
        return START_STICKY
    }

    private fun startStreamingForeground(url: String?, includeAudio: Boolean) {
        val message = if (!url.isNullOrEmpty()) {
            if (includeAudio) getString(R.string.streaming_video_and_audio_to, url) else getString(R.string.streaming_to, url)
        } else {
            if (includeAudio) getString(R.string.streaming_camera_with_audio) else getString(R.string.streaming_camera)
        }
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                foregroundServiceTypes(includeAudio)
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Streaming foreground service started")
    }

    private fun stopStreamingForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Streaming foreground service stopped")
    }

    private fun pauseStreamingForeground(url: String?) {
        val message = if (!url.isNullOrEmpty()) getString(R.string.streaming_paused_url, url) else getString(R.string.streaming_paused)
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                foregroundServiceTypes(includeAudio = false)
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Streaming foreground service paused")
    }

    private fun buildNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_notification_title))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.streaming_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun foregroundServiceTypes(includeAudio: Boolean): Int {
        var serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (includeAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return serviceTypes
    }

    companion object {
        const val ACTION_START = "com.raulshma.lenscast.START_STREAMING"
        const val ACTION_PAUSE = "com.raulshma.lenscast.PAUSE_STREAMING"
        const val ACTION_STOP = "com.raulshma.lenscast.STOP_STREAMING"
        const val EXTRA_URL = "stream_url"
        const val EXTRA_AUDIO_ACTIVE = "stream_audio_active"
        private const val CHANNEL_ID = "streaming_channel"
        private const val NOTIFICATION_ID = 1002
        private const val TAG = "StreamingService"
    }
}
