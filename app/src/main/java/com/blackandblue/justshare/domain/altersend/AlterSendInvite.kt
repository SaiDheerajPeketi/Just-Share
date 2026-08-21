package com.blackandblue.justshare.domain.altersend

import android.util.Base64
import org.json.JSONObject

enum class AlterSendInviteMode {
    Direct,
    Relay,
    Hybrid,
    /** Relay via Cloudflare Workers + Durable Objects — no IPs or ports embedded. */
    Cloudflare
}

data class AlterSendInvite(
    val host: String,
    val port: Int,
    val topicHex: String,
    val mode: AlterSendInviteMode = AlterSendInviteMode.Direct,
    val relaySessionId: String? = null,
    val relayHost: String? = null,
    val relayPort: Int? = null,
    // ── Cloudflare-specific fields (only set when mode == Cloudflare) ──────
    /** Cloudflare session id (32 hex chars). */
    val cfSessionId: String? = null,
    /** Full wss:// URL of the relay Worker endpoint (no IP/port). */
    val cfRelayUrl: String? = null,
    /** Unix epoch seconds at which the token expires. */
    val cfExpiry: Long? = null,
    /** HMAC-SHA256 token authorising this device to join the session. */
    val cfToken: String? = null
) {
    fun encode(): String = when (mode) {
        AlterSendInviteMode.Direct -> "$DIRECT_PREFIX$host:$port:$topicHex"
        AlterSendInviteMode.Relay -> {
            val sessionId = requireNotNull(relaySessionId) { "Relay invite requires a session id" }
            "$RELAY_PREFIX$host:$port:$sessionId:$topicHex"
        }
        AlterSendInviteMode.Hybrid -> {
            val sessionId = requireNotNull(relaySessionId) { "Hybrid invite requires a relay session id" }
            val fallbackHost = requireNotNull(relayHost) { "Hybrid invite requires a relay host" }
            val fallbackPort = requireNotNull(relayPort) { "Hybrid invite requires a relay port" }
            "$HYBRID_PREFIX$host:$port:$fallbackHost:$fallbackPort:$sessionId:$topicHex"
        }
        AlterSendInviteMode.Cloudflare -> {
            // Encode as base64(JSON) so the QR / link stays a single opaque token.
            // No IPs or ports are embedded — only the relay URL (a domain) and metadata.
            val json = JSONObject().apply {
                put("v", 1)
                put("sessionId", requireNotNull(cfSessionId) { "Cloudflare invite requires cfSessionId" })
                put("topic",     topicHex)
                put("relayUrl",  requireNotNull(cfRelayUrl)  { "Cloudflare invite requires cfRelayUrl" })
                put("expiry",    requireNotNull(cfExpiry)    { "Cloudflare invite requires cfExpiry" })
                put("token",     requireNotNull(cfToken)     { "Cloudflare invite requires cfToken" })
            }
            val b64 = Base64.encodeToString(json.toString().encodeToByteArray(), Base64.NO_WRAP)
            "$CLOUDFLARE_PREFIX$b64"
        }
    }

    companion object {
        const val DIRECT_PREFIX     = "JSAS1:"
        const val RELAY_PREFIX      = "JSASR1:"
        const val HYBRID_PREFIX     = "JSASH1:"
        /**
         * Versioned Cloudflare invite prefix.
         * Format: JSASCF1:<base64(JSON)>
         * JSON schema: { v:1, sessionId, topic, relayUrl, expiry, token }
         */
        const val CLOUDFLARE_PREFIX = "JSASCF1:"

        fun decode(value: String): AlterSendInvite {
            val trimmed = value.trim()

            // ── Cloudflare invite ─────────────────────────────────────────
            if (trimmed.startsWith(CLOUDFLARE_PREFIX)) {
                val b64  = trimmed.removePrefix(CLOUDFLARE_PREFIX)
                val json = runCatching {
                    JSONObject(Base64.decode(b64, Base64.NO_WRAP).decodeToString())
                }.getOrElse { throw IllegalArgumentException("Invalid Cloudflare invite code") }

                val sessionId = json.optString("sessionId").takeIf { it.length == 32 }
                    ?: throw IllegalArgumentException("Cloudflare invite missing or invalid sessionId")
                val topic    = AlterSendProtocol.normalizeTopicHex(
                    json.optString("topic").takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Cloudflare invite missing topic")
                )
                val relayUrl = json.optString("relayUrl").takeIf { it.startsWith("wss://") || it.startsWith("ws://") }
                    ?: throw IllegalArgumentException("Cloudflare invite has invalid relayUrl")
                val expiry   = json.optLong("expiry").takeIf { it > 0 }
                    ?: throw IllegalArgumentException("Cloudflare invite missing expiry")
                val token    = json.optString("token").takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Cloudflare invite missing token")

                return AlterSendInvite(
                    // host/port are unused for Cloudflare mode but kept non-null for the data class.
                    host          = "",
                    port          = 0,
                    topicHex      = topic,
                    mode          = AlterSendInviteMode.Cloudflare,
                    cfSessionId   = sessionId,
                    cfRelayUrl    = relayUrl,
                    cfExpiry      = expiry,
                    cfToken       = token
                )
            }

            if (trimmed.startsWith(HYBRID_PREFIX)) {
                val raw = trimmed.removePrefix(HYBRID_PREFIX)
                val parts = raw.split(":")
                require(parts.size == 6) { "Invalid Remote Transfer hybrid code" }
                val directPort = parts[1].toIntOrNull()
                val fallbackPort = parts[3].toIntOrNull()
                require(directPort != null && directPort in 1..65535) { "Invalid Remote Transfer direct port" }
                require(fallbackPort != null && fallbackPort in 1..65535) { "Invalid Remote Transfer relay port" }
                require(parts[4].isNotBlank()) { "Invalid Remote Transfer relay session" }
                return AlterSendInvite(
                    host = parts[0],
                    port = directPort,
                    relayHost = parts[2],
                    relayPort = fallbackPort,
                    relaySessionId = parts[4],
                    topicHex = AlterSendProtocol.normalizeTopicHex(parts[5]),
                    mode = AlterSendInviteMode.Hybrid
                )
            }

            if (trimmed.startsWith(RELAY_PREFIX)) {
                val raw = trimmed.removePrefix(RELAY_PREFIX)
                val parts = raw.split(":")
                require(parts.size == 4) { "Invalid Remote Transfer relay code" }
                val port = parts[1].toIntOrNull()
                require(port != null && port in 1..65535) { "Invalid Remote Transfer relay port" }
                require(parts[2].isNotBlank()) { "Invalid Remote Transfer relay session" }
                return AlterSendInvite(
                    host = parts[0],
                    port = port,
                    relaySessionId = parts[2],
                    topicHex = AlterSendProtocol.normalizeTopicHex(parts[3]),
                    mode = AlterSendInviteMode.Relay
                )
            }

            val raw = trimmed.removePrefix(DIRECT_PREFIX)
            val parts = raw.split(":")
            require(parts.size == 3) { "Invalid Remote Transfer code" }
            val port = parts[1].toIntOrNull()
            require(port != null && port in 1..65535) { "Invalid Remote Transfer port" }
            return AlterSendInvite(
                host = parts[0],
                port = port,
                topicHex = AlterSendProtocol.normalizeTopicHex(parts[2])
            )
        }
    }
}
