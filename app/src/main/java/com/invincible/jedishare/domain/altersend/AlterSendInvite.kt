package com.invincible.jedishare.domain.altersend

enum class AlterSendInviteMode {
    Direct,
    Relay,
    Hybrid
}

data class AlterSendInvite(
    val host: String,
    val port: Int,
    val topicHex: String,
    val mode: AlterSendInviteMode = AlterSendInviteMode.Direct,
    val relaySessionId: String? = null,
    val relayHost: String? = null,
    val relayPort: Int? = null
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
    }

    companion object {
        const val DIRECT_PREFIX = "JSAS1:"
        const val RELAY_PREFIX = "JSASR1:"
        const val HYBRID_PREFIX = "JSASH1:"

        fun decode(value: String): AlterSendInvite {
            val trimmed = value.trim()
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
