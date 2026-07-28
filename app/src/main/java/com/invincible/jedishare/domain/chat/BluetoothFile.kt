package com.invincible.jedishare.domain.chat

import timber.log.Timber

import java.io.Serializable

data class FileInfo(
    val fileName: String?,
    val format: String?,
    val size: String?,
    val mimeType: String?,
    val uri: String? = null,
    val manifest: List<FileInfo>? = null
) : Serializable

data class FileData(
    val fileInfo: FileInfo,
    val imageData: ByteArray,
    var isFromLocalUser: Boolean = true
) : Serializable
