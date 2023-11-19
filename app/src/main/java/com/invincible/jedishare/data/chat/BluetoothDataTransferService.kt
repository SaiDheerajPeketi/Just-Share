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
    fun listenForIncomingMessages(): Flow<ByteArray> {
        return flow {
            if (!socket.isConnected) {
                return@flow
            }
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val byteCount = try {
                    socket.inputStream.read(buffer)
                } catch (e: IOException) {
                    throw TransferFailedException()
                }
                var bufferRed = buffer.copyOfRange(0, byteCount)
                Log.e("HELLOME", "Received: " + bufferRed.size.toString())

                emit(
                    bufferRed
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(bytes: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
        try {
            socket.outputStream.write(bytes)
            Log.e("HELLOME", "Sent: " + bytes.size.toString())

        } catch (e: IOException) {
                return@withContext false
        }

        true
        }
    }
}
