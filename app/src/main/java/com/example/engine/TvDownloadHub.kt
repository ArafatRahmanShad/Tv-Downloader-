package com.example.engine

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.DownloadItem
import com.example.data.StorageManager
import com.example.data.TorrentItem
import com.example.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class TvDownloadHub private constructor(private val context: Context) {

    val idmDownloader = IdmDownloader(context)
    val torrentEngine = TorrentEngine(context)

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _tvId = MutableStateFlow(getOrCreateTvId())
    val tvId: StateFlow<String> = _tvId.asStateFlow()

    private val _serverIp = MutableStateFlow(resolveIpAddress())
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    fun setServerRunning(running: Boolean) {
        _isServerRunning.value = running
        _serverIp.value = resolveIpAddress()
    }

    fun handleNewLink(link: String, threads: Int = 16): String {
        val trimmed = link.trim()
        return if (trimmed.startsWith("magnet:", ignoreCase = true) || trimmed.endsWith(".torrent", ignoreCase = true)) {
            torrentEngine.addMagnet(trimmed) { item ->
                NotificationHelper.notifyDownloadComplete(
                    context,
                    "Torrent Finished",
                    "${item.name} completed and is now seeding (ratio 2.0x)"
                )
            }
        } else {
            idmDownloader.enqueueDownload(trimmed, threads = threads) { file ->
                NotificationHelper.notifyDownloadComplete(
                    context,
                    "Download Complete",
                    "${file.name} saved in ${file.parentFile?.name ?: "Downloads"}"
                )
            }
        }
    }

    fun pauseAll() {
        idmDownloader.downloads.value.forEach { idmDownloader.pauseDownload(it.id) }
        torrentEngine.pauseAll()
    }

    fun resumeAll() {
        idmDownloader.downloads.value.forEach { idmDownloader.resumeDownload(it.id) }
        torrentEngine.resumeAll()
    }

    fun refreshNetworkInfo() {
        _serverIp.value = resolveIpAddress()
    }

    fun getStatusJson(): String {
        val root = JSONObject()
        root.put("tvId", _tvId.value)
        root.put("ip", _serverIp.value)
        root.put("port", 8080)
        root.put("serverRunning", _isServerRunning.value)

        // Downloads
        val downArr = JSONArray()
        for (item in idmDownloader.downloads.value) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.fileName)
            obj.put("status", item.status.name)
            obj.put("progress", (item.progress * 100).toInt())
            obj.put("speed", item.speedFormatted)
            obj.put("total", StorageManager.formatBytes(item.totalBytes))
            obj.put("downloaded", StorageManager.formatBytes(item.downloadedBytes))
            obj.put("threads", item.threadsCount)
            downArr.put(obj)
        }
        root.put("downloads", downArr)

        // Torrents
        val torArr = JSONArray()
        for (item in torrentEngine.torrents.value) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("status", item.status.name)
            obj.put("progress", (item.progress * 100).toInt())
            obj.put("downSpeed", item.downloadSpeedFormatted)
            obj.put("upSpeed", item.uploadSpeedFormatted)
            obj.put("peers", item.peersConnected)
            obj.put("seeders", item.seedersCount)
            obj.put("seedRatio", String.format(Locale.US, "%.2f", item.seedRatio))
            torArr.put(obj)
        }
        root.put("torrents", torArr)

        // Storage
        val storageArr = JSONArray()
        for (store in StorageManager.getAvailableStorages(context)) {
            val obj = JSONObject()
            obj.put("name", store.name)
            obj.put("free", store.freeSpace)
            obj.put("total", store.totalSpace)
            obj.put("path", store.path)
            storageArr.put(obj)
        }
        root.put("storage", storageArr)

        return root.toString()
    }

    private fun getOrCreateTvId(): String {
        val prefs = context.getSharedPreferences("tv_downloader", Context.MODE_PRIVATE)
        var id = prefs.getString("tv_id", null)
        if (id.isNullOrBlank()) {
            id = (1000 + (Math.random() * 9000).toInt()).toString()
            prefs.edit().putString("tv_id", id).apply()
        }
        return id
    }

    fun setCustomTvId(newId: String) {
        if (newId.isNotBlank()) {
            val prefs = context.getSharedPreferences("tv_downloader", Context.MODE_PRIVATE)
            prefs.edit().putString("tv_id", newId.trim()).apply()
            _tvId.value = newId.trim()
        }
    }

    private fun resolveIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback using WifiManager
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ip and 0xff,
                    ip shr 8 and 0xff,
                    ip shr 16 and 0xff,
                    ip shr 24 and 0xff
                )
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    companion object {
        @Volatile
        private var instance: TvDownloadHub? = null

        fun getInstance(context: Context): TvDownloadHub {
            return instance ?: synchronized(this) {
                instance ?: TvDownloadHub(context.applicationContext).also { instance = it }
            }
        }
    }
}
