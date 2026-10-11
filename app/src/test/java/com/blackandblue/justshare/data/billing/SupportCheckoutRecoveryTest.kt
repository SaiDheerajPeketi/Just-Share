package com.blackandblue.justshare.data.billing

import org.junit.Assert.*
import org.junit.Test

class SupportCheckoutRecoveryTest {
    private class MemoryStorage : SupportAttemptStorage {
        var attempt: SupportAttempt? = null
        var history = emptyMap<String, Set<String>>()
        var writable = true
        override fun read() = attempt
        override fun readKnown() = history
        override fun write(attempt: SupportAttempt?, known: Map<String, Set<String>>): Boolean {
            if (!writable) return false
            this.attempt = attempt
            history = known
            return true
        }
    }
    @Test fun successfulAThenRestartRepeatedPendingOnlyAcceptsBoundNewB() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val first = state.begin()!!
        assertTrue(state.remember(first, "account", "support_developer_1", emptySet()))
        assertTrue(state.bindReceipt("account", "support_developer_1", "A", "encryptedA"))
        assertTrue(state.finalized("account", "support_developer_1", "A"))
        state.gate.finish(first)
        val restarted = SupportCheckoutState()
        assertTrue(restarted.bind(storage))
        val repeat = restarted.begin()!!
        assertTrue(restarted.remember(repeat, "account", "support_developer_1", emptySet()))
        assertFalse(restarted.bindReceipt("account", "support_developer_1", "A", "encryptedA"))
        assertFalse(restarted.finalized("account", "support_developer_1", "A"))
        assertFalse(restarted.bindReceipt("account", "support_developer_10", "B", "encryptedB"))
        assertTrue(restarted.bindReceipt("account", "support_developer_1", "B", "encryptedB"))
        assertFalse(restarted.finalized("account", "support_developer_1", "A"))
        assertTrue(restarted.finalized("account", "support_developer_1", "B"))
        assertEquals(setOf("A", "B"), restarted.known("account"))
    }
    @Test fun restartPendingBlocksOtherSkuProAndSurvivesUnknownFinalization() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val ticket = state.begin()!!
        assertTrue(state.remember(ticket, "account", "support_developer_999", setOf("old")))
        assertTrue(state.bindReceipt("account", "support_developer_999", "new", "encrypted"))
        val restarted = SupportCheckoutState()
        assertTrue(restarted.bind(storage))
        assertNull(restarted.begin()) // Pro, data packs and every support tier share this dispatcher.
        assertEquals("encrypted", restarted.pending!!.protectedPurchase)
        assertFalse(restarted.finalized("other-account", "support_developer_999", "new"))
        assertFalse(restarted.finalized("account", "pro_unlock", "new"))
        storage.writable = false
        assertFalse(restarted.finalized("account", "support_developer_999", "new"))
        assertNotNull(SupportAttemptLedger(storage).pending)
        storage.writable = true
        assertTrue(restarted.finalized("account", "support_developer_999", "new"))
        assertNotNull(restarted.begin())
    }
    @Test fun storageFailureAndStaleTicketCannotPersistOrReleaseNewerAttempt() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val first = state.begin()!!
        storage.writable = false
        assertFalse(state.remember(first, "account", "support_developer_1", emptySet()))
        state.gate.finish(first)
        storage.writable = true
        val second = state.begin()!!
        assertTrue(state.remember(second, "account", "pro_unlock", emptySet()))
        state.gate.finish(first)
        assertTrue(state.gate.owns(second))
        assertFalse(state.remember(first, "account", "support_developer_1", emptySet()))
        storage.writable = false
        assertFalse(state.bindReceipt("account", "pro_unlock", "new", "encrypted"))
        assertNull(state.pending!!.receipt)
    }
}
