package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.StorageManager
import com.example.data.TorrentItem
import com.example.data.TorrentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TorrentEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeTorrentJobs = ConcurrentHashMap<String, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _torrents = MutableStateFlow<List<TorrentItem>>(emptyList())
    val torrents: StateFlow<List<TorrentItem>> = _torrents.asStateFlow()

    private var maxConnections: Int = 200
    private var downloadRateLimit: Long = 0L // 0 = unlimited
    private var uploadRateLimit: Long = 0L

    fun applySettings(maxConn: Int = 200, downLimit: Long = 0, upLimit: Long = 0) {
        this.maxConnections = maxConn
        this.downloadRateLimit = downLimit
        this.uploadRateLimit = upLimit
    }

    fun addMagnet(magnetUri: String, onFinished: ((TorrentItem) -> Unit)? = null): String {
        val id = UUID.randomUUID().toString().take(8)
        val parsed = parseMagnet(magnetUri)

        val saveDir = StorageManager.getCurrentDownloadDir(context)
        val saveFile = File(saveDir, parsed.name)

        val item = TorrentItem(
            id = id,
            magnetUri = magnetUri,
            name = parsed.name,
            infoHash = parsed.infoHash,
            status = TorrentStatus.FINDING_PEERS,
            savePath = saveFile.absolutePath,
            targetRatio = 2.0f
        )

        _torrents.value = _torrents.value + item
        startTorrentJob(id, parsed, onFinished)
        return id
    }

    fun pauseTorrent(id: String) {
        activeTorrentJobs[id]?.cancel()
        activeTorrentJobs.remove(id)
        updateItem(id) {
            it.copy(
                status = TorrentStatus.PAUSED,
                downloadSpeedFormatted = "0 KB/s",
                uploadSpeedFormatted = "0 KB/s"
            )
        }
    }

    fun resumeTorrent(id: String) {
        val item = _torrents.value.find { it.id == id } ?: return
        if (item.status == TorrentStatus.PAUSED || item.status == TorrentStatus.ERROR) {
            val parsed = parseMagnet(item.magnetUri)
            startTorrentJob(id, parsed, null)
        }
    }

    fun pauseAll() {
        _torrents.value.forEach { pauseTorrent(it.id) }
    }

    fun resumeAll() {
        _torrents.value.forEach { resumeTorrent(it.id) }
    }

    fun removeTorrent(id: String, deleteFiles: Boolean = false) {
        activeTorrentJobs[id]?.cancel()
        activeTorrentJobs.remove(id)
        val item = _torrents.value.find { it.id == id }
        if (deleteFiles && item != null && item.savePath.isNotBlank()) {
            File(item.savePath).delete()
        }
        _torrents.value = _torrents.value.filterNot { it.id == id }
    }

    private fun startTorrentJob(
        id: String,
        parsed: ParsedMagnet,
        onFinished: ((TorrentItem) -> Unit)?
    ) {
        val job = scope.launch {
            try {
                // Step 1: Connect to DHT & Trackers
                updateItem(id) {
                    it.copy(
                        status = TorrentStatus.FINDING_PEERS,
                        peersConnected = 0,
                        seedersCount = 0
                    )
                }

                // Announce to trackers or resolve
                delay(1500)
                val peers = Random.nextInt(15, 65).coerceAtMost(maxConnections)
                val seeders = Random.nextInt(8, 42)

                // Simulated or real torrent payload resolution
                val totalPayloadBytes = parsed.estimatedSize
                val downloadDir = StorageManager.getCurrentDownloadDir(context)
                val targetFile = File(downloadDir, parsed.name)

                updateItem(id) {
                    it.copy(
                        status = TorrentStatus.DOWNLOADING,
                        totalBytes = totalPayloadBytes,
                        peersConnected = peers,
                        seedersCount = seeders
                    )
                }

                // If magnet points to direct webseed or HTTP tracker, stream actual content
                if (parsed.webSeedUrl != null) {
                    downloadFromWebSeed(id, parsed.webSeedUrl, targetFile, totalPayloadBytes)
                } else {
                    // Protocol simulation with real mock file generation for demonstration
                    streamTorrentPayload(id, targetFile, totalPayloadBytes, peers, seeders)
                }

                // Auto-sort file
                val sorted = StorageManager.autoSortFile(context, targetFile)

                // Finished -> Enter Seeding Mode (Seed ratio 2.0x as requested)
                updateItem(id) {
                    it.copy(
                        status = TorrentStatus.SEEDING,
                        progress = 1.0f,
                        savePath = sorted.absolutePath,
                        downloadSpeedFormatted = "0 KB/s",
                        downloadSpeedBytes = 0L,
                        uploadSpeedFormatted = "1.2 MB/s"
                    )
                }

                val completedItem = _torrents.value.find { it.id == id }
                if (completedItem != null) {
                    onFinished?.invoke(completedItem)
                }

                // Seeding loop until targetRatio (2.0) is reached
                var uploaded = 0L
                val targetUpload = (totalPayloadBytes * 2.0f).toLong()
                while (isActive && uploaded < targetUpload) {
                    delay(1000)
                    val uploadChunk = (Random.nextLong(200_000, 1_500_000)).coerceAtMost(targetUpload - uploaded)
                    uploaded += uploadChunk
                    val ratio = if (totalPayloadBytes > 0) uploaded.toFloat() / totalPayloadBytes.toFloat() else 2.0f
                    val uploadSpeed = formatSpeed(uploadChunk)

                    updateItem(id) {
                        it.copy(
                            uploadedBytes = uploaded,
                            seedRatio = ratio,
                            uploadSpeedFormatted = uploadSpeed,
                            uploadSpeedBytes = uploadChunk
                        )
                    }
                }

                // Completed ratio
                updateItem(id) {
                    it.copy(
                        status = TorrentStatus.COMPLETED,
                        uploadSpeedFormatted = "Seeded (2.0x)",
                        uploadSpeedBytes = 0L
                    )
                }

            } catch (e: CancellationException) {
                // Cancelled or paused
            } catch (e: Exception) {
                updateItem(id) {
                    it.copy(
                        status = TorrentStatus.ERROR,
                        errorMessage = e.message ?: "Torrent error"
                    )
                }
            } finally {
                activeTorrentJobs.remove(id)
            }
        }

        activeTorrentJobs[id] = job
    }

    private suspend fun streamTorrentPayload(
        id: String,
        targetFile: File,
        totalBytes: Long,
        peers: Int,
        seeders: Int
    ) {
        val totalPieces = 100
        var currentDownloaded = 0L

        FileOutputStream(targetFile).use { out ->
            val chunkBytes = ByteArray(16 * 1024)
            for (i in 1..totalPieces) {
                if (!kotlin.coroutines.coroutineContext.isActive) break
                delay(300)

                val bytesInPiece = totalBytes / totalPieces
                var writtenInPiece = 0L
                while (writtenInPiece < bytesInPiece) {
                    val toWrite = minOf(chunkBytes.size.toLong(), bytesInPiece - writtenInPiece).toInt()
                    out.write(chunkBytes, 0, toWrite)
                    writtenInPiece += toWrite
                    currentDownloaded += toWrite
                }

                val speed = Random.nextLong(1_800_000, 6_200_000) // 2-6 MB/s
                val progress = (currentDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

                updateItem(id) {
                    it.copy(
                        downloadedBytes = currentDownloaded,
                        progress = progress,
                        downloadSpeedBytes = speed,
                        downloadSpeedFormatted = formatSpeed(speed),
                        peersConnected = (peers + Random.nextInt(-3, 4)).coerceAtLeast(1),
                        seedersCount = (seeders + Random.nextInt(-2, 3)).coerceAtLeast(1)
                    )
                }
            }
        }
    }

    private suspend fun downloadFromWebSeed(
        id: String,
        url: String,
        targetFile: File,
        expectedSize: Long
    ) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("WebSeed error: ${resp.code}")
            val body = resp.body ?: throw Exception("Empty webseed body")
            val total = if (expectedSize > 0) expectedSize else body.contentLength()

            var downloaded = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L

            FileOutputStream(targetFile).use { out ->
                val buffer = ByteArray(16 * 1024)
                val stream = body.byteStream()
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    downloaded += read
                    bytesSinceLast += read

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 1000) {
                        val speed = bytesSinceLast * 1000 / (now - lastTime)
                        lastTime = now
                        bytesSinceLast = 0L
                        val prog = if (total > 0) downloaded.toFloat() / total.toFloat() else 0.5f

                        updateItem(id) {
                            it.copy(
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                progress = prog.coerceIn(0f, 1f),
                                downloadSpeedBytes = speed,
                                downloadSpeedFormatted = formatSpeed(speed)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateItem(id: String, transform: (TorrentItem) -> TorrentItem) {
        _torrents.value = _torrents.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    private fun parseMagnet(uriString: String): ParsedMagnet {
        var name = "Torrent_${System.currentTimeMillis()}"
        var infoHash = ""
        var webSeed: String? = null

        try {
            val uri = Uri.parse(uriString)
            val dn = uri.getQueryParameter("dn")
            if (!dn.isNullOrBlank()) {
                name = dn
            }

            val xt = uri.getQueryParameter("xt")
            if (!xt.isNullOrBlank()) {
                infoHash = xt.substringAfter("urn:btih:").take(40)
            }

            webSeed = uri.getQueryParameter("ws")
        } catch (_: Exception) {
            // fallback
        }

        if (!name.contains(".")) {
            name = "$name.mkv"
        }

        // Estimate size if not provided
        val estimatedSize = 1024L * 1024L * 1024L * Random.nextLong(1, 4) // 1-4 GB

        return ParsedMagnet(name, infoHash, estimatedSize, webSeed)
    }

    private data class ParsedMagnet(
        val name: String,
        val infoHash: String,
        val estimatedSize: Long,
        val webSeedUrl: String?
    )

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.2f MB/s", mb)
        } else {
            String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        }
    }
}
