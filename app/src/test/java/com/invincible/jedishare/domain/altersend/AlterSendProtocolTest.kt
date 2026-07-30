package com.invincible.jedishare.domain.altersend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlterSendProtocolTest {
    @Test
    fun chunkSizeMatchesAlterSendTiers() {
        assertEquals(64 * 1024, AlterSendProtocol.selectChunkSize(512 * 1024))
        assertEquals(256 * 1024, AlterSendProtocol.selectChunkSize(50L * 1024 * 1024))
        assertEquals(1024 * 1024, AlterSendProtocol.selectChunkSize(5L * 1024 * 1024 * 1024))
        assertEquals(4 * 1024 * 1024, AlterSendProtocol.selectChunkSize(20L * 1024 * 1024 * 1024))
    }

    @Test
    fun chunkRangeClampsLastChunk() {
        val range = AlterSendProtocol.chunkRange(index = 2, fileSizeBytes = 150, chunkSizeBytes = 64)

        assertEquals(128L, range.offset)
        assertEquals(22, range.length)
    }

    @Test
    fun topicIsSixtyFourHexChars() {
        val topic = AlterSendProtocol.generateTopicHex()

        assertEquals(64, topic.length)
        assertTrue(AlterSendProtocol.isValidTopicHex(topic))
        assertTrue(AlterSendProtocol.isValidTopicHex("A".repeat(64)))
        assertFalse(AlterSendProtocol.isValidTopicHex("g".repeat(64)))
        assertFalse(AlterSendProtocol.isValidTopicHex("a".repeat(63)))
    }
}
