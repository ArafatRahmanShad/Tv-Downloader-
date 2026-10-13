package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.data.StorageManager
import com.example.engine.TvDownloadHub
import java.io.File

class DownloadService : Service() {

    private val binder = LocalBinder()
    private lateinit var hub: TvDownloadHub

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        hub = TvDownloadHub.getInstance(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val threads = intent?.getIntExtra(EXTRA_THREADS, 16) ?: 16
        if (!url.isNullOrBlank()) {
            addDownload(url, threads)
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

    fun addDownload(url: String, threads: Int = 16) {
        val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank { "download_${System.currentTimeMillis()}" }
        val saveFile = File(StorageManager.getCurrentDownloadDir(this), fileName)

        hub.idmDownloader.enqueueDownload(
            url = url,
            threads = threads,
            customName = fileName,
            onComplete = { resultFile ->
                autoSortFile(resultFile)
                sendNotificationToPhone("Download Complete: ${resultFile.name}")
            }
        )
    }

    private fun autoSortFile(file: File) {
        StorageManager.autoSortFile(this, file)
    }

    private fun sendNotificationToPhone(message: String) {
        NotificationHelper.notifyDownloadComplete(this, "TV Download Status", message)
    }

    companion object {
        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_THREADS = "extra_threads"

        @Volatile
        private var instance: DownloadService? = null

        fun getInstance(): DownloadService? = instance

        fun startDownload(context: Context, url: String, threads: Int = 16) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_THREADS, threads)
            }
            context.startService(intent)
        }
    }
}
