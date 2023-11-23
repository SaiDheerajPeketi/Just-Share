package com.invincible.jedishare.domain.chat

import com.invincible.jedishare.presentation.BluetoothViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDevice>>
    val pairedDevices: StateFlow<List<BluetoothDevice>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()

    fun startBluetoothServer(viewModel: BluetoothViewModel): Flow<ConnectionResult>
    fun connectToDevice(device: BluetoothDevice, viewModel: BluetoothViewModel): Flow<ConnectionResult>

    suspend fun trySendMessage(message: String,
                               iterationCountFlow: MutableSharedFlow<Long>, // Use SharedFlow to emit values
                               viewModel: BluetoothViewModel
                               ): BluetoothMessage?

    fun closeConnection()
    fun release()
}