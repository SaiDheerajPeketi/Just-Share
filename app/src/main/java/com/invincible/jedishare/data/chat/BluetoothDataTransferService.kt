package com.invincible.jedishare.data.chat

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.invincible.jedishare.domain.chat.BluetoothMessage
import com.invincible.jedishare.domain.chat.TransferFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

const val BUFFER_SIZE = 990 // Adjust the buffer size as needed

class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {
    fun listenForIncomingMessages(): Flow<BluetoothMessage> {
        return flow {
            if (!socket.isConnected) {
                return@flow
            }

            val mutableListOfByteArrays: MutableList<ByteArray> = mutableListOf()

            var cnt = 0
            val timeoutMillis = 5000 // Adjust the timeout as needed

            while (true) {
                val buffer = ByteArray(BUFFER_SIZE)
                val startTime = System.currentTimeMillis()
                var byteCount: Int

                // Read until data is available or timeout occurs
                while (true) {
                    byteCount = try {
                        socket.inputStream.read(buffer)
                    } catch (e: IOException) {
                        throw TransferFailedException()
                    }

                    if (byteCount > 0) {
                        break
                    }

                    if (System.currentTimeMillis() - startTime > timeoutMillis) {
                        // Timeout, break the outer loop
                        return@flow
                    }
                }

                Log.e("HELLOME", "outtt " + byteCount.toString())

                // Only use the relevant portion of the buffer based on byteCount
                val relevantBytes = buffer.copyOf(byteCount)
                cnt = cnt + byteCount
                mutableListOfByteArrays.add(relevantBytes)
            }

            // Concatenate all ByteArrays into a single ByteArray
            val concatenatedByteArray: ByteArray = mutableListOfByteArrays.flatMap { it.toList() }.toByteArray()

            Log.e("HELLOME", "output size = " + cnt.toString())

            emit(
                concatenatedByteArray.decodeToString().toBluetoothMessage(
                    isFromLocalUser = false
                )
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val outputStream = socket.outputStream

                // Send the length of the message first (as a 4-byte integer)
                val lengthBytes = ByteBuffer.allocate(100).putInt(bytes.size).array()
                outputStream.write(lengthBytes)

                // Send the actual message
                outputStream.write(bytes)
                Log.e("HELLOME", "input size = " + bytes.size.toString())
            } catch (e: IOException) {
                return@withContext false
            }

            true
        }
    }

    fun ByteBuffer.putInt(value: Int): ByteBuffer {
        return put((value shr 24).toByte())
            .put((value shr 16).toByte())
            .put((value shr 8).toByte())
            .put(value.toByte())
    }
}
