package com.blackandblue.justshare.data.altersend

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [RelayTransport] backed by a Cloudflare Workers WebSocket.
 *
 * Wire framing over WebSocket:
 *   Each [sendBytes] call sends exactly one binary WebSocket frame.
 *   Each [readBytes] call dequeues exactly one binary frame.
 *
 *   Cloudflare Workers / Durable Objects allow messages up to 32 MiB; our
 *   maximum encrypted chunk is ≤ 4 MiB + crypto overhead, so we are well
 *   within limits.
 *
 * Backpressure:
 *   OkHttp's WebSocket sends are buffered internally; we rely on the existing
 *   FRAME_ACK / FRAME_NEED protocol above this layer for application-level
 *   backpressure, exactly as before.
 *
 * @param wsUrl    Full wss:// URL, e.g. wss://relay.justshare.app/v1/session/abc123?role=sender&token=...&expiry=...
 * @param client   Shared [OkHttpClient] instance (caller owns lifecycle).
 * @param connectTimeoutMs How long to block waiting for the WebSocket handshake.
 */
class CloudflareWebSocketTransport(
    private val wsUrl: String,
    private val client: OkHttpClient,
    private val connectTimeoutMs: Long = 10_000L
) : RelayTransport {

    // Sentinel pushed to the queue when the connection closes.
    private object EOF_SENTINEL

    private val queue    = LinkedBlockingQueue<Any>(/* unbounded */ )
    private val closed   = AtomicBoolean(false)
    private var webSocket: WebSocket? = null

    // ── Listener ──────────────────────────────────────────────────────────────

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            webSocket = ws
            synchronized(this@CloudflareWebSocketTransport) {
                (this@CloudflareWebSocketTransport as java.lang.Object).notifyAll()
            }
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            queue.put(bytes.toByteArray())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            // The relay DO never sends text frames; ignore.
        }

        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
            ws.close(1000, null)
            queue.put(EOF_SENTINEL)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            queue.put(t)   // Surface as IOException in readBytes()
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Opens the WebSocket and blocks until the connection is established or
     * [connectTimeoutMs] elapses.
     *
     * @throws IOException if the connection cannot be established in time.
     */
    fun connect() {
        val request = Request.Builder().url(wsUrl).build()
        client.newWebSocket(request, listener)

        // Wait for onOpen to set webSocket
        val deadline = System.currentTimeMillis() + connectTimeoutMs
        synchronized(this) {
            while (webSocket == null && !closed.get()) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                (this as java.lang.Object).wait(remaining)
            }
        }
        if (webSocket == null) {
            close()
            throw IOException("Cloudflare WebSocket connection timed out ($wsUrl)")
        }
    }

    // ── RelayTransport ────────────────────────────────────────────────────────

    override fun sendBytes(payload: ByteArray) {
        if (closed.get()) throw IOException("Transport is closed")
        val ws = webSocket ?: throw IOException("WebSocket not connected")
        val sent = ws.send(payload.toByteString())
        if (!sent) throw IOException("WebSocket send failed (buffer full or closed)")
    }

    override fun readBytes(): ByteArray {
        while (true) {
            val item = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                ?: if (closed.get()) throw EOFException("Transport closed")
                   else continue   // spurious timeout, retry

            return when (item) {
                is ByteArray     -> item
                EOF_SENTINEL     -> throw EOFException("WebSocket closed by peer")
                is Throwable     -> throw IOException("WebSocket error: ${item.message}", item)
                else             -> throw IOException("Unexpected queue item: $item")
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { webSocket?.close(1000, "Client closed") }
            queue.put(EOF_SENTINEL)  // Unblock any waiting readBytes()
            synchronized(this) { (this as java.lang.Object).notifyAll() }
        }
    }

    companion object {
        private const val POLL_TIMEOUT_MS = 5_000L

        /**
         * Build the full WebSocket URL for a Cloudflare session.
         *
         * @param baseUrl    Value of [BuildConfig.CF_RELAY_BASE_URL]
         * @param sessionId  32-char hex session id
         * @param role       "sender" or "receiver"
         * @param token      Short-lived HMAC token issued by the backend
         * @param expiry     Unix epoch seconds (token lifetime)
         * @param maxBytes   Server-reserved byte ceiling bound into the token
         */
        fun buildUrl(
            baseUrl:   String,
            sessionId: String,
            role:      String,
            token:     String,
            expiry:    Long,
            maxBytes:  Long
        ): String = "${baseUrl.trimEnd('/')}/v1/session/$sessionId" +
                    "?role=$role&token=$token&expiry=$expiry&limit=$maxBytes"
    }
}
