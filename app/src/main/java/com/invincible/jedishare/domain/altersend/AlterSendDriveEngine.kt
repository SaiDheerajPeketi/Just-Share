package com.invincible.jedishare.domain.altersend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

interface AlterSendChunkReader {
    suspend fun size(): Long
    suspend fun read(offset: Long, length: Int): ByteArray
    suspend fun close()
}

interface AlterSendChunkWriter {
    suspend fun allocate(size: Long)
    suspend fun write(offset: Long, data: ByteArray)
    suspend fun finalize(): String
    suspend fun abort()
}

data class AlterSendDriveResult(
    val savedTo: String,
    val totalBytes: Long,
    val chunkSize: Int,
    val chunksWritten: Int,
    val resumeBitmap: ByteArray
)

class AlterSendIntegrityException(message: String) : IllegalStateException(message)

object AlterSendDriveEngine {
    suspend fun receiveFrom(
        reader: AlterSendChunkReader,
        writer: AlterSendChunkWriter,
        expectedSize: Long? = null,
        resumeBits: ByteArray? = null,
        onProgress: (receivedBytes: Long, totalBytes: Long, bitmap: AlterSendBitmap) -> Unit = { _, _, _ -> }
    ): AlterSendDriveResult = withContext(Dispatchers.IO) {
        val size = reader.size()
        if (size < 0L) throw AlterSendIntegrityException("File size must be non-negative")
        if (expectedSize != null && expectedSize != size) {
            throw AlterSendIntegrityException("Sender announced $size bytes, expected $expectedSize")
        }

        val chunkSize = AlterSendProtocol.selectChunkSize(size)
        val totalChunks = AlterSendProtocol.chunkCount(size, chunkSize)
        val bitmap = try {
            resumeBits?.let { AlterSendBitmap.deserialize(totalChunks, it) } ?: AlterSendBitmap(totalChunks)
        } catch (err: IllegalArgumentException) {
            throw AlterSendIntegrityException("Resume state does not match this file: ${err.message}")
        }

        var receivedBytes = 0L
        for (index in 0 until totalChunks) {
            if (bitmap.get(index)) {
                receivedBytes += AlterSendProtocol.chunkRange(index, size, chunkSize).length
            }
        }

        try {
            writer.allocate(size)
            for (index in bitmap.missing()) {
                coroutineContext.ensureActive()
                val range = AlterSendProtocol.chunkRange(index, size, chunkSize)
                val data = reader.read(range.offset, range.length)
                if (data.size != range.length) {
                    throw AlterSendIntegrityException("Chunk $index length ${data.size}, expected ${range.length}")
                }
                writer.write(range.offset, data)
                bitmap.set(index)
                receivedBytes += range.length
                onProgress(receivedBytes, size, bitmap)
            }
            val savedTo = writer.finalize()
            AlterSendDriveResult(
                savedTo = savedTo,
                totalBytes = size,
                chunkSize = chunkSize,
                chunksWritten = bitmap.count(),
                resumeBitmap = bitmap.serialize()
            )
        } catch (err: Throwable) {
            writer.abort()
            throw err
        } finally {
            reader.close()
        }
    }
}
