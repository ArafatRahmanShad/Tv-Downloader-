package com.example.data

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val savePath: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val speedFormatted: String = "0 KB/s",
    val etaFormatted: String = "--",
    val threadsCount: Int = 16,
    val activeConnections: Int = 0,
    val category: String = "General",
    val errorMessage: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
