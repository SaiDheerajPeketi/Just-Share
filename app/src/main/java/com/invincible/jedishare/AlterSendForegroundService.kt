package com.invincible.jedishare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlterSendForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.invincible.jedishare.altersend.START_FOREGROUND"
        const val ACTION_STOP = "com.invincible.jedishare.altersend.STOP_FOREGROUND"
        private const val CHANNEL_ID = "altersend_transfer_channel"
        private const val NOTIFICATION_ID = 2407
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForegroundNotification()
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_altersend_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_altersend_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_altersend_title))
            .setContentText(getString(R.string.notif_altersend_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
