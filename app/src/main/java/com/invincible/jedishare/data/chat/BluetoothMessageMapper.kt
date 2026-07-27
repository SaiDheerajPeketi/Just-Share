package com.invincible.jedishare.data.chat

import timber.log.Timber

import com.invincible.jedishare.domain.chat.BluetoothMessage

/**
 * Extension functions for [BluetoothMessage] wire encoding.
 *
 * Fix (TODO-03): Removed the Image.toByteArray / ByteArray.toImage functions that
 * wrote directly to [android.os.Environment.getExternalStoragePublicDirectory] —
 * a pattern deprecated since API 29 and broken on API 30+.
 *
 * Image data is now handled by [com.invincible.jedishare.data.repository.FileTransferRepository]
 * which writes to MediaStore using [android.content.ContentResolver], the modern approach.
 */

/** Converts a raw "senderName#message" string to a [BluetoothMessage]. */
fun String.toBluetoothMessage(isFromLocalUser: Boolean): BluetoothMessage {
    Timber.d("Global - toBluetoothMessage called")
    val name    = substringBeforeLast("#")
    val message = substringAfterLast("#")
    return BluetoothMessage(
        message        = message,
        senderName     = name,
        isFromLocalUser = isFromLocalUser
    )
}

/** Encodes a [BluetoothMessage] as UTF-8 bytes in "senderName#message" format. */
fun BluetoothMessage.toByteArray(): ByteArray =
    "$senderName#$message".encodeToByteArray()