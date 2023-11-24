package com.invincible.jedishare.domain.chat

import java.io.Serializable

data class FileInfo(
    val fileName: String?,
    val format: String?,
    val size: String?,
    val mimeType: String?
) : Serializable

data class FileData(
    val fileInfo: FileInfo,
    val imageData: ByteArray,
    var isFromLocalUser: Boolean = true
) : Serializable
