package com.example.engine

import android.content.Context
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.StorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class IdmDownloader(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun enqueueDownload(
        url: String,
        threads: Int = 16,
        customName: String? = null,
        onComplete: ((File) -> Unit)? = null
    ): String {
        val id = UUID.randomUUID().toString().take(8)
        val initialName = customName?.takeIf { it.isNotBlank() }
            ?: extractFileNameFromUrl(url)

        val targetDir = StorageManager.getCurrentDownloadDir(context)
        val targetFile = File(targetDir, initialName)

        val item = DownloadItem(
            id = id,
            url = url,
            fileName = initialName,
            savePath = targetFile.absolutePath,
            status = DownloadStatus.QUEUED,
            threadsCount = threads.coerceIn(1, 32),
            activeConnections = 0
        )

        _downloads.value = _downloads.value + item
        startDownloadJob(id, onComplete)
        return id
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        updateItem(id) {
            it.copy(
                status = DownloadStatus.PAUSED,
                speedBytesPerSec = 0L,
                speedFormatted = "Paused",
                activeConnections = 0
            )
        }
    }

    fun resumeDownload(id: String) {
        val item = _downloads.value.find { it.id == id } ?: return
        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
            startDownloadJob(id, null)
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloads.value = _downloads.value.filterNot { it.id == id }

        // Clean up part files
        scope.launch {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("${id}_part_")) {
                    file.delete()
                }
            }
        }
    }

    fun retryDownload(id: String) {
        resumeDownload(id)
    }

    private fun startDownloadJob(id: String, onComplete: ((File) -> Unit)?) {
        val job = scope.launch {
            val item = _downloads.value.find { it.id == id } ?: return@launch
            updateItem(id) { it.copy(status = DownloadStatus.CONNECTING, errorMessage = null) }

            try {
                // Step 1: Probe server with HEAD request
                val headReq = Request.Builder()
                    .url(item.url)
                    .head()
                    .header("User-Agent", "TV-Downloader-NAS/1.0 (Android Leanback)")
                    .build()

                var contentLength = -1L
                var acceptRanges = false
                var resolvedName = item.fileName

                try {
                    client.newCall(headReq).execute().use { response ->
                        if (response.isSuccessful) {
                            contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                            val rangeHeader = response.header("Accept-Ranges")
                            acceptRanges = rangeHeader != null && rangeHeader.contains("bytes", ignoreCase = true)
                            val disposition = response.header("Content-Disposition")
                            if (disposition != null && disposition.contains("filename=")) {
                                val match = Regex("""filename=["']?([^"';]+)["']?""").find(disposition)
                                match?.groupValues?.get(1)?.let { resolvedName = it.trim() }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Head might be blocked by some servers, fallback to GET probing
                }

                val downloadDir = StorageManager.getCurrentDownloadDir(context)
                val destFile = File(downloadDir, resolvedName)

                updateItem(id) {
                    it.copy(
                        fileName = resolvedName,
                        savePath = destFile.absolutePath,
                        totalBytes = contentLength,
                        status = DownloadStatus.DOWNLOADING
                    )
                }

                // Speed monitoring loop
                val totalDownloadedAtomic = AtomicLong(item.downloadedBytes)
                val monitorJob = launch {
                    var lastBytes = totalDownloadedAtomic.get()
                    while (isActive) {
                        delay(1000)
                        val currentBytes = totalDownloadedAtomic.get()
                        val diff = (currentBytes - lastBytes).coerceAtLeast(0)
                        lastBytes = currentBytes

                        val speedStr = formatSpeed(diff)
                        val etaStr = if (contentLength > 0 && diff > 0) {
                            val remainingBytes = (contentLength - currentBytes).coerceAtLeast(0)
                            formatEta(remainingBytes / diff)
                        } else {
                            "--"
                        }

                        val progress = if (contentLength > 0) {
                            (currentBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        updateItem(id) {
                            it.copy(
                                downloadedBytes = currentBytes,
                                progress = progress,
                                speedBytesPerSec = diff,
                                speedFormatted = speedStr,
                                etaFormatted = etaStr
                            )
                        }
                    }
                }

                // Step 2: Download with IDM Multi-Threading or Direct Stream
                val threadCount = item.threadsCount.coerceIn(1, 32)
                if (acceptRanges && contentLength > 1024 * 1024 && threadCount > 1) {
                    // IDM Multi-threaded chunking (up to 32 threads)
                    val chunkSize = contentLength / threadCount
                    val partFiles = mutableListOf<File>()

                    updateItem(id) { it.copy(activeConnections = threadCount) }

                    val tasks = (0 until threadCount).map { i ->
                        async(Dispatchers.IO) {
                            val startByte = i * chunkSize
                            val endByte = if (i == threadCount - 1) contentLength - 1 else (i + 1) * chunkSize - 1
                            val partFile = File(context.cacheDir, "${id}_part_$i.part")
                            partFiles.add(partFile)

                            var currentPartDownloaded = if (partFile.exists()) partFile.length() else 0L
                            val chunkStart = startByte + currentPartDownloaded

                            if (chunkStart <= endByte) {
                                val chunkReq = Request.Builder()
                                    .url(item.url)
                                    .header("Range", "bytes=$chunkStart-$endByte")
                                    .header("User-Agent", "TV-Downloader-NAS/1.0 (Android Leanback)")
                                    .build()

                                client.newCall(chunkReq).execute().use { chunkResp ->
                                    if (!chunkResp.isSuccessful && chunkResp.code != 206) {
                                        throw Exception("Chunk $i HTTP error: ${chunkResp.code}")
                                    }
                                    val body = chunkResp.body ?: throw Exception("Empty body chunk $i")
                                    FileOutputStream(partFile, true).use { out ->
                                        val buffer = ByteArray(16 * 1024)
                                        val inputStream: InputStream = body.byteStream()
                                        var read: Int
                                        while (inputStream.read(buffer).also { read = it } != -1) {
                                            out.write(buffer, 0, read)
                                            totalDownloadedAtomic.addAndGet(read.toLong())
                                        }
                                    }
                                }
                            }
                        }
                    }

                    tasks.awaitAll()
                    monitorJob.cancel()

                    // Assemble all chunks into destination file
                    FileOutputStream(destFile).use { finalOut ->
                        for (partFile in partFiles) {
                            if (partFile.exists()) {
                                FileInputStream(partFile).use { partIn ->
                                    val buffer = ByteArray(32 * 1024)
                                    var read: Int
                                    while (partIn.read(buffer).also { read = it } != -1) {
                                        finalOut.write(buffer, 0, read)
                                    }
                                }
                                partFile.delete()
                            }
                        }
                    }
                } else {
                    // Single-connection stream
                    updateItem(id) { it.copy(activeConnections = 1) }

                    val req = Request.Builder()
                        .url(item.url)
                        .header("User-Agent", "TV-Downloader-NAS/1.0 (Android Leanback)")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("HTTP error: ${resp.code}")
                        val body = resp.body ?: throw Exception("Empty response body")
                        if (contentLength <= 0) {
                            contentLength = body.contentLength()
                        }

                        FileOutputStream(destFile).use { out ->
                            val buffer = ByteArray(16 * 1024)
                            val stream = body.byteStream()
                            var read: Int
                            while (stream.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                totalDownloadedAtomic.addAndGet(read.toLong())
                            }
                        }
                    }
                    monitorJob.cancel()
                }

                // Step 3: Auto-sort completed file into Movies/Series/Subtitles etc.
                val sortedFile = StorageManager.autoSortFile(context, destFile)

                updateItem(id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1.0f,
                        downloadedBytes = destFile.length().coerceAtLeast(contentLength),
                        totalBytes = destFile.length().coerceAtLeast(contentLength),
                        savePath = sortedFile.absolutePath,
                        speedFormatted = "Complete",
                        speedBytesPerSec = 0L,
                        etaFormatted = "0s",
                        activeConnections = 0
                    )
                }

                onComplete?.invoke(sortedFile)

            } catch (e: CancellationException) {
                // Was cancelled or paused
            } catch (e: Exception) {
                updateItem(id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Download error",
                        speedBytesPerSec = 0L,
                        speedFormatted = "Failed",
                        activeConnections = 0
                    )
                }
            } finally {
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val cleanUrl = url.substringBefore("?").substringBefore("#")
            val name = cleanUrl.substringAfterLast("/").trim()
            if (name.isNotBlank()) name else "download_${System.currentTimeMillis()}"
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.2f MB/s", mb)
        } else {
            String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
