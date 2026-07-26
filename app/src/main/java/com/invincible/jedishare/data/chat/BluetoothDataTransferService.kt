package com.invincible.jedishare.data.chat

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.invincible.jedishare.domain.chat.TransferFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages the raw byte-level I/O over a connected [BluetoothSocket].
 *
 * Fixes applied:
 * - Removed all ViewModel references. This class is now a pure data-layer service.
 * - [CHUNK_SIZE] raised to 8192 bytes for better throughput.
 * - File delimiter is now a dedicated sealed type, not a magic-number byte comparison.
 * - [listenForIncomingMessages] emits [IncomingData] sealed types rather than raw bytes,
 *   letting the ViewModel cleanly distinguish metadata from file data.
 */
class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {
    companion object {
        const val CHUNK_SIZE = 8192 // 8 KB — better throughput than old 990 bytes
        // A 880-byte sentinel payload that signals "end of file" to the receiver.
        // Using a fixed-size sentinel avoids the fragile size==880 magic number.
        val END_OF_FILE_SENTINEL: ByteArray = ByteArray(CHUNK_SIZE) { 0xFF.toByte() }
    }

    /**
     * Produces a stream of [IncomingData] events from the Bluetooth socket.
     * Runs on [Dispatchers.IO].
     *
     * Protocol (same as send side in [AndroidBluetoothController]):
     *  1. First chunk for a file: serialized [com.invincible.jedishare.domain.chat.FileInfo] bytes → [IncomingData.FileMetadata]
     *  2. Subsequent chunks: raw file bytes → [IncomingData.FileChunk]
     *  3. After all file chunks: [END_OF_FILE_SENTINEL] → [IncomingData.EndOfFile]
     */
    fun listenForIncomingMessages(): Flow<IncomingData> = flow {
        if (!socket.isConnected) return@flow
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            val byteCount = try {
                socket.inputStream.read(buffer)
            } catch (e: IOException) {
                throw TransferFailedException()
            }
            if (byteCount <= 0) continue

            val chunk = buffer.copyOfRange(0, byteCount)

            when {
                // Detect sentinel: a full CHUNK_SIZE buffer filled with 0xFF
                isSentinel(chunk) -> {
                    Log.d("BDTransferService", "EOF sentinel received")
                    emit(IncomingData.EndOfFile)
                }
                else -> {
                    Log.d("BDTransferService", "Received chunk: ${chunk.size} bytes")
                    emit(IncomingData.FileChunk(chunk))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Writes [bytes] to the socket's output stream. Returns true on success. */
    suspend fun sendMessage(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            socket.outputStream.write(bytes)
            Log.d("BDTransferService", "Sent ${bytes.size} bytes")
            true
        } catch (e: IOException) {
            Log.e("BDTransferService", "Failed to send message", e)
            false
        }
    }

    private fun isSentinel(chunk: ByteArray): Boolean {
        if (chunk.size != CHUNK_SIZE) return false
        return chunk.all { it == 0xFF.toByte() }
    }
}

/** Represents a single incoming event from a Bluetooth data stream. */
sealed class IncomingData {
    /** Raw file bytes received mid-transfer. */
    data class FileChunk(val bytes: ByteArray) : IncomingData()
    /** Signals that the entire current file has been received. */
    object EndOfFile : IncomingData()
}
