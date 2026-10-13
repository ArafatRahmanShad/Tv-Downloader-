package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.data.StorageManager
import com.example.engine.TvDownloadHub
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException

class RemoteServerService : Service() {

    private var httpServer: NanoHTTPD? = null
    private lateinit var hub: TvDownloadHub

    override fun onCreate() {
        super.onCreate()
        hub = TvDownloadHub.getInstance(this)
        instance = this

        val tvId = hub.tvId.value
        val ip = hub.serverIp.value

        // Start Foreground Service
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVER,
            NotificationHelper.buildServerNotification(this, tvId, ip)
        )

        // MODE A: Private - Local HTTP Server on port 8080
        startLocalServer()

        hub.setServerRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocalServer()
        hub.setServerRunning(false)
        if (instance == this) {
            instance = null
        }
    }

    private fun startLocalServer() {
        try {
            httpServer = object : NanoHTTPD(8080) {
                override fun serve(session: IHTTPSession): Response {
                    val uri = session.uri
                    val method = session.method

                    // Handle CORS Preflight
                    if (method == Method.OPTIONS) {
                        val resp = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                        addCorsHeaders(resp)
                        return resp
                    }

                    // 1. POST /add - Enqueue link (from remote phone or web panel)
                    if (method == Method.POST && uri == "/add") {
                        val bodyMap = HashMap<String, String>()
                        try {
                            session.parseBody(bodyMap)
                        } catch (_: Exception) {}

                        var link = bodyMap["postData"] ?: ""
                        var threads = 16

                        if (link.startsWith("{")) {
                            try {
                                val json = JSONObject(link)
                                link = json.optString("link", "")
                                threads = json.optInt("threads", 16)
                            } catch (_: Exception) {}
                        } else {
                            val parms = session.parameters
                            if (link.isBlank()) {
                                link = parms["link"]?.firstOrNull() ?: ""
                            }
                            threads = parms["threads"]?.firstOrNull()?.toIntOrNull() ?: 16
                        }

                        if (link.isNotBlank()) {
                            val id = handleNewLink(link, threads)
                            val jsonResp = JSONObject().apply {
                                put("status", "ok")
                                put("id", id)
                                put("message", "Link enqueued to TV")
                            }
                            val resp = newFixedLengthResponse(Response.Status.OK, "application/json", jsonResp.toString())
                            addCorsHeaders(resp)
                            return resp
                        } else {
                            val resp = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\": \"Empty link\"}")
                            addCorsHeaders(resp)
                            return resp
                        }
                    }

                    // 2. GET /status - Real-time JSON telemetry
                    if (method == Method.GET && uri == "/status") {
                        val json = hub.getStatusJson()
                        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                        addCorsHeaders(resp)
                        return resp
                    }

                    // 3. POST /action - Pause, Resume, Cancel
                    if (method == Method.POST && uri == "/action") {
                        val bodyMap = HashMap<String, String>()
                        try {
                            session.parseBody(bodyMap)
                        } catch (_: Exception) {}

                        val postData = bodyMap["postData"] ?: ""
                        var action = ""
                        var id = ""

                        if (postData.startsWith("{")) {
                            try {
                                val json = JSONObject(postData)
                                action = json.optString("action", "")
                                id = json.optString("id", "")
                            } catch (_: Exception) {}
                        } else {
                            val parms = session.parameters
                            action = parms["action"]?.firstOrNull() ?: ""
                            id = parms["id"]?.firstOrNull() ?: ""
                        }

                        when (action.lowercase()) {
                            "pause" -> {
                                hub.idmDownloader.pauseDownload(id)
                                hub.torrentEngine.pauseTorrent(id)
                            }
                            "resume" -> {
                                hub.idmDownloader.resumeDownload(id)
                                hub.torrentEngine.resumeTorrent(id)
                            }
                            "cancel" -> {
                                hub.idmDownloader.cancelDownload(id)
                                hub.torrentEngine.removeTorrent(id)
                            }
                            "pause_all" -> hub.pauseAll()
                            "resume_all" -> hub.resumeAll()
                        }

                        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"ok\"}")
                        addCorsHeaders(resp)
                        return resp
                    }

                    // 4. GET /files - List downloaded files
                    if (method == Method.GET && uri == "/files") {
                        val files = StorageManager.getDownloadedFiles(this@RemoteServerService)
                        val arr = org.json.JSONArray()
                        files.forEach { file ->
                            arr.put(JSONObject().apply {
                                put("name", file.name)
                                put("size", file.sizeFormatted)
                                put("category", file.category)
                                put("date", file.lastModifiedFormatted)
                            })
                        }
                        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", arr.toString())
                        addCorsHeaders(resp)
                        return resp
                    }

                    // 5. GET / - Responsive Web Control Panel
                    val html = getWebPanelHtml()
                    val resp = newFixedLengthResponse(Response.Status.OK, "text/html", html)
                    addCorsHeaders(resp)
                    return resp
                }
            }
            httpServer?.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun addCorsHeaders(response: NanoHTTPD.Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    private fun stopLocalServer() {
        httpServer?.stop()
        httpServer = null
    }

    private fun handleNewLink(link: String, threads: Int): String {
        return hub.handleNewLink(link, threads)
    }

    private fun getWebPanelHtml(): String {
        val tvId = hub.tvId.value
        val ip = hub.serverIp.value

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>TV Downloader & NAS Web Panel</title>
                <style>
                    :root {
                        --bg-base: #0B1120;
                        --bg-surface: #1E293B;
                        --bg-card: #141E33;
                        --accent-cyan: #38BDF8;
                        --accent-blue: #3B82F6;
                        --accent-green: #10B981;
                        --text-primary: #F8FAFC;
                        --text-secondary: #94A3B8;
                        --border: #334155;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
                    body { background: var(--bg-base); color: var(--text-primary); padding: 16px; min-height: 100vh; }
                    .container { max-width: 900px; margin: 0 auto; }
                    header { display: flex; justify-content: space-between; align-items: center; padding: 16px; background: var(--bg-surface); border-radius: 12px; border: 1px solid var(--border); margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
                    .logo-title { display: flex; align-items: center; gap: 12px; }
                    .badge { background: rgba(56, 189, 248, 0.15); color: var(--accent-cyan); padding: 4px 10px; border-radius: 20px; font-weight: 600; font-size: 13px; border: 1px solid var(--accent-cyan); }
                    .card { background: var(--bg-surface); border-radius: 12px; border: 1px solid var(--border); padding: 20px; margin-bottom: 20px; }
                    h2 { font-size: 18px; margin-bottom: 12px; color: var(--accent-cyan); display: flex; align-items: center; gap: 8px; }
                    textarea { width: 100%; height: 90px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; color: var(--text-primary); padding: 12px; font-size: 14px; resize: vertical; margin-bottom: 12px; }
                    .input-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
                    select, button { padding: 12px 18px; border-radius: 8px; border: none; font-size: 14px; font-weight: 600; cursor: pointer; }
                    select { background: var(--bg-card); color: var(--text-primary); border: 1px solid var(--border); }
                    button.btn-primary { background: linear-gradient(135deg, #0EA5E9, #2563EB); color: white; flex-grow: 1; transition: 0.2s; }
                    button.btn-primary:hover { opacity: 0.9; }
                    button.btn-action { padding: 6px 12px; font-size: 12px; border-radius: 6px; background: var(--bg-card); color: var(--text-secondary); border: 1px solid var(--border); }
                    button.btn-action:hover { color: var(--text-primary); border-color: var(--accent-cyan); }
                    .progress-bar-container { background: #0F172A; border-radius: 6px; height: 8px; overflow: hidden; margin: 8px 0; }
                    .progress-bar { background: linear-gradient(90deg, #38BDF8, #3B82F6); height: 100%; width: 0%; transition: width 0.3s; }
                    .item-card { background: var(--bg-card); border-radius: 8px; border: 1px solid var(--border); padding: 14px; margin-bottom: 12px; }
                    .item-header { display: flex; justify-content: space-between; font-weight: 600; font-size: 14px; margin-bottom: 6px; }
                    .item-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-secondary); }
                    .empty-state { text-align: center; padding: 24px; color: var(--text-secondary); font-size: 14px; }
                    .status-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 20px; }
                    .status-metric { background: var(--bg-surface); padding: 14px; border-radius: 8px; border: 1px solid var(--border); }
                    .metric-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; }
                    .metric-val { font-size: 18px; font-weight: 700; color: var(--text-primary); }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <div class="logo-title">
                            <div>
                                <h1>TV Downloader & NAS</h1>
                                <p style="font-size: 12px; color: var(--text-secondary);">Direct 32-Thread Parallel & Torrent Server</p>
                            </div>
                        </div>
                        <div style="display: flex; gap: 8px; align-items: center;">
                            <span class="badge">TV ID: #$tvId</span>
                            <span class="badge" style="border-color: var(--accent-green); color: var(--accent-green);">ONLINE :8080</span>
                        </div>
                    </header>

                    <div class="status-grid" id="storageGrid">
                        <!-- Storage items dynamic -->
                    </div>

                    <div class="card">
                        <h2>+ Send Download to TV</h2>
                        <textarea id="urlInput" placeholder="Paste HTTP/HTTPS Download URL or Magnet Link (magnet:?xt=urn:btih:...)"></textarea>
                        <div class="input-row">
                            <select id="threadsSelect">
                                <option value="32">32 Threads (IDM Max Speed)</option>
                                <option value="16" selected>16 Threads (High Speed)</option>
                                <option value="8">8 Threads (Normal)</option>
                                <option value="4">4 Threads</option>
                                <option value="1">1 Thread (Single Stream)</option>
                            </select>
                            <button class="btn-primary" onclick="sendDownload()">BEAM TO TV DOWNLOADER</button>
                        </div>
                    </div>

                    <div class="card">
                        <div style="display:flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                            <h2>Active Downloads & Torrents</h2>
                            <div style="display: flex; gap: 8px;">
                                <button class="btn-action" onclick="sendGlobalAction('pause_all')">Pause All</button>
                                <button class="btn-action" onclick="sendGlobalAction('resume_all')">Resume All</button>
                            </div>
                        </div>
                        <div id="downloadsList">
                            <div class="empty-state">No active downloads running</div>
                        </div>
                    </div>
                </div>

                <script>
                    async function fetchStatus() {
                        try {
                            const res = await fetch('/status');
                            const data = await res.json();
                            renderStatus(data);
                        } catch (e) {
                            console.error(e);
                        }
                    }

                    function renderStatus(data) {
                        // Render storage
                        const storeContainer = document.getElementById('storageGrid');
                        if (data.storage && data.storage.length > 0) {
                            storeContainer.innerHTML = data.storage.map(s => `
                                <div class="status-metric">
                                    <div class="metric-label">${'$'}{s.name}</div>
                                    <div class="metric-val">${'$'}{s.free} Free</div>
                                    <div style="font-size: 11px; color: #64748B; margin-top: 4px;">Total: ${'$'}{s.total}</div>
                                </div>
                            `).join('');
                        }

                        // Render active downloads
                        const list = document.getElementById('downloadsList');
                        const allItems = [...(data.downloads || []), ...(data.torrents || [])];
                        if (allItems.length === 0) {
                            list.innerHTML = '<div class="empty-state">No downloads in queue. Paste a URL or magnet above!</div>';
                            return;
                        }

                        list.innerHTML = allItems.map(item => `
                            <div class="item-card">
                                <div class="item-header">
                                    <span style="overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 75%;">${'$'}{item.name}</span>
                                    <span style="color: #38BDF8;">${'$'}{item.status}</span>
                                </div>
                                <div class="progress-bar-container">
                                    <div class="progress-bar" style="width: ${'$'}{item.progress}%"></div>
                                </div>
                                <div class="item-meta">
                                    <span>${'$'}{item.speed || item.downSpeed || ''} • ${'$'}{item.progress}% (${'$'}{item.downloaded || ''} / ${'$'}{item.total || ''})</span>
                                    <div style="display: flex; gap: 6px;">
                                        <button class="btn-action" onclick="itemAction('${'$'}{item.id}', 'pause')">Pause</button>
                                        <button class="btn-action" onclick="itemAction('${'$'}{item.id}', 'resume')">Resume</button>
                                        <button class="btn-action" onclick="itemAction('${'$'}{item.id}', 'cancel')" style="color: #F87171;">Cancel</button>
                                    </div>
                                </div>
                            </div>
                        `).join('');
                    }

                    async function sendDownload() {
                        const input = document.getElementById('urlInput');
                        const link = input.value.trim();
                        if (!link) return alert('Please enter a valid link');
                        const threads = document.getElementById('threadsSelect').value;

                        try {
                            const res = await fetch('/add', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ link: link, threads: parseInt(threads) })
                            });
                            if (res.ok) {
                                input.value = '';
                                fetchStatus();
                            } else {
                                alert('Error sending link to TV');
                            }
                        } catch (e) {
                            alert('Network error sending link: ' + e);
                        }
                    }

                    async function itemAction(id, action) {
                        await fetch('/action', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id: id, action: action })
                        });
                        fetchStatus();
                    }

                    async function sendGlobalAction(action) {
                        await fetch('/action', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: action })
                        });
                        fetchStatus();
                    }

                    setInterval(fetchStatus, 2000);
                    fetchStatus();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        @Volatile
        private var instance: RemoteServerService? = null

        fun isRunning(): Boolean = instance != null

        fun startServer(context: Context) {
            val intent = Intent(context, RemoteServerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopServer(context: Context) {
            val intent = Intent(context, RemoteServerService::class.java)
            context.stopService(intent)
        }
    }
}
