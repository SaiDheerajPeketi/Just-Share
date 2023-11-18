package com.invincible.jedishare.data.chat

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.invincible.jedishare.domain.chat.BluetoothMessage
import com.invincible.jedishare.presentation.Image
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

fun String.toBluetoothMessage(isFromLocalUser: Boolean): BluetoothMessage {
    val name = substringBeforeLast("#")
    val message = substringAfter("#")
    return BluetoothMessage(
        message = message,
        senderName = name,
        isFromLocalUser = isFromLocalUser
    )
}

fun BluetoothMessage.toByteArray(): ByteArray {
    return "$senderName#$message".encodeToByteArray()
}

fun Image.toByteArray(context: Context, image: Image): ByteArray? {
    val contentResolver: ContentResolver = context.contentResolver
    try {
        val inputStream: InputStream? = contentResolver.openInputStream(image.uri)
        inputStream?.use {
            val bitmap = BitmapFactory.decodeStream(it)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            return byteArrayOutputStream.toByteArray()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun ByteArray.toImage(context: Context, byteArray: ByteArray): Image? {
    try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageName = "IMG_$timestamp.png"

        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val file = File(directory, imageName)

        val uri = Uri.fromFile(file)
        val outputStream: OutputStream = FileOutputStream(file)
        outputStream.write(byteArray)
        outputStream.close()

        return Image(id = System.currentTimeMillis(), name = imageName, uri = uri)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}