package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DownloadStatus
import com.example.data.TorrentStatus
import com.example.engine.TvDownloadHub
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.TorrentItemCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard

@Composable
fun DownloadsScreen(
    hub: TvDownloadHub,
    onOpenAddDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by hub.idmDownloader.downloads.collectAsState()
    val torrents by hub.torrentEngine.torrents.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }

    val filteredDownloads = when (selectedFilter) {
        "Active" -> downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING }
        "Completed" -> downloads.filter { it.status == DownloadStatus.COMPLETED }
        "Torrents" -> emptyList()
        else -> downloads
    }

    val filteredTorrents = when (selectedFilter) {
        "Active" -> torrents.filter { it.status == TorrentStatus.DOWNLOADING || it.status == TorrentStatus.SEEDING || it.status == TorrentStatus.FINDING_PEERS }
        "Completed" -> torrents.filter { it.status == TorrentStatus.COMPLETED || it.status == TorrentStatus.SEEDING }
        "Downloads" -> emptyList()
        else -> torrents
    }

    val totalItems = filteredDownloads.size + filteredTorrents.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloads & Torrents",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { hub.pauseAll() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("pause_all_btn")
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause All")
                }

                OutlinedButton(
                    onClick = { hub.resumeAll() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("resume_all_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume All")
                }

                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("downloads_add_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Link", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Active", "Completed", "Torrents").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == filter,
                        borderColor = SlateBorder,
                        selectedBorderColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("filter_chip_$filter")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (totalItems == 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SlateCard.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Items in $selectedFilter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Enqueue a download with 32 parallel threads or torrent magnet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDownloads) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { hub.idmDownloader.pauseDownload(item.id) },
                        onResume = { hub.idmDownloader.resumeDownload(item.id) },
                        onCancel = { hub.idmDownloader.cancelDownload(item.id) }
                    )
                }

                items(filteredTorrents) { item ->
                    TorrentItemCard(
                        item = item,
                        onPause = { hub.torrentEngine.pauseTorrent(item.id) },
                        onResume = { hub.torrentEngine.resumeTorrent(item.id) },
                        onCancel = { hub.torrentEngine.removeTorrent(item.id) }
                    )
                }
            }
        }
    }
}
