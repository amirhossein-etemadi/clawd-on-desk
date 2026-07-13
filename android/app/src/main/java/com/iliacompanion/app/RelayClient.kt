package com.iliacompanion.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface RelayListener {
    fun onState(state: CompanionState) {}
    fun onPresence(info: PresenceInfo) {}
    fun onConnectionChanged(connected: Boolean) {}
    /** Transient desktop event worth a phone notification (level-up,
     *  streak milestone, achievement, break reminder). */
    fun onNotify(event: String, title: String) {}
}

/**
 * WebSocket client for the Cloudflare relay (see cloud/src/worker.js).
 * Mirrors companion-cloud-sync.js's reconnect/backoff behavior on the
 * desktop side for consistency. Fails open: network hiccups just retry
 * quietly in the background and never crash the overlay.
 */
class RelayClient(private val relayUrl: String, private val syncCode: String) {
    private var webSocket: WebSocket? = null
    private var stopped = false
    private var reconnectDelayMs = RECONNECT_BASE_MS
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    var listener: RelayListener? = null
    var lastState: CompanionState? = null
        private set

    fun start() {
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        try { webSocket?.close(1000, "client stopping") } catch (_: Exception) {}
        webSocket = null
    }

    fun sendReaction(kind: String) {
        val ws = webSocket ?: return
        val msg = JSONObject().apply {
            put("type", "reaction")
            put("data", JSONObject().apply { put("kind", kind) })
        }
        try { ws.send(msg.toString()) } catch (_: Exception) { /* connection likely closing */ }
    }

    private fun buildWsUrl(): String? {
        return try {
            val trimmed = relayUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val wsBase = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
                else -> "wss://$trimmed"
            }
            val code = syncCode.trim().uppercase()
            if (code.isEmpty()) return null
            "$wsBase/ws?code=$code&role=phone"
        } catch (_: Exception) {
            null
        }
    }

    private fun connect() {
        if (stopped) return
        val url = buildWsUrl() ?: return
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectDelayMs = RECONNECT_BASE_MS
                handler.post { listener?.onConnectionChanged(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.post { listener?.onConnectionChanged(false) }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handler.post { listener?.onConnectionChanged(false) }
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val obj = JSONObject(text)
            when (obj.optString("type")) {
                "state" -> {
                    val state = CompanionState.fromJson(obj.getJSONObject("data"))
                    lastState = state
                    handler.post { listener?.onState(state) }
                }
                "presence" -> {
                    val data = obj.getJSONObject("data")
                    val info = PresenceInfo(
                        desktopOnline = data.optBoolean("desktopOnline", false),
                        phoneCount = data.optInt("phoneCount", 0)
                    )
                    handler.post { listener?.onPresence(info) }
                }
                "notify" -> {
                    val data = obj.getJSONObject("data")
                    val event = data.optString("event", "")
                    val title = data.optString("title", "")
                    if (title.isNotBlank()) {
                        handler.post { listener?.onNotify(event, title) }
                    }
                }
                // "pong" -- no-op, just confirms the relay is alive
            }
        } catch (_: Exception) {
            // malformed frame -- ignore, connection stays open
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        handler.postDelayed({ connect() }, reconnectDelayMs)
        reconnectDelayMs = (reconnectDelayMs * 1.6).toLong().coerceAtMost(RECONNECT_MAX_MS)
    }

    companion object {
        private const val RECONNECT_BASE_MS = 2000L
        private const val RECONNECT_MAX_MS = 60000L
    }
}
