package com.invincible.jedishare.data.chat

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.invincible.jedishare.domain.chat.BluetoothMessage
import com.invincible.jedishare.domain.chat.TransferFailedException
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.components.CustomProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

const val BUFFER_SIZE = 990 // Adjust the buffer size as needed
const val FILE_DELIMITER = "----FILE_DELIMITER----"

class BluetoothDataTransferService(
    private val socket: BluetoothSocket
) {

    private val incomingDataStream = ByteArrayOutputStream()

    fun listenForIncomingMessages(viewModel: BluetoothViewModel): Flow<ByteArray> {
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

                processIncomingData(bufferRed)
                checkForFiles(viewModel)?.let { fileBytes ->
//                    emit(fileBytes)
                }

                emit(
                    bufferRed
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun processIncomingData(data: ByteArray) {
        incomingDataStream.write(data)
    }

    private fun checkForFiles(viewModel: BluetoothViewModel?): ByteArray? {
        val data = incomingDataStream.toByteArray()
        val delimiterIndex = findIndexOfSubArray(incomingDataStream.toByteArray(), FILE_DELIMITER.toByteArray())


//        Log.e("MYTAG","delimiter size" + FILE_DELIMITER.toByteArray().size.toString())
        Log.e("MYTAG","byte array size" + data.size)

//        val delimiterIndex = data.indexOf(FILE_DELIMITER.toByteArray())
        if (delimiterIndex != -1) {
//            val fileBytes = data.copyOfRange(0, delimiterIndex)
            incomingDataStream.reset()
            Log.e("MYTAG","END OF FILE" + delimiterIndex)
            Log.e("MYTAG","main array size" + incomingDataStream.toByteArray().size.toString())
            Log.e("MYTAG","delimiter size" + FILE_DELIMITER.toByteArray().size.toString())
            viewModel?.isFirst = true
            incomingDataStream.write(data, delimiterIndex + FILE_DELIMITER.length, data.size - (delimiterIndex + FILE_DELIMITER.length))
//            return fileBytes
            incomingDataStream.reset()
            return null
            return incomingDataStream.toByteArray()
        }
        incomingDataStream.reset()
        return data
    }

    fun findIndexOfSubArray(mainArray: ByteArray, subArray: ByteArray): Int {
        if(mainArray.size == 22)
            return 0
//        for (i in 0 until mainArray.size - subArray.size + 1) {
//            if (mainArray.copyOfRange(i, i + subArray.size).contentEquals(subArray)) {
//                return i
//            }
//        }
        return -1 // Return -1 if subArray is not found in mainArray
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
