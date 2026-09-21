package com.blackandblue.justshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JustShareTelemetryTest {
    @Test
    fun `sanitizer creates bounded operational labels`() {
        assertEquals("relay_session_failed", JustShareTelemetry.sanitize("Relay session failed"))
        assertEquals(40, JustShareTelemetry.sanitize("x".repeat(80))?.length)
    }

    @Test
    fun `sanitizer rejects empty labels`() {
        assertNull(JustShareTelemetry.sanitize(" -- "))
    }
}
