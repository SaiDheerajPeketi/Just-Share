package com.blackandblue.justshare.data.altersend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareWebSocketTransportTest {
    @Test
    fun `relay credential is never placed in websocket URL`() {
        val url = CloudflareWebSocketTransport.buildUrl(
            baseUrl = "wss://relay.justshare.blackandblue.co.in/",
            sessionId = "0123456789abcdef0123456789abcdef",
            role = "sender",
            expiry = 1_900_000_000,
            maxBytes = 1024
        )

        assertTrue(url.startsWith("wss://relay.justshare.blackandblue.co.in/v1/session/"))
        assertTrue(url.contains("role=sender"))
        assertTrue(url.contains("expiry=1900000000"))
        assertTrue(url.contains("limit=1024"))
        assertFalse(url.contains("token", ignoreCase = true))
    }
}
