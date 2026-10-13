package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.data.StorageManager
import com.example.data.TorrentItem
import com.example.data.TorrentStatus
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SunsetOrange

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonCyan.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download icon",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.threadsCount} Threads • ${item.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(statusText = item.status.name, statusColor = getDownloadStatusColor(item.status))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NeonCyan,
                trackColor = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Telemetry & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val downloadedStr = StorageManager.formatBytes(item.downloadedBytes)
                    val totalStr = if (item.totalBytes > 0) StorageManager.formatBytes(item.totalBytes) else "--"
                    val percentInt = (item.progress * 100).toInt()

                    Text(
                        text = "$downloadedStr / $totalStr ($percentInt%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${item.speedFormatted} • ETA: ${item.etaFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (item.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> {
                            FilledTonalIconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("pause_btn_${item.id}"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color(0xFF334155),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("resume_btn_${item.id}"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = NeonCyan.copy(alpha = 0.2f),
                                    contentColor = NeonCyan
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.FAILED -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("retry_btn_${item.id}"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = SunsetOrange.copy(alpha = 0.2f),
                                    contentColor = SunsetOrange
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(18.dp))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            // Completed icon indicator
                        }
                        DownloadStatus.QUEUED -> {}
                    }

                    FilledTonalIconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("delete_btn_${item.id}"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CrimsonRed.copy(alpha = 0.15f),
                            contentColor = CrimsonRed
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Cancel/Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TorrentItemCard(
    item: TorrentItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("torrent_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (item.status == TorrentStatus.SEEDING) Icons.Default.Upload else Icons.Default.Download,
                                contentDescription = "Torrent icon",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Peers: ${item.peersConnected} • Seeds: ${item.seedersCount} • Ratio: ${String.format(java.util.Locale.US, "%.2f", item.seedRatio)} / 2.0x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(statusText = item.status.name, statusColor = getTorrentStatusColor(item.status))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (item.status == TorrentStatus.SEEDING) EmeraldGreen else NeonCyan,
                trackColor = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Telemetry & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val downloadedStr = StorageManager.formatBytes(item.downloadedBytes)
                    val totalStr = StorageManager.formatBytes(item.totalBytes)
                    val percentInt = (item.progress * 100).toInt()

                    Text(
                        text = if (item.status == TorrentStatus.SEEDING) "Seeding to Peers (Ratio: ${String.format(java.util.Locale.US, "%.2f", item.seedRatio)})" else "$downloadedStr / $totalStr ($percentInt%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (item.status == TorrentStatus.SEEDING) "Up: ${item.uploadSpeedFormatted}" else "Down: ${item.downloadSpeedFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.status == TorrentStatus.DOWNLOADING || item.status == TorrentStatus.SEEDING) {
                        FilledTonalIconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("pause_torrent_${item.id}"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(18.dp))
                        }
                    } else if (item.status == TorrentStatus.PAUSED) {
                        FilledTonalIconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("resume_torrent_${item.id}"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = EmeraldGreen.copy(alpha = 0.2f),
                                contentColor = EmeraldGreen
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(18.dp))
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("delete_torrent_${item.id}"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CrimsonRed.copy(alpha = 0.15f),
                            contentColor = CrimsonRed
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(statusText: String, statusColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = statusColor.copy(alpha = 0.15f),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.6f)))
    ) {
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun getDownloadStatusColor(status: DownloadStatus): Color {
    return when (status) {
        DownloadStatus.DOWNLOADING -> NeonCyan
        DownloadStatus.COMPLETED -> EmeraldGreen
        DownloadStatus.CONNECTING -> SunsetOrange
        DownloadStatus.PAUSED -> Color(0xFF94A3B8)
        DownloadStatus.FAILED -> CrimsonRed
        DownloadStatus.QUEUED -> Color(0xFF64748B)
    }
}

fun getTorrentStatusColor(status: TorrentStatus): Color {
    return when (status) {
        TorrentStatus.DOWNLOADING -> NeonCyan
        TorrentStatus.SEEDING -> EmeraldGreen
        TorrentStatus.COMPLETED -> EmeraldGreen
        TorrentStatus.FINDING_PEERS -> SunsetOrange
        TorrentStatus.PAUSED -> Color(0xFF94A3B8)
        TorrentStatus.ERROR -> CrimsonRed
    }
}
