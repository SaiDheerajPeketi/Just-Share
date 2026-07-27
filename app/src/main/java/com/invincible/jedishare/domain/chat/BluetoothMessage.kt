package com.invincible.jedishare.domain.chat

import timber.log.Timber

data class BluetoothMessage(
    val message: String,
    val senderName: String,
    val isFromLocalUser: Boolean
)