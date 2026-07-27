package com.invincible.jedishare.data.chat

import timber.log.Timber

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Concrete implementation of [BluetoothController].
 *
 * MVVM fixes applied:
 * - No ViewModel references anywhere in this class.
 * - [startBluetoothServer] and [connectToDevice] emit [ConnectionResult] sealed types.
 * - The receiving side distinguishes FileMetadata vs FileChunk via [IncomingData] sealed type
 *   emitted by [BluetoothDataTransferService], and wraps them into [ConnectionResult] for
 *   the ViewModel to consume and route to [com.invincible.jedishare.data.repository.FileTransferRepository].
 * - [trySendMessage] accepts URI list and callbacks instead of a ViewModel parameter.
 * - Uses [BluetoothDataTransferService.CHUNK_SIZE] (8KB) for streaming, with proper IO-dispatched reads.
 */
@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context
) : BluetoothController {

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a6d1-2459987e5e2d"
        private const val TAG = "BTController"
    }

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var dataTransferService: BluetoothDataTransferService? = null
    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = _pairedDevices.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        // We no longer update _isConnected here because generic OS-level connections (e.g. from Settings)
        // do not guarantee that our RFCOMM socket is established. _isConnected will be updated
        // internally by connectToDevice() and startBluetoothServer().
    }

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    override fun startDiscovery() {
        Timber.d("AndroidBluetoothController - startDiscovery called")
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND)
        )
        updatePairedDevices()
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        Timber.d("AndroidBluetoothController - stopDiscovery called")
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        bluetoothAdapter?.cancelDiscovery()
    }

    // ── Server / Client Flows ─────────────────────────────────────────────────

    override fun startBluetoothServer(): Flow<ConnectionResult> = flow {
        Timber.d("AndroidBluetoothController - startBluetoothServer called")
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            throw SecurityException("No BLUETOOTH_CONNECT permission")
        }
        currentServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
            "jedi_share_service", UUID.fromString(SERVICE_UUID)
        )
        var shouldLoop = true
        while (shouldLoop) {
            currentClientSocket = try {
                currentServerSocket?.accept()
            } catch (e: IOException) {
                shouldLoop = false
                null
            }
            if (currentClientSocket != null) {
                val service = BluetoothDataTransferService(currentClientSocket!!)
                dataTransferService = service
                _isConnected.update { true }
                emit(ConnectionResult.ConnectionEstablished)
                currentServerSocket?.close()
                emitAll(
                    service.listenForIncomingMessages().map { incomingData ->
                        incomingData.toConnectionResult()
                    }
                )
            }
        }
    }.onCompletion { closeConnection() }.flowOn(Dispatchers.IO)

    override fun connectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult> = flow {
        Timber.d("AndroidBluetoothController - connectToDevice called")
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            throw SecurityException("No BLUETOOTH_CONNECT permission")
        }
        val remoteDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        stopDiscovery()

        // Try secure connection first; fall back to insecure on failure (TODO-17)
        var socket: android.bluetooth.BluetoothSocket? = try {
            remoteDevice?.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID))
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create secure RFCOMM socket", e)
            null
        }
        currentClientSocket = socket

        socket?.let { secureSocket ->
            try {
                secureSocket.connect()
                val service = BluetoothDataTransferService(secureSocket)
                dataTransferService = service
                _isConnected.update { true }
                emit(ConnectionResult.ConnectionEstablished)
                emitAll(service.listenForIncomingMessages().map { it.toConnectionResult() })
            } catch (secureEx: IOException) {
                Log.w(TAG, "Secure connect failed, trying insecure fallback: ${secureEx.message}")
                secureSocket.close()

                // Insecure fallback
                val insecureSocket = try {
                    remoteDevice?.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID))
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to create insecure RFCOMM socket", e)
                    null
                }
                currentClientSocket = insecureSocket

                if (insecureSocket != null) {
                    try {
                        insecureSocket.connect()
                        val service = BluetoothDataTransferService(insecureSocket)
                        dataTransferService = service
                        _isConnected.update { true }
                        emit(ConnectionResult.ConnectionEstablished)
                        emitAll(service.listenForIncomingMessages().map { it.toConnectionResult() })
                    } catch (insecureEx: IOException) {
                        insecureSocket.close()
                        currentClientSocket = null
                        emit(ConnectionResult.Error("Connection failed (secure + insecure): ${insecureEx.message}"))
                    }
                } else {
                    emit(ConnectionResult.Error("Could not create Bluetooth socket"))
                }
            }
        } ?: emit(ConnectionResult.Error("Remote device not found"))
    }.onCompletion { closeConnection() }.flowOn(Dispatchers.IO)


    // ── Sending ───────────────────────────────────────────────────────────────

    /**
     * Sends all files in [uriList] over the active Bluetooth connection.
     *
     * Protocol per file:
     *  1. Send 4-byte metadata size.
     *  2. Send serialized [FileInfo] metadata.
     *  3. Send 8-byte file size.
     *  4. Stream raw file bytes in [BluetoothDataTransferService.CHUNK_SIZE]-byte chunks.
     *
     * Removes the 10ms + 1000ms busy-wait delays from the old implementation.
     * Calls [onFileSizeResolved] and [onFileCountUpdated] callbacks for ViewModel progress tracking.
     */
    override suspend fun trySendMessage(
        uriList: List<Uri>,
        iterationCountFlow: MutableSharedFlow<Long>,
        onFileSizeResolved: (Long) -> Unit,
        onFileCountUpdated: (Int) -> Unit,
        onFileInfoResolved: (FileInfo) -> Unit,
        onBytesSent: (Long) -> Unit,
        onFileSent: (FileInfo) -> Unit
    ): BluetoothMessage? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@withContext null
        if (dataTransferService == null) return@withContext null

        var fileCount = 0
        for (uri in uriList) {
            val fileInfo: FileInfo = getFileDetailsFromUri(uri, context.contentResolver)
            onFileInfoResolved(fileInfo)
            onFileSizeResolved(fileInfo.size?.toLongOrNull() ?: 1L)

            val metaBytes = fileInfo.toByteArray() ?: ByteArray(0)
            val metaSizeBuffer = java.nio.ByteBuffer.allocate(4).putInt(metaBytes.size).array()
            dataTransferService?.sendMessage(metaSizeBuffer)
            dataTransferService?.sendMessage(metaBytes)

            val fileSize = fileInfo.size?.toLongOrNull() ?: 0L
            val fileSizeBuffer = java.nio.ByteBuffer.allocate(8).putLong(fileSize).array()
            dataTransferService?.sendMessage(fileSizeBuffer)

            // Stream file bytes
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(BluetoothDataTransferService.CHUNK_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    dataTransferService?.sendMessage(buffer.copyOf(bytesRead))
                    val sentBytes = bytesRead.toLong()
                    onBytesSent(sentBytes)
                    iterationCountFlow.tryEmit(sentBytes)
                }
            } ?: Log.e(TAG, "Could not open input stream for $uri")
            onFileCountUpdated(++fileCount)
            Log.d(TAG, "File $fileCount sent: $uri")
            onFileSent(fileInfo)
        }

        return@withContext BluetoothMessage(
            message = "Sent ${uriList.size} file(s)",
            senderName = bluetoothAdapter?.name ?: "Unknown",
            isFromLocalUser = true
        )
    }

    // ── Connection Management ─────────────────────────────────────────────────

    override fun closeConnection() {
        Timber.d("AndroidBluetoothController - closeConnection called")
        _isConnected.update { false }
        currentClientSocket?.close()
        currentServerSocket?.close()
        currentClientSocket = null
        currentServerSocket = null
    }

    override fun release() {
        Timber.d("AndroidBluetoothController - release called")
        try { context.unregisterReceiver(foundDeviceReceiver) } catch (e: Exception) { Log.w(TAG, "foundDeviceReceiver already unregistered") }
        try { context.unregisterReceiver(bluetoothStateReceiver) } catch (e: Exception) { Log.w(TAG, "bluetoothStateReceiver already unregistered") }
        closeConnection()
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private fun updatePairedDevices() {
        Timber.d("AndroidBluetoothController - updatePairedDevices called")
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        bluetoothAdapter?.bondedDevices
            ?.map { it.toBluetoothDeviceDomain() }
            ?.also { devices -> _pairedDevices.update { devices } }
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Maps an [IncomingData] event to a [ConnectionResult] for the ViewModel. */
    private fun IncomingData.toConnectionResult(): ConnectionResult {
        Timber.d("AndroidBluetoothController - toConnectionResult called")
        return when (this) {
            is IncomingData.FileMetadata -> ConnectionResult.TransferSucceeded(bytes)
            is IncomingData.FileChunk -> ConnectionResult.TransferSucceeded(bytes)
            is IncomingData.EndOfFile -> ConnectionResult.EndOfFile
        }
    }
}
