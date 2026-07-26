package com.invincible.jedishare

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.invincible.jedishare.domain.chat.FileInfo

// ─────────────────────────────────────────────────────────────────────────────
// FILE UTILITIES
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns [FileInfo] (name, format, size, mimeType) for the given [uri].
 * Fix: uses try-catch for robustness; mimeType is resolved from extension if missing.
 */
fun getFileDetailsFromUri(uri: Uri, contentResolver: ContentResolver): FileInfo {
    var fileName: String? = null
    var format: String? = null
    var size: String? = null
    var mimeType: String? = null

    try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

            if (cursor.moveToFirst()) {
                fileName = if (nameColumn >= 0) cursor.getString(nameColumn) else null
                format = fileName?.substringAfterLast('.', "")
                size = if (sizeColumn >= 0) cursor.getLong(sizeColumn).toString() else null
                mimeType = if (mimeTypeColumn >= 0) cursor.getString(mimeTypeColumn) else null

                if (mimeType.isNullOrBlank()) {
                    mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(format?.lowercase())
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Error querying URI: $uri", e)
    }

    return FileInfo(fileName, format, size, mimeType)
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
fun bytesToHumanReadableSize(bytes: Double): String = when {
    bytes >= 1_073_741_824.0 -> "%.1f GB".format(bytes / 1_073_741_824.0) // 1024^3
    bytes >= 1_048_576.0     -> "%.1f MB".format(bytes / 1_048_576.0)     // 1024^2
    bytes >= 1_024.0         -> "%.0f kB".format(bytes / 1_024.0)         // 1024^1
    else                     -> "${bytes.toLong()} B"
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
