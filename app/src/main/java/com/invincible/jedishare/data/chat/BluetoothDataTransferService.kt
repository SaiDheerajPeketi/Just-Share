package com.invincible.jedishare.data.chat

import timber.log.Timber

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
 * - [listenForIncomingMessages] emits [IncomingData] sealed types rather than raw bytes.
 */
class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {
    companion object {
        const val CHUNK_SIZE = 65536 // 64 KB — much better throughput for Bluetooth
    }

    /**
     * Produces a stream of [IncomingData] events from the Bluetooth socket.
     * Runs on [Dispatchers.IO].
     *
     * Protocol (same as send side in [AndroidBluetoothController]):
     *  1. 4-byte metadata length.
     *  2. Serialized [com.invincible.jedishare.domain.chat.FileInfo] bytes → [IncomingData.FileMetadata].
     *  3. 8-byte file size.
     *  4. Raw file bytes → [IncomingData.FileChunk] until [IncomingData.EndOfFile].
     */
    fun listenForIncomingMessages(): Flow<IncomingData> = flow {
        Timber.d("BluetoothDataTransferService - listenForIncomingMessages called")
        if (!socket.isConnected) return@flow
        val dataIn = java.io.DataInputStream(socket.inputStream)
        val buffer = ByteArray(CHUNK_SIZE)
        
        while (true) {
            val metaSize = try {
                dataIn.readInt()
            } catch (e: IOException) {
                break
            }
            
            val metaBytes = ByteArray(metaSize)
            try {
                dataIn.readFully(metaBytes)
            } catch (e: IOException) {
                throw TransferFailedException()
            }
            emit(IncomingData.FileMetadata(metaBytes))
            
            val fileSize = try {
                dataIn.readLong()
            } catch (e: IOException) {
                throw TransferFailedException()
            }
            
            while (true) {
                val chunkSize = try {
                    dataIn.readInt()
                } catch (e: IOException) {
                    throw TransferFailedException()
                }
                
                if (chunkSize == 0) {
                    Log.d("BDTransferService", "EOF marker (chunk size 0) reached for file")
                    emit(IncomingData.EndOfFile)
                    break
                }
                
                if (chunkSize < 0 || chunkSize > CHUNK_SIZE) {
                    Log.e("BDTransferService", "Invalid chunk size received: $chunkSize")
                    throw TransferFailedException()
                }
                
                var remainingChunk = chunkSize
                var offset = 0
                while (remainingChunk > 0) {
                    val byteCount = try {
                        dataIn.read(buffer, offset, remainingChunk)
                    } catch (e: IOException) {
                        throw TransferFailedException()
                    }
                    if (byteCount == -1) throw TransferFailedException()
                    offset += byteCount
                    remainingChunk -= byteCount
                }
                
                val chunk = buffer.copyOfRange(0, chunkSize)
                emit(IncomingData.FileChunk(chunk))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Writes [bytes] to the socket's output stream. Returns true on success. */
    fun sendMessage(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean {
        return try {
            socket.outputStream.write(bytes, offset, length)
            true
        } catch (e: IOException) {
            Log.e("BDTransferService", "Failed to send message", e)
            false
        }
    }

}

/** Represents a single incoming event from a Bluetooth data stream. */
sealed class IncomingData {
    /** Serialized metadata for the next incoming file. */
    data class FileMetadata(val bytes: ByteArray) : IncomingData()
    /** Raw file bytes received mid-transfer. */
    data class FileChunk(val bytes: ByteArray) : IncomingData()
    /** Signals that the entire current file has been received. */
    object EndOfFile : IncomingData()
}
