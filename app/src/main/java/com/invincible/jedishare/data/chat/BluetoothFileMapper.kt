package com.invincible.jedishare.data.chat

import com.invincible.jedishare.domain.chat.FileData
import com.invincible.jedishare.domain.chat.FileInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

fun FileData.toByteArray(): ByteArray? {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(this)
    objectOutputStream.close()
    return byteArrayOutputStream.toByteArray()
}

fun FileInfo.toByteArray(): ByteArray?{
    val byteArrayOutputStream = ByteArrayOutputStream()
    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(this)
    objectOutputStream.close()
    return byteArrayOutputStream.toByteArray()
}

fun ByteArray.toFileInfo(): FileInfo?{
    val byteArrayInputStream = ByteArrayInputStream(this)
    val objectInputStream = ObjectInputStream(byteArrayInputStream)
    val obj = objectInputStream.readObject() as? FileInfo
    objectInputStream.close()
    return obj
}

fun ByteArray.toFileData(isFromLocalUser: Boolean): FileData? {
    val byteArrayInputStream = ByteArrayInputStream(this)
    val objectInputStream = ObjectInputStream(byteArrayInputStream)
    val obj = objectInputStream.readObject() as? FileData
    if (obj != null) {
        obj.isFromLocalUser = isFromLocalUser
    }
    objectInputStream.close()
    return obj
}