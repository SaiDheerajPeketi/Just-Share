package com.invincible.jedishare

import timber.log.Timber

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.media.MediaScannerConnection
import com.invincible.jedishare.domain.chat.FileInfo

// ─────────────────────────────────────────────────────────────────────────────
// PERMISSIONS
// ─────────────────────────────────────────────────────────────────────────────

fun hasAllRequiredPermissions(context: android.content.Context): Boolean {
    val storagePerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    val btPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        listOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val wifiPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val notifPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    fun checkPerms(perms: List<String>) = perms.all { 
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
    }

    val storageGranted = checkPerms(storagePerms)
    val btGranted = checkPerms(btPerms)
    val wifiGranted = checkPerms(wifiPerms)
    val notifGranted = checkPerms(notifPerms)

    return storageGranted && (notifPerms.isEmpty() || notifGranted) && (btGranted || wifiGranted)
}

// ─────────────────────────────────────────────────────────────────────────────
// FILE UTILITIES
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns [FileInfo] (name, format, size, mimeType) for the given [uri].
 * Fix: uses try-catch for robustness; mimeType is resolved from extension if missing.
 */
fun getFileDetailsFromUri(uri: Uri, contentResolver: ContentResolver): FileInfo {
    Timber.d("Global - getFileDetailsFromUri called")
    var fileName: String? = null
    var format: String? = null
    var size: String? = null
    var mimeType: String? = contentResolver.getType(uri)

    try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

            if (cursor.moveToFirst()) {
                fileName = if (nameColumn >= 0) cursor.getString(nameColumn) else null
                format = fileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                size = if (sizeColumn >= 0) cursor.getLong(sizeColumn).toString() else null
                mimeType = if (mimeTypeColumn >= 0) cursor.getString(mimeTypeColumn) else mimeType

                if (mimeType.isNullOrBlank()) {
                    mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(format?.lowercase())
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Error querying URI: $uri", e)
    }

    if (fileName.isNullOrBlank()) {
        fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
    if (format.isNullOrBlank()) {
        format = fileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }
    if (mimeType.isNullOrBlank()) {
        mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format?.lowercase())
    }
    if (size.isNullOrBlank() || size == "-1") {
        size = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }?.toString()
            }
        }.getOrNull()
    }


    return FileInfo(fileName, format, size, mimeType, uri.toString())
}

// ─────────────────────────────────────────────────────────────────────────────
// SIZE FORMATTING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a byte count to a human-readable string.
 *
 * Fix (TODO-08): The original implementation used integer shifts (`1 shl 30`) which evaluate
 * as [Int], causing silent overflow / precision loss for values > 2GB.
 * This version uses explicit [Long] literals (1024L * 1024 * 1024, etc.) to ensure
 * correct formatting for files up to several TB.
 */
fun bytesToHumanReadableSize(bytes: Double): String {
    Timber.d("Global - bytesToHumanReadableSize called")
    return when {
        bytes >= 1_073_741_824.0 -> "%.1f GB".format(bytes / 1_073_741_824.0) // 1024^3
        bytes >= 1_048_576.0     -> "%.1f MB".format(bytes / 1_048_576.0)     // 1024^2
        bytes >= 1_024.0         -> "%.0f kB".format(bytes / 1_024.0)         // 1024^1
        else                     -> "${bytes.toLong()} B"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FILE TYPE CLASSIFICATION
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a file extension to a display category.
 *
 * Fix: uses [String.lowercase] instead of deprecated [String.toLowerCase].
 */
fun classifyFileType(fileExtension: String): String {
    Timber.d("Global - classifyFileType called")
    val ext = fileExtension.lowercase()
    return when {
        ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "heic", "webp") -> "Photo"
        ext in listOf("mp4", "mov", "avi", "mkv", "wmv", "flv", "webm")   -> "Video"
        ext in listOf("mp3", "wav", "ogg", "aac", "flac", "m4a")          -> "Music"
        ext in listOf("pdf", "doc", "docx", "ppt", "pptx", "xls",
            "xlsx", "txt", "csv", "zip", "rar")                            -> "Document"
        else                                                                -> "Document"
    }
}

/**
 * Manually triggers the Android Media Scanner for a given content URI.
 * This ensures metadata like duration, dimensions, and size are properly extracted 
 * after the file is written to MediaStore.
 */
fun scanMediaUri(context: android.content.Context, uri: Uri) {
    try {
        var path: String? = null
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                if (dataIndex != -1) {
                    path = cursor.getString(dataIndex)
                }
            }
        }
        if (path != null) {
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> }
        }
    } catch (e: Exception) {
        Log.e("Utils", "Failed to scan media uri: $uri", e)
    }
}
