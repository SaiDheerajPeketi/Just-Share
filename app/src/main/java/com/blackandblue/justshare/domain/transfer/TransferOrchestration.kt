package com.blackandblue.justshare.domain.transfer

enum class RemoteHostRoute {
    Cloudflare,
    Relay,
    Hybrid,
    Direct,
    Unavailable
}

data class WifiGroupState(
    val connected: Boolean,
    val status: String
)

object TransferOrchestration {
    fun remoteHostRoute(
        cloudflareEnabled: Boolean,
        cloudflareUrlConfigured: Boolean,
        emulator: Boolean,
        relayReachable: Boolean
    ): RemoteHostRoute = when {
        cloudflareEnabled && cloudflareUrlConfigured -> RemoteHostRoute.Cloudflare
        emulator && relayReachable -> RemoteHostRoute.Relay
        emulator -> RemoteHostRoute.Unavailable
        relayReachable -> RemoteHostRoute.Hybrid
        else -> RemoteHostRoute.Direct
    }

    fun shouldConnectAfterBond(
        pendingAddress: String?,
        pairedAddresses: Set<String>
    ): Boolean = pendingAddress != null && pendingAddress in pairedAddresses

    fun wifiGroupState(
        senderRole: Boolean,
        groupFormed: Boolean,
        groupOwner: Boolean,
        connectedClientCount: Int
    ): WifiGroupState = when {
        !groupFormed -> WifiGroupState(false, if (senderRole) "" else "hosting")
        !groupOwner -> WifiGroupState(true, "connected")
        connectedClientCount > 0 -> WifiGroupState(true, "connected")
        else -> WifiGroupState(false, "hosting")
    }
}
