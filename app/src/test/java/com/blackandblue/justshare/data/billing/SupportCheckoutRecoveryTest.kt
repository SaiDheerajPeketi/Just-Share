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
        assertTrue(state.remember(first, SupportAttempt("account", "support_developer_1", emptySet())))
        assertTrue(state.bindReceipt(state.pending!!.identifier, "account", "support_developer_1", "A", "encryptedA", 100, null))
        assertTrue(state.finalized(state.pending!!.identifier, "account", "support_developer_1", "A"))
        state.gate.finish(first)
        val restarted = SupportCheckoutState()
        assertTrue(restarted.bind(storage))
        val repeat = restarted.begin()!!
        assertTrue(restarted.remember(repeat, SupportAttempt("account", "support_developer_1", emptySet())))
        assertFalse(restarted.bindReceipt(restarted.pending!!.identifier, "account", "support_developer_1", "A", "encryptedA", 100, null))
        assertFalse(restarted.finalized(restarted.pending!!.identifier, "account", "support_developer_1", "A"))
        assertFalse(restarted.bindReceipt(restarted.pending!!.identifier, "account", "support_developer_10", "B", "encryptedB", 100, null))
        assertTrue(restarted.bindReceipt(restarted.pending!!.identifier, "account", "support_developer_1", "B", "encryptedB", 100, null))
        assertFalse(restarted.finalized(restarted.pending!!.identifier, "account", "support_developer_1", "A"))
        assertTrue(restarted.finalized(restarted.pending!!.identifier, "account", "support_developer_1", "B"))
        assertEquals(setOf("A", "B"), restarted.known("account"))
    }
    @Test fun restartPendingBlocksOtherSkuProAndSurvivesUnknownFinalization() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val ticket = state.begin()!!
        assertTrue(state.remember(ticket, SupportAttempt("account", "support_developer_999", setOf("old"))))
        assertTrue(state.bindReceipt(state.pending!!.identifier, "account", "support_developer_999", "new", "encrypted", 100, null))
        val restarted = SupportCheckoutState()
        assertTrue(restarted.bind(storage))
        assertNull(restarted.begin()) // Pro, data packs and every support tier share this dispatcher.
        assertEquals("encrypted", restarted.pending!!.protectedPurchase)
        assertFalse(restarted.finalized(restarted.pending!!.identifier, "other-account", "support_developer_999", "new"))
        assertFalse(restarted.finalized(restarted.pending!!.identifier, "account", "pro_unlock", "new"))
        storage.writable = false
        assertFalse(restarted.finalized(restarted.pending!!.identifier, "account", "support_developer_999", "new"))
        assertNotNull(SupportAttemptLedger(storage).pending)
        storage.writable = true
        assertTrue(restarted.finalized(restarted.pending!!.identifier, "account", "support_developer_999", "new"))
        assertNotNull(restarted.begin())
    }
    @Test fun storageFailureAndStaleTicketCannotPersistOrReleaseNewerAttempt() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val first = state.begin()!!
        storage.writable = false
        assertFalse(state.remember(first, SupportAttempt("account", "support_developer_1", emptySet())))
        state.gate.finish(first)
        storage.writable = true
        val second = state.begin()!!
        assertTrue(state.remember(second, SupportAttempt("account", "pro_unlock", emptySet())))
        state.gate.finish(first)
        assertTrue(state.gate.owns(second))
        assertFalse(state.remember(first, SupportAttempt("account", "support_developer_1", emptySet())))
        storage.writable = false
        assertFalse(state.bindReceipt(state.pending!!.identifier, "account", "pro_unlock", "new", "encrypted", 100, null))
        assertNull(state.pending!!.receipt)
    }
    @Test fun delayedUnseenOldOrderCannotBindLiveAttemptEvenWithWrongDeviceClock() {
        val state = SupportCheckoutState()
        assertTrue(state.bind(MemoryStorage()))
        val ticket = state.begin()!!
        val live = SupportAttempt("account", "support_developer_1", emptySet(), startedAt = 200, live = true)
        assertTrue(state.remember(ticket, live))
        assertFalse(state.bindReceipt(live.identifier, "account", live.product, "old-A", "encrypted", 199, "account"))
        assertFalse(state.bindReceipt(live.identifier, "account", live.product, "new-B", "encrypted", 220, null))
        assertFalse(state.bindReceipt(live.identifier, "account", live.product, "new-B", "encrypted", 220, "other-account"))
        assertTrue(state.bindReceipt(live.identifier, "account", live.product, "new-B", "encrypted", 220, "account"))
        assertFalse(state.finalized("old-attempt", "account", live.product, "new-B"))
        assertTrue(state.finalized(live.identifier, "account", live.product, "new-B"))
    }
    @Test fun staleAttemptCancellationCannotClearNewPendingRecord() {
        val storage = MemoryStorage()
        val state = SupportCheckoutState()
        assertTrue(state.bind(storage))
        val ticket = state.begin()!!
        val a = SupportAttempt("account", "pro_unlock", emptySet(), identifier = "A", startedAt = 100, live = true)
        assertTrue(state.remember(ticket, a))
        assertTrue(state.clear(identifier = "A"))
        state.gate.finish(ticket)
        val next = state.begin()!!
        assertTrue(state.remember(next, a.copy(identifier = "B", startedAt = 200)))
        assertFalse(state.clear(identifier = "A"))
        val restarted = SupportCheckoutState()
        assertTrue(restarted.bind(storage))
        assertNull(restarted.begin())
        assertEquals("B", restarted.pending!!.identifier)
        assertEquals(200L, restarted.pending!!.startedAt)
    }

}
