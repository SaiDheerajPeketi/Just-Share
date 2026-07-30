package com.invincible.jedishare.domain.altersend

enum class AlterSendInviteMode {
    Direct,
    Relay
}

data class AlterSendInvite(
    val host: String,
    val port: Int,
    val topicHex: String,
    val mode: AlterSendInviteMode = AlterSendInviteMode.Direct,
    val relaySessionId: String? = null
) {
    fun encode(): String = when (mode) {
        AlterSendInviteMode.Direct -> "$DIRECT_PREFIX$host:$port:$topicHex"
        AlterSendInviteMode.Relay -> {
            val sessionId = requireNotNull(relaySessionId) { "Relay invite requires a session id" }
            "$RELAY_PREFIX$host:$port:$sessionId:$topicHex"
        }
    }

    companion object {
        const val DIRECT_PREFIX = "JSAS1:"
        const val RELAY_PREFIX = "JSASR1:"

        fun decode(value: String): AlterSendInvite {
            val trimmed = value.trim()
            if (trimmed.startsWith(RELAY_PREFIX)) {
                val raw = trimmed.removePrefix(RELAY_PREFIX)
                val parts = raw.split(":")
                require(parts.size == 4) { "Invalid AlterSend relay code" }
                val port = parts[1].toIntOrNull()
                require(port != null && port in 1..65535) { "Invalid AlterSend relay port" }
                require(parts[2].isNotBlank()) { "Invalid AlterSend relay session" }
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
            require(parts.size == 3) { "Invalid AlterSend code" }
            val port = parts[1].toIntOrNull()
            require(port != null && port in 1..65535) { "Invalid AlterSend port" }
            return AlterSendInvite(
                host = parts[0],
                port = port,
                topicHex = AlterSendProtocol.normalizeTopicHex(parts[2])
            )
        }
    }
}
