package com.invincible.jedishare.domain.chat

import timber.log.Timber

typealias BluetoothDeviceDomain = BluetoothDevice

data class BluetoothDevice(
    val name: String?,
    val address: String
)