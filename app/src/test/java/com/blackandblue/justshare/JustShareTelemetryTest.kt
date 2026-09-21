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

    @Test
    fun `reported failures exclude file details`() {
        val failure = JustShareTelemetry.sanitizedFailure(
            code = "relay_failed",
            throwable = IllegalStateException("Photo.jpg from peer-123"),
        )

        assertEquals("relay_failed", failure.code)
        assertEquals("illegalstateexception", failure.type)
        assertEquals("relay_failed:illegalstateexception", failure.exception.message)
        assertNull(failure.exception.cause)
    }
}
