package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.engine.TvDownloadHub

class TorrentService : Service() {

    private val binder = LocalBinder()
    private lateinit var hub: TvDownloadHub

    inner class LocalBinder : Binder() {
        fun getService(): TorrentService = this@TorrentService
    }

    override fun onCreate() {
        super.onCreate()
        hub = TvDownloadHub.getInstance(this)
        instance = this

        // Apply seeding settings like BiglyBT
        hub.torrentEngine.applySettings(
            maxConn = 200,
            downLimit = 0L, // 0 = unlimited
            upLimit = 0L
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val magnet = intent?.getStringExtra(EXTRA_MAGNET)
        if (!magnet.isNullOrBlank()) {
            addMagnet(magnet)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun addMagnet(magnetUri: String) {
        hub.torrentEngine.addMagnet(magnetUri) { torrent ->
            // Keep seeding after download - ratio 2.0
            sendNotificationToPhone("Torrent Complete: ${torrent.name}")
        }
    }

    fun pauseAll() = hub.torrentEngine.pauseAll()
    fun resumeAll() = hub.torrentEngine.resumeAll()

    private fun sendNotificationToPhone(message: String) {
        NotificationHelper.notifyDownloadComplete(this, "TV Torrent Engine", message)
    }

    companion object {
        const val EXTRA_MAGNET = "extra_magnet_uri"

        @Volatile
        private var instance: TorrentService? = null

        fun getInstance(): TorrentService? = instance

        fun startMagnet(context: Context, magnetUri: String) {
            val intent = Intent(context, TorrentService::class.java).apply {
                putExtra(EXTRA_MAGNET, magnetUri)
            }
            context.startService(intent)
        }
    }
}
