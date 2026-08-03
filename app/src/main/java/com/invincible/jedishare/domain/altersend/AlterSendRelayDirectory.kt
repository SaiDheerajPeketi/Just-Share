package com.invincible.jedishare.domain.altersend

data class AlterSendRelayEndpoint(
    val host: String,
    val port: Int
)

object AlterSendRelayDirectory {
    private const val ANDROID_HOST_RELAY = "10.0.2.2"
    private const val DEFAULT_RELAY_PORT = 41404

    fun endpoints(
        publicRelayNodes: String,
        configuredHost: String,
        configuredPort: Int,
        includeAndroidHostRelay: Boolean = false
    ): List<AlterSendRelayEndpoint> = buildList {
        if (includeAndroidHostRelay) {
            addDistinct(AlterSendRelayEndpoint(ANDROID_HOST_RELAY, DEFAULT_RELAY_PORT))
        }
        publicRelayNodes
            .split(',', ';')
            .mapNotNull { raw -> parseEndpoint(raw.trim()) }
            .forEach { endpoint -> addDistinct(endpoint) }
        val configured = configuredHost.trim()
        if (configured.isNotBlank() && configuredPort in 1..65535) {
            addDistinct(AlterSendRelayEndpoint(configured, configuredPort))
        }
    }

    fun parseEndpoint(value: String): AlterSendRelayEndpoint? {
        if (value.isBlank()) return null
        val separator = value.lastIndexOf(':')
        if (separator <= 0 || separator == value.lastIndex) return null
        val host = value.substring(0, separator).trim()
        val port = value.substring(separator + 1).trim().toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) return null
        return AlterSendRelayEndpoint(host, port)
    }

    private fun MutableList<AlterSendRelayEndpoint>.addDistinct(endpoint: AlterSendRelayEndpoint) {
        if (endpoint !in this) add(endpoint)
    }
}
