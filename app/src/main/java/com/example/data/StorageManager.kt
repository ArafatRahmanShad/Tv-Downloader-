package com.example.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageManager {
    private const val PREFS_NAME = "tv_downloader"
    private const val PREF_KEY = "download_path"
    private const val PREF_AUTO_SORT = "auto_sort_enabled"

    data class StorageItem(
        val name: String,
        val path: String,
        val freeSpace: String,
        val totalSpace: String,
        val freeBytes: Long,
        val totalBytes: Long,
        val isRemovable: Boolean
    )

    data class DownloadedFileItem(
        val name: String,
        val path: String,
        val sizeFormatted: String,
        val sizeBytes: Long,
        val lastModifiedFormatted: String,
        val category: String,
        val extension: String
    )

    fun getAvailableStorages(context: Context): List<StorageItem> {
        val list = mutableListOf<StorageItem>()

        // 1. Internal Storage
        try {
            val internalDir = Environment.getExternalStorageDirectory()
            if (internalDir != null && internalDir.exists()) {
                val free = internalDir.freeSpace
                val total = internalDir.totalSpace
                list.add(
                    StorageItem(
                        name = "Internal Storage",
                        path = internalDir.absolutePath,
                        freeSpace = formatBytes(free),
                        totalSpace = formatBytes(total),
                        freeBytes = free,
                        totalBytes = total,
                        isRemovable = false
                    )
                )
            }
        } catch (_: Exception) {
            val fallback = context.filesDir
            list.add(
                StorageItem(
                    name = "App Internal Storage",
                    path = fallback.absolutePath,
                    freeSpace = formatBytes(fallback.freeSpace),
                    totalSpace = formatBytes(fallback.totalSpace),
                    freeBytes = fallback.freeSpace,
                    totalBytes = fallback.totalSpace,
                    isRemovable = false
                )
            )
        }

        // 2. USB / SD Card / Removable External storage
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            externalDirs.forEach { file ->
                if (file != null && Environment.isExternalStorageRemovable(file)) {
                    val rootPath = file.absolutePath.split("/Android")[0]
                    val rootFile = File(rootPath)
                    if (rootFile.exists() && rootFile.canRead()) {
                        val free = rootFile.freeSpace
                        val total = rootFile.totalSpace
                        val label = if (rootFile.name.isNotBlank()) rootFile.name else "USB Drive"
                        list.add(
                            StorageItem(
                                name = "USB / Removable ($label)",
                                path = rootPath,
                                freeSpace = formatBytes(free),
                                totalSpace = formatBytes(total),
                                freeBytes = free,
                                totalBytes = total,
                                isRemovable = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Removable storage query fallback
        }

        // Ensure at least one storage entry is present
        if (list.isEmpty()) {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            list.add(
                StorageItem(
                    name = "Primary Storage",
                    path = dir.absolutePath,
                    freeSpace = formatBytes(dir.freeSpace),
                    totalSpace = formatBytes(dir.totalSpace),
                    freeBytes = dir.freeSpace,
                    totalBytes = dir.totalSpace,
                    isRemovable = false
                )
            )
        }

        return list
    }

    fun getCurrentDownloadDir(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPath = prefs.getString(PREF_KEY, null)
        val basePath = if (!savedPath.isNullOrBlank()) {
            savedPath
        } else {
            val defaultPublic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (defaultPublic != null && (defaultPublic.exists() || defaultPublic.mkdirs())) {
                defaultPublic.absolutePath
            } else {
                val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                appExternal?.absolutePath ?: context.filesDir.absolutePath
            }
        }

        return File(basePath).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun setDownloadDir(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, path)
            .apply()
    }

    fun isAutoSortEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_SORT, true)
    }

    fun setAutoSortEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AUTO_SORT, enabled)
            .apply()
    }

    fun autoSortFile(context: Context, file: File): File {
        if (!file.exists()) return file
        if (!isAutoSortEnabled(context)) return file

        val ext = file.extension.lowercase(Locale.ROOT)
        val fileName = file.name
        val isEpisode = fileName.contains(Regex("""(?i)s\d{1,2}e\d{1,2}|season|episode"""))

        val categoryFolderName = when (ext) {
            "mp4", "mkv", "avi", "mov", "webm", "flv", "ts" -> {
                if (isEpisode) "Series" else "Movies"
            }
            "mp3", "flac", "wav", "aac", "m4a", "ogg" -> "Music"
            "srt", "vtt", "sub", "ass" -> "Subtitles"
            "zip", "rar", "7z", "tar", "gz", "iso" -> "Archives"
            "pdf", "epub", "doc", "docx", "txt" -> "Documents"
            "apk" -> "Apps"
            else -> null
        }

        if (categoryFolderName == null) return file

        val baseDir = getCurrentDownloadDir(context)
        val destFolder = File(baseDir, categoryFolderName)
        if (!destFolder.exists()) {
            destFolder.mkdirs()
        }

        val destFile = File(destFolder, file.name)
        return if (file.renameTo(destFile)) {
            destFile
        } else {
            file
        }
    }

    fun getDownloadedFiles(context: Context): List<DownloadedFileItem> {
        val root = getCurrentDownloadDir(context)
        val result = mutableListOf<DownloadedFileItem>()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun scanDir(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    scanDir(file)
                } else if (!file.name.endsWith(".part") && !file.name.endsWith(".tmp")) {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    val category = when (ext) {
                        "mp4", "mkv", "avi", "mov", "webm", "flv", "ts" -> "Video"
                        "mp3", "flac", "wav", "aac", "m4a", "ogg" -> "Audio"
                        "srt", "vtt", "sub", "ass" -> "Subtitle"
                        "zip", "rar", "7z", "tar", "gz", "iso" -> "Archive"
                        "pdf", "epub", "doc", "docx", "txt" -> "Document"
                        "apk" -> "App"
                        else -> "File"
                    }
                    result.add(
                        DownloadedFileItem(
                            name = file.name,
                            path = file.absolutePath,
                            sizeFormatted = formatBytes(file.length()),
                            sizeBytes = file.length(),
                            lastModifiedFormatted = dateFormat.format(Date(file.lastModified())),
                            category = category,
                            extension = ext
                        )
                    )
                }
            }
        }

        scanDir(root)
        result.sortByDescending { it.sizeBytes }
        return result
    }

    fun deleteDownloadedFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}
