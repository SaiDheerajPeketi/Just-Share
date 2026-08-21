package com.blackandblue.justshare.presentation

import timber.log.Timber

import com.blackandblue.justshare.domain.chat.BluetoothDevice
import com.blackandblue.justshare.domain.chat.BluetoothMessage

/**
 * UI state for the Bluetooth device list and connection screens.
 *
 * Fix: Removed the progress fields (currSize, currSizeReceiver, globalSize) that were
 * previously embedded here, causing a single MutableStateFlow to carry both connection
 * state AND per-file progress state. Progress is now tracked via [TransferProgressState].
 */
data class BluetoothUiState(
    val scannedDevices: List<BluetoothDevice> = emptyList(),
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: List<BluetoothMessage> = emptyList()
)