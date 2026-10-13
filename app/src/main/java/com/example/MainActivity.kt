package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.engine.TvDownloadHub
import com.example.service.RemoteServerService
import com.example.ui.TvDownloaderApp
import com.example.ui.theme.TvDownloaderTheme

class MainActivity : ComponentActivity() {

    private lateinit var hub: TvDownloadHub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hub = TvDownloadHub.getInstance(this)

        // Automatically start the TV Remote Server on port 8080
        try {
            RemoteServerService.startServer(this)
        } catch (_: Exception) {}

        // Handle incoming intent if shared from browser or magnet link
        handleIncomingIntent(intent)

        setContent {
            TvDownloaderTheme {
                TvDownloaderApp(hub = hub)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data = intent.dataString

        if (Intent.ACTION_VIEW == action && !data.isNullOrBlank()) {
            hub.handleNewLink(data)
        } else if (Intent.ACTION_SEND == action && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                hub.handleNewLink(sharedText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hub.refreshNetworkInfo()
    }
}
