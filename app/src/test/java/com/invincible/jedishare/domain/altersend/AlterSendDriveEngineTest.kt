package com.invincible.jedishare.domain.altersend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlterSendDriveEngineTest {
    @Test
    fun transfersBytesWithAlterSendChunkGeometry() = runBlocking {
        val input = ByteArray(150_000) { index -> (index % 251).toByte() }
        val writer = MemoryWriter()

        val result = AlterSendDriveEngine.receiveFrom(
            reader = MemoryReader(input),
            writer = writer,
            expectedSize = input.size.toLong()
        )

        assertEquals("memory://received", result.savedTo)
        assertEquals(64 * 1024, result.chunkSize)
        assertEquals(3, result.chunksWritten)
        assertArrayEquals(input, writer.bytes())
    }

    @Test
    fun resumeBitsSkipAlreadyVerifiedChunks() = runBlocking {
        val input = ByteArray(300_000) { index -> (index % 127).toByte() }
        val chunkSize = AlterSendProtocol.selectChunkSize(input.size.toLong())
        val firstRange = AlterSendProtocol.chunkRange(0, input.size.toLong(), chunkSize)
        val resume = AlterSendBitmap(AlterSendProtocol.chunkCount(input.size.toLong(), chunkSize))
        val writer = MemoryWriter()

        writer.allocate(input.size.toLong())
        writer.write(firstRange.offset, input.copyOfRange(0, firstRange.length))
        resume.set(0)

        val result = AlterSendDriveEngine.receiveFrom(
            reader = MemoryReader(input),
            writer = writer,
            expectedSize = input.size.toLong(),
            resumeBits = resume.serialize()
        )

        assertEquals(5, result.chunksWritten)
        assertArrayEquals(input, writer.bytes())
    }

    @Test
    fun rejectsMismatchedExpectedSize() = runBlocking {
        val writer = MemoryWriter()
        val failed = runCatching {
            AlterSendDriveEngine.receiveFrom(
                reader = MemoryReader(ByteArray(16)),
                writer = writer,
                expectedSize = 15
            )
        }.exceptionOrNull()

        assertTrue(failed is AlterSendIntegrityException)
    }

    @Test
    fun rejectsShortChunkRead() = runBlocking {
        val writer = MemoryWriter()
        val failed = runCatching {
            AlterSendDriveEngine.receiveFrom(
                reader = ShortChunkReader(ByteArray(128)),
                writer = writer,
                expectedSize = 128
            )
        }.exceptionOrNull()

        assertTrue(failed is AlterSendIntegrityException)
    }

    private class MemoryReader(private val data: ByteArray) : AlterSendChunkReader {
        override suspend fun size(): Long = data.size.toLong()

        override suspend fun read(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            return data.copyOfRange(start, start + length)
        }

        override suspend fun close() = Unit
    }

    private class MemoryWriter : AlterSendChunkWriter {
        private var data = ByteArray(0)
        private var aborted = false

        override suspend fun allocate(size: Long) {
            if (data.size != size.toInt()) {
                data = ByteArray(size.toInt())
            }
            aborted = false
        }

        override suspend fun write(offset: Long, data: ByteArray) {
            data.copyInto(this.data, destinationOffset = offset.toInt())
        }

        override suspend fun finalize(): String {
            check(!aborted)
            return "memory://received"
        }

        override suspend fun abort() {
            aborted = true
        }

        fun bytes(): ByteArray = data.copyOf()
    }

    private class ShortChunkReader(private val data: ByteArray) : AlterSendChunkReader {
        override suspend fun size(): Long = data.size.toLong()

        override suspend fun read(offset: Long, length: Int): ByteArray {
            val start = offset.toInt()
            return data.copyOfRange(start, start + (length - 1).coerceAtLeast(0))
        }

        override suspend fun close() = Unit
    }
}
