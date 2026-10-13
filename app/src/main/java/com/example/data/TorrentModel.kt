package com.example.data

enum class TorrentStatus {
    FINDING_PEERS,
    DOWNLOADING,
    SEEDING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class TorrentItem(
    val id: String,
    val magnetUri: String,
    val name: String,
    val infoHash: String,
    val status: TorrentStatus = TorrentStatus.FINDING_PEERS,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val progress: Float = 0f,
    val downloadSpeedFormatted: String = "0 KB/s",
    val uploadSpeedFormatted: String = "0 KB/s",
    val downloadSpeedBytes: Long = 0L,
    val uploadSpeedBytes: Long = 0L,
    val peersConnected: Int = 0,
    val seedersCount: Int = 0,
    val seedRatio: Float = 0f,
    val targetRatio: Float = 2.0f,
    val errorMessage: String? = null,
    val savePath: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
)
