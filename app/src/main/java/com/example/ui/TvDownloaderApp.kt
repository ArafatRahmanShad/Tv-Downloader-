package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.TvDownloadHub
import com.example.ui.components.AddDownloadDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.RemoteControlScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageScreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SunsetOrange

enum class NavTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Tv),
    DOWNLOADS("Queue", Icons.Default.Download),
    STORAGE("Storage", Icons.Default.Folder),
    REMOTE("Remote", Icons.Default.Cast),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvDownloaderApp(hub: TvDownloadHub) {
    var selectedTab by remember { mutableStateOf(NavTab.DASHBOARD) }
    var showAddDialog by remember { mutableStateOf(false) }

    val isServerRunning by hub.isServerRunning.collectAsState()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720

    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { link, threads ->
                hub.handleNewLink(link, threads)
            }
        )
    }

    if (isWideScreen) {
        // TV & Tablet Landscape Layout with Navigation Rail
        Row(modifier = Modifier.fillMaxSize().background(SlateBackground)) {
            NavigationRail(
                containerColor = SlateSurface,
                contentColor = Color.White,
                header = {
                    Surface(
                        shape = CircleShape,
                        color = NeonCyan.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 12.dp).size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = NeonCyan)
                        }
                    }
                },
                modifier = Modifier.testTag("tv_nav_rail")
            ) {
                NavTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF0F172A),
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                    )
                }
            }

            Scaffold(
                containerColor = SlateBackground,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TV Downloader & NAS",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isServerRunning) EmeraldGreen.copy(alpha = 0.2f) else SunsetOrange.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isServerRunning) "SERVER ONLINE" else "OFFLINE",
                                        color = if (isServerRunning) EmeraldGreen else SunsetOrange,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("topbar_add_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Download", tint = NeonCyan)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateSurface)
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (selectedTab) {
                        NavTab.DASHBOARD -> DashboardScreen(hub = hub, onOpenAddDialog = { showAddDialog = true })
                        NavTab.DOWNLOADS -> DownloadsScreen(hub = hub, onOpenAddDialog = { showAddDialog = true })
                        NavTab.STORAGE -> StorageScreen()
                        NavTab.REMOTE -> RemoteControlScreen(hub = hub)
                        NavTab.SETTINGS -> SettingsScreen(hub = hub)
                    }
                }
            }
        }
    } else {
        // Mobile / Compact Screen Layout with Bottom Navigation Bar
        Scaffold(
            containerColor = SlateBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TV Downloader",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isServerRunning) EmeraldGreen.copy(alpha = 0.15f) else SunsetOrange.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isServerRunning) ":8080" else "PAUSED",
                                    color = if (isServerRunning) EmeraldGreen else SunsetOrange,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("topbar_add_btn_mobile")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = NeonCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateSurface)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = SlateSurface,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("mobile_bottom_bar")
                ) {
                    NavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF0F172A),
                                selectedTextColor = NeonCyan,
                                indicatorColor = NeonCyan,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_bottom_${tab.name.lowercase()}")
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == NavTab.DASHBOARD || selectedTab == NavTab.DOWNLOADS) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF0F172A),
                        modifier = Modifier.testTag("fab_add_download")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Download")
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (selectedTab) {
                    NavTab.DASHBOARD -> DashboardScreen(hub = hub, onOpenAddDialog = { showAddDialog = true })
                    NavTab.DOWNLOADS -> DownloadsScreen(hub = hub, onOpenAddDialog = { showAddDialog = true })
                    NavTab.STORAGE -> StorageScreen()
                    NavTab.REMOTE -> RemoteControlScreen(hub = hub)
                    NavTab.SETTINGS -> SettingsScreen(hub = hub)
                }
            }
        }
    }
}
