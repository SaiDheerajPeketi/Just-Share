package com.invincible.jedishare.domain.altersend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlterSendRelayDirectoryTest {
    @Test
    fun parseEndpointRejectsInvalidValues() {
        assertNull(AlterSendRelayDirectory.parseEndpoint(""))
        assertNull(AlterSendRelayDirectory.parseEndpoint("relay.example.com"))
        assertNull(AlterSendRelayDirectory.parseEndpoint("relay.example.com:not-a-port"))
        assertNull(AlterSendRelayDirectory.parseEndpoint("relay.example.com:70000"))
    }

    @Test
    fun endpointsPreferPublicNodesBeforeConfiguredRelay() {
        val endpoints = AlterSendRelayDirectory.endpoints(
            publicRelayNodes = "public-one.example:41404; public-two.example:41405",
            configuredHost = "relay.edgelab.co.in",
            configuredPort = 41404
        )

        assertEquals(
            listOf(
                AlterSendRelayEndpoint("public-one.example", 41404),
                AlterSendRelayEndpoint("public-two.example", 41405),
                AlterSendRelayEndpoint("relay.edgelab.co.in", 41404)
            ),
            endpoints
        )
    }

    @Test
    fun endpointsDeduplicateAndCanPreferAndroidHostRelay() {
        val endpoints = AlterSendRelayDirectory.endpoints(
            publicRelayNodes = "relay.edgelab.co.in:41404,relay.edgelab.co.in:41404",
            configuredHost = "relay.edgelab.co.in",
            configuredPort = 41404,
            includeAndroidHostRelay = true
        )

        assertEquals(
            listOf(
                AlterSendRelayEndpoint("10.0.2.2", 41404),
                AlterSendRelayEndpoint("relay.edgelab.co.in", 41404)
            ),
            endpoints
        )
    }
}
