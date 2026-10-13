package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.TvDownloadHub
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SunsetOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteProgressItem(
    val id: String,
    val name: String,
    val status: String,
    val progress: Int,
    val speed: String,
    val downloaded: String,
    val total: String
)

@Composable
fun RemoteControlScreen(
    hub: TvDownloadHub,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localServerIp by hub.serverIp.collectAsState()
    val localTvId by hub.tvId.collectAsState()

    var tvId by remember { mutableStateOf(localTvId) }
    var tvIp by remember { mutableStateOf(localServerIp) }
    var link by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Private (Local WiFi)") }
    var threads by remember { mutableIntStateOf(16) }
    var isSending by remember { mutableStateOf(false) }

    var remoteList by remember { mutableStateOf<List<RemoteProgressItem>>(emptyList()) }
    var isConnectedToTv by remember { mutableStateOf(false) }

    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Polling loop for live progress from the TV server
    LaunchedEffect(tvIp) {
        while (isActive) {
            val targetHost = if (tvIp.contains(":")) tvIp else "$tvIp:8080"
            try {
                val req = Request.Builder()
                    .url("http://$targetHost/status")
                    .get()
                    .build()

                val resp = withContext(Dispatchers.IO) {
                    try {
                        client.newCall(req).execute()
                    } catch (_: Exception) {
                        null
                    }
                }

                if (resp != null && resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val downArr = json.optJSONArray("downloads") ?: org.json.JSONArray()
                    val torArr = json.optJSONArray("torrents") ?: org.json.JSONArray()

                    val items = mutableListOf<RemoteProgressItem>()
                    for (i in 0 until downArr.length()) {
                        val obj = downArr.getJSONObject(i)
                        items.add(
                            RemoteProgressItem(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", "File"),
                                status = obj.optString("status", ""),
                                progress = obj.optInt("progress", 0),
                                speed = obj.optString("speed", ""),
                                downloaded = obj.optString("downloaded", ""),
                                total = obj.optString("total", "")
                            )
                        )
                    }
                    for (i in 0 until torArr.length()) {
                        val obj = torArr.getJSONObject(i)
                        items.add(
                            RemoteProgressItem(
                                id = obj.optString("id", ""),
                                name = obj.optString("name", "Torrent"),
                                status = obj.optString("status", ""),
                                progress = obj.optInt("progress", 0),
                                speed = obj.optString("downSpeed", ""),
                                downloaded = "",
                                total = ""
                            )
                        )
                    }
                    remoteList = items
                    isConnectedToTv = true
                } else {
                    isConnectedToTv = false
                }
            } catch (_: Exception) {
                isConnectedToTv = false
            }
            delay(2000)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TV Downloader Remote",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Beam download links & magnets straight to your TV / NAS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isConnectedToTv) EmeraldGreen.copy(alpha = 0.15f) else Color(0xFF334155),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (isConnectedToTv) EmeraldGreen else Color(0xFF475569))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnectedToTv) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = null,
                            tint = if (isConnectedToTv) EmeraldGreen else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnectedToTv) "TV PAIRED" else "SEARCHING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnectedToTv) EmeraldGreen else Color.White
                        )
                    }
                }
            }
        }

        // Connection Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_connection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = tvId,
                            onValueChange = { tvId = it },
                            label = { Text("TV ID") },
                            leadingIcon = { Icon(Icons.Default.Tv, contentDescription = null, tint = NeonCyan) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remote_tv_id_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = SlateBorder
                            )
                        )

                        OutlinedTextField(
                            value = tvIp,
                            onValueChange = { tvIp = it },
                            label = { Text("TV IP / Host") },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = SunsetOrange) },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("remote_tv_ip_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SunsetOrange,
                                unfocusedBorderColor = SlateBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = mode.startsWith("Private"),
                                onClick = { mode = "Private (Local WiFi)" },
                                label = { Text("Private WiFi") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan
                                )
                            )
                            FilterChip(
                                selected = mode.startsWith("Global"),
                                onClick = { mode = "Global (Remote Beam)" },
                                label = { Text("Global Queue") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SunsetOrange.copy(alpha = 0.2f),
                                    selectedLabelColor = SunsetOrange
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                tvIp = localServerIp
                                tvId = localTvId
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("use_my_tv_btn")
                        ) {
                            Text("This TV IP")
                        }
                    }
                }
            }
        }

        // Link Input & Send Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_send_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paste Magnet / URL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        placeholder = { Text("https://... or magnet:?xt=urn:btih:...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("remote_link_input"),
                        trailingIcon = {
                            if (link.isNotBlank()) {
                                IconButton(onClick = { link = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Parallel thread selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Threads:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(8, 16, 32).forEach { t ->
                                FilterChip(
                                    selected = threads == t,
                                    onClick = { threads = t },
                                    label = { Text("${t}T") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (t == 32) SunsetOrange.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = if (t == 32) SunsetOrange else NeonCyan
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (link.isBlank()) {
                                Toast.makeText(context, "Please paste a link first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSending = true
                            scope.launch {
                                val targetHost = if (tvIp.contains(":")) tvIp else "$tvIp:8080"
                                val jsonBody = JSONObject().apply {
                                    put("link", link.trim())
                                    put("threads", threads)
                                    put("tvId", tvId)
                                }.toString()

                                try {
                                    val req = Request.Builder()
                                        .url("http://$targetHost/add")
                                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                                        .build()

                                    val resp = withContext(Dispatchers.IO) {
                                        client.newCall(req).execute()
                                    }

                                    if (resp.isSuccessful) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Sent to TV Downloader!", Toast.LENGTH_SHORT).show()
                                            link = ""
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            // Fallback local queuing
                                            hub.handleNewLink(link.trim(), threads)
                                            Toast.makeText(context, "Queued to Local Server!", Toast.LENGTH_SHORT).show()
                                            link = ""
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        // Fallback directly to local hub
                                        hub.handleNewLink(link.trim(), threads)
                                        Toast.makeText(context, "Directly Enqueued: ${e.message?.take(20)}", Toast.LENGTH_SHORT).show()
                                        link = ""
                                    }
                                } finally {
                                    isSending = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("remote_send_btn"),
                        enabled = !isSending && link.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0F172A))
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0F172A))
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SEND TO TV", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Live Progress List (TV Server)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Progress on TV (#$tvId)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${remoteList.size} Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (remoteList.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlateCard.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active downloads on TV", style = MaterialTheme.typography.bodyMedium)
                        Text("Send a link above to see live progress stream here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(remoteList) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_item_${item.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SlateBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = item.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonCyan,
                            trackColor = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.speed} • ${item.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch {
                                            val targetHost = if (tvIp.contains(":")) tvIp else "$tvIp:8080"
                                            val body = JSONObject().apply {
                                                put("id", item.id)
                                                put("action", "pause")
                                            }.toString()
                                            val req = Request.Builder()
                                                .url("http://$targetHost/action")
                                                .post(body.toRequestBody("application/json".toMediaType()))
                                                .build()
                                            withContext(Dispatchers.IO) {
                                                try { client.newCall(req).execute() } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch {
                                            val targetHost = if (tvIp.contains(":")) tvIp else "$tvIp:8080"
                                            val body = JSONObject().apply {
                                                put("id", item.id)
                                                put("action", "resume")
                                            }.toString()
                                            val req = Request.Builder()
                                                .url("http://$targetHost/action")
                                                .post(body.toRequestBody("application/json".toMediaType()))
                                                .build()
                                            withContext(Dispatchers.IO) {
                                                try { client.newCall(req).execute() } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
