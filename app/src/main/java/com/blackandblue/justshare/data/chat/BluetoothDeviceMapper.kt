package com.blackandblue.justshare.data.chat

import timber.log.Timber

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.blackandblue.justshare.domain.chat.BluetoothDeviceDomain


@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    Timber.d("Global - toBluetoothDeviceDomain called")
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}