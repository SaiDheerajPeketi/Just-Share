package com.invincible.jedishare.domain.chat

import timber.log.Timber

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for all Bluetooth operations.
 *
 * MVVM fix: ViewModel is no longer passed as a parameter to any method here.
 * The data layer communicates exclusively through [Flow] / [StateFlow] / [SharedFlow].
 */
interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDevice>>
    val pairedDevices: StateFlow<List<BluetoothDevice>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()

    /** Opens a server socket and emits connection/transfer results as a cold Flow. */
    fun startBluetoothServer(): Flow<ConnectionResult>

    /** Connects to a remote device and emits connection/transfer results as a cold Flow. */
    fun connectToDevice(device: BluetoothDevice): Flow<ConnectionResult>

    /**
     * Sends the files represented by [uriList] over the active Bluetooth connection.
     * Emits each chunk iteration index to [iterationCountFlow] for progress tracking.
     *
     * @return A [BluetoothMessage] summary if send succeeded, or null on failure.
     */
    suspend fun trySendMessage(
        uriList: List<android.net.Uri>,
        iterationCountFlow: MutableSharedFlow<Long>,
        onFileSizeResolved: (Long) -> Unit,
        onFileCountUpdated: (Int) -> Unit,
        onBytesSent: (Long) -> Unit = {},
        onFileSent: (com.invincible.jedishare.domain.chat.FileInfo) -> Unit = {}
    ): BluetoothMessage?

    fun closeConnection()
    fun release()
}
