package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {
    const val CHANNEL_SERVER_ID = "tv_nas_server_channel"
    const val CHANNEL_DOWNLOADS_ID = "tv_nas_downloads_channel"
    const val NOTIFICATION_ID_SERVER = 1001
    const val NOTIFICATION_ID_DOWNLOAD = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serverChannel = NotificationChannel(
                CHANNEL_SERVER_ID,
                "TV NAS Server & Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of the running TV Downloader HTTP server and listener"
            }

            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS_ID,
                "TV Downloads & Torrents",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download and torrent progress notifications"
            }

            notificationManager.createNotificationChannel(serverChannel)
            notificationManager.createNotificationChannel(downloadsChannel)
        }
    }

    fun buildServerNotification(context: Context, tvId: String, ipAddress: String): Notification {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVER_ID)
            .setContentTitle("TV Downloader Server Active")
            .setContentText("TV ID: #$tvId • Web: http://$ipAddress:8080")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyDownloadComplete(context: Context, title: String, message: String) {
        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify((System.currentTimeMillis() % 10000).toInt() + 2000, notification)
    }
}
