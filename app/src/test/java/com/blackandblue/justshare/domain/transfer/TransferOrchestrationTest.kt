package com.blackandblue.justshare.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferOrchestrationTest {
    @Test
    fun cloudflareWinsWhenEnabledAndConfigured() {
        assertEquals(
            RemoteHostRoute.Cloudflare,
            TransferOrchestration.remoteHostRoute(true, true, false, true)
        )
    }

    @Test
    fun physicalDeviceUsesHybridThenDirectWhenRelayIsUnavailable() {
        assertEquals(
            RemoteHostRoute.Hybrid,
            TransferOrchestration.remoteHostRoute(false, false, false, true)
        )
        assertEquals(
            RemoteHostRoute.Direct,
            TransferOrchestration.remoteHostRoute(false, false, false, false)
        )
    }

    @Test
    fun emulatorRequiresAReachableRelay() {
        assertEquals(
            RemoteHostRoute.Relay,
            TransferOrchestration.remoteHostRoute(false, false, true, true)
        )
        assertEquals(
            RemoteHostRoute.Unavailable,
            TransferOrchestration.remoteHostRoute(false, false, true, false)
        )
    }

    @Test
    fun newlyBondedPendingDeviceConnectsExactlyOnceEligible() {
        assertFalse(TransferOrchestration.shouldConnectAfterBond("AA", setOf("BB")))
        assertTrue(TransferOrchestration.shouldConnectAfterBond("AA", setOf("AA", "BB")))
        assertFalse(TransferOrchestration.shouldConnectAfterBond(null, setOf("AA")))
    }

    @Test
    fun wifiReceiverHostsUntilAClientJoins() {
        assertEquals(
            WifiGroupState(false, "hosting"),
            TransferOrchestration.wifiGroupState(false, false, true, 0)
        )
        assertEquals(
            WifiGroupState(false, "hosting"),
            TransferOrchestration.wifiGroupState(false, true, true, 0)
        )
        assertEquals(
            WifiGroupState(true, "connected"),
            TransferOrchestration.wifiGroupState(false, true, true, 1)
        )
    }

    @Test
    fun wifiClientConnectsWhenGroupForms() {
        assertEquals(
            WifiGroupState(true, "connected"),
            TransferOrchestration.wifiGroupState(true, true, false, 0)
        )
    }
}
