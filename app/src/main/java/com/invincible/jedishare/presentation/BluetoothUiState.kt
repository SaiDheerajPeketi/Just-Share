package com.invincible.jedishare.presentation
import com.invincible.jedishare.domain.chat.BluetoothDevice
import com.invincible.jedishare.domain.chat.BluetoothMessage

data class BluetoothUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: List<BluetoothMessage> = emptyList(),

    val currSize: Long = 1, // Add currSize to the state
    val globalSize: Long = -1
)