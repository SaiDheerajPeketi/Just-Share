package com.invincible.jedishare.data.chat
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.invincible.jedishare.domain.chat.BluetoothController
import com.invincible.jedishare.domain.chat.BluetoothDeviceDomain
import com.invincible.jedishare.domain.chat.BluetoothMessage
import com.invincible.jedishare.domain.chat.ConnectionResult
import com.invincible.jedishare.domain.chat.FileInfo
import com.invincible.jedishare.getFileDetailsFromUri
import com.invincible.jedishare.presentation.BluetoothViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context
): BluetoothController {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var dataTransferService: BluetoothDataTransferService? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if(newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if(bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Can't connect to a non-paired device.")
            }
        }
    }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    override fun startDiscovery() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    override fun startBluetoothServer(viewModel: BluetoothViewModel): Flow<ConnectionResult> {
        return flow {
            if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT permission")
            }

            currentServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                "chat_service",
                UUID.fromString(SERVICE_UUID)
            )

            var shouldLoop = true
            while(shouldLoop) {
                currentClientSocket = try {
                    currentServerSocket?.accept()
                } catch(e: IOException) {
                    shouldLoop = false
                    null
                }
                emit(ConnectionResult.ConnectionEstablished)
                currentClientSocket?.let {
                    currentServerSocket?.close()
                    val service = BluetoothDataTransferService(it)
                    dataTransferService = service

//                    service.listenForIncomingMessages().

                    emitAll(
                        service
                            .listenForIncomingMessages(viewModel)
                            .map {
                                ConnectionResult.TransferSucceeded(it)
                            }
                    )
                }
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    override fun connectToDevice(device: BluetoothDeviceDomain, viewModel: BluetoothViewModel): Flow<ConnectionResult> {
        return flow {
            if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT permission")
            }

            currentClientSocket = bluetoothAdapter
                ?.getRemoteDevice(device.address)
                ?.createRfcommSocketToServiceRecord(
                    UUID.fromString(SERVICE_UUID)
                )
            stopDiscovery()

            currentClientSocket?.let { socket ->
                try {
                    socket.connect()
                    emit(ConnectionResult.ConnectionEstablished)

                    BluetoothDataTransferService(socket).also {
                        dataTransferService = it
                        emitAll(
                            it.listenForIncomingMessages(viewModel)
                                .map { ConnectionResult.TransferSucceeded(it) }
                        )
                    }
                } catch(e: IOException) {
                    socket.close()
                    currentClientSocket = null
                    emit(ConnectionResult.Error("Connection was interrupted"))
                }
            }
        }.onCompletion {
            closeConnection()
        }.flowOn(Dispatchers.IO)
    }

    val FILE_DELIMITER = "----FILE_DELIMITER----"
    val repeatedString = FILE_DELIMITER.repeat(40)

    override suspend fun trySendMessage(
        message: String,
        iterationCountFlow: MutableSharedFlow<Long>, // Use SharedFlow to emit values
        viewModel: BluetoothViewModel
        ): BluetoothMessage? {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)){
            return null
        }

        if(dataTransferService == null){
            return null
        }

        val bluetoothMessage = BluetoothMessage(
            message = message,
            senderName = bluetoothAdapter?.name ?:"Unknown name",
            isFromLocalUser = true
        )


        val uriList = viewModel.getUriList()

        for(uri in uriList){
//            delay(500)
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            var fileInfo: FileInfo? = null

            // Get file information
            fileInfo = getFileDetailsFromUri(uri, context.contentResolver)
            viewModel.setFileInfo(fileInfo.size?.toLong())
//            delay(500)
            fileInfo.toByteArray()?.let { dataTransferService?.sendMessage(it) }
            delay(2000)

//            delay(1000)

            stream.use { inputStream ->
                val buffer = ByteArray(990)
                var bytesRead: Int = 0
                var iterationCount = 0L

                // Add file delimiter before sending the file
//                dataTransferService?.sendMessage(FILE_DELIMITER.toByteArray())

                while (inputStream?.read(buffer).also {
                        if (it != null) {
                            bytesRead = it
                            iterationCountFlow.emit(iterationCount)
                        }
                    } != -1) {
                    Log.e("MYTAG", "Bytes Read : " + bytesRead.toString())
                    delay(10)
                    dataTransferService?.sendMessage(buffer.copyOfRange(0, bytesRead))
                    delay(10)

                    iterationCount++
                }

//                delay(1000)

                // Add file delimiter after sending the file
//                dataTransferService?.sendMessage(FILE_DELIMITER.toByteArray())
                delay(2000)
                dataTransferService?.sendMessage(repeatedString.toByteArray())
                delay(5000)


//                delay(1000)
            }

            Log.e("MYTAG", "DELAY")
//            delay(1000)

        }

//        val stream: InputStream? = context.contentResolver.openInputStream(Uri.parse(message))
//        var fileInfo: FileInfo? = null
//        var byteArray: ByteArray
//
//        // Get file information
//        fileInfo = getFileDetailsFromUri(Uri.parse(message), context.contentResolver)
//        viewModel.setFileInfo(fileInfo.size?.toLong())
//        fileInfo.toByteArray()?.let { dataTransferService?.sendMessage(it) }
//
//        stream.use { inputStream ->
//            val outputStream = ByteArrayOutputStream()
//            val buffer = ByteArray(990)
//            var bytesRead: Int
//            bytesRead = 0
//
//            var iterationCount = 0L // Declare and initialize iterationCount before the loop
//
//            while (inputStream?.read(buffer).also {
//                    if (it != null) {
//                        bytesRead = it
//                        iterationCountFlow.emit(iterationCount) // Emit iteration count
//                    }
//                } != -1) {
////                outputStream.write(buffer, 0, bytesRead)
//                Log.e("HELLOME", "Bytes Read : " + bytesRead.toString())
//                dataTransferService?.sendMessage(buffer.copyOfRange(0, bytesRead))
//
//                iterationCount++
//            }
////
////            byteArray = outputStream.toByteArray()
////            Log.e("HELLOME", "IN ByteArray = " + byteArray.size.toString())
//        }



        // Serialize FileInfo and image data to byte array
//        val fileData = fileInfo?.let { FileData(it, byteArray) }
//        Log.e("HELLOME","BYTE ARRAY SIZE: " + byteArray.size.toString())
//        fileData?.toByteArray()?.let { dataTransferService?.sendMessage(it) }
//            dataTransferService?.sendMessage(byteArray)
//        dataTransferService?.sendMessage(bluetoothMessage.toByteArray())

        return bluetoothMessage
    }

    override fun closeConnection() {
        currentClientSocket?.close()
        currentServerSocket?.close()
        currentClientSocket = null
        currentServerSocket = null
    }

    override fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
        context.unregisterReceiver(bluetoothStateReceiver)
        closeConnection()
    }

    private fun updatePairedDevices() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toBluetoothDeviceDomain() }
            ?.also { devices ->
                _pairedDevices.update { devices }
            }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
    }
}