package com.invincible.jedishare.domain.chat

import android.net.Uri

data class BluetoothImage(
    val uri: Uri,
    val senderName: String,
    val isFromLocalUser: Boolean
)
