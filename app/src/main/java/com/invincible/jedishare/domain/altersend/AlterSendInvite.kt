package com.invincible.jedishare.domain.altersend

data class AlterSendInvite(
    val host: String,
    val port: Int,
    val topicHex: String
) {
    fun encode(): String = "$PREFIX$host:$port:$topicHex"

    companion object {
        const val PREFIX = "JSAS1:"

        fun decode(value: String): AlterSendInvite {
            val raw = value.trim().removePrefix(PREFIX)
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
