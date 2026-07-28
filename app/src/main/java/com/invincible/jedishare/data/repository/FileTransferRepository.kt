package com.invincible.jedishare.data.repository

import timber.log.Timber

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.invincible.jedishare.domain.chat.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository responsible for writing received file data to MediaStore.
 * Removes all File I/O from BluetoothViewModel, keeping the ViewModel clean and testable.
 */
class FileTransferRepository @Inject constructor(
    private val contentResolver: ContentResolver
) {

    /**
     * Creates a new MediaStore entry for the incoming file and returns its URI.
     * Must be called before writing any chunks.
     *
     * @param fileInfo Metadata about the incoming file (name, mime type, etc.)
     * @return The URI of the newly created MediaStore entry, or null on failure.
     */
    suspend fun createMediaStoreEntry(fileInfo: FileInfo): Uri? = withContext(Dispatchers.IO) {
        Timber.d("FileTransferRepository - createMediaStoreEntry called")
        val mimeType = fileInfo.mimeType ?: "*/*"
        val fileName = fileInfo.fileName ?: "received_file"

        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                when {
                    mimeType.startsWith("image/") -> put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/JustShare"
                    )
                    mimeType.startsWith("audio/") -> put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/JustShare"
                    )
                    mimeType.startsWith("video/") -> put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/JustShare"
                    )
                    else -> put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/JustShare"
                    )
                }
            }
        }

        val contentUri = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        }

        try {
            contentResolver.insert(contentUri, values)
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to create MediaStore entry", e)
            null
        }
    }

    private var currentOutputStream: java.io.OutputStream? = null
    private var currentFileUri: Uri? = null

    /**
     * Opens an output stream to the given MediaStore URI.
     */
    suspend fun openFile(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        Timber.d("FileTransferRepository - openFile called")
        try {
            currentOutputStream = contentResolver.openOutputStream(fileUri, "wa")
            if (currentOutputStream != null) {
                currentFileUri = fileUri
                true
            } else false
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to open output stream for $fileUri", e)
            false
        }
    }

    /**
     * Appends a chunk of bytes to the currently open MediaStore entry.
     */
    suspend fun appendChunkToFile(chunk: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            currentOutputStream?.write(chunk)
            true
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to write chunk", e)
            false
        }
    }

    /**
     * Closes the currently open MediaStore entry and clears the IS_PENDING flag.
     */
    suspend fun closeFile() = withContext(Dispatchers.IO) {
        Timber.d("FileTransferRepository - closeFile called")
        try {
            currentOutputStream?.flush()
            currentOutputStream?.close()
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Error closing stream", e)
        } finally {
            currentOutputStream = null
            
            // If API >= 29, clear the IS_PENDING flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && currentFileUri != null) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(currentFileUri!!, values, null, null)
                } catch (e: Exception) {
                    Log.e("FileTransferRepository", "Failed to clear IS_PENDING for $currentFileUri", e)
                }
            }
            currentFileUri = null
        }
    }

    /**
     * Deletes a file by its URI.
     */
    suspend fun deleteFile(uri: Uri) = withContext(Dispatchers.IO) {
        Timber.d("FileTransferRepository - deleteFile called")
        try {
            contentResolver.delete(uri, null, null)
            Log.d("FileTransferRepository", "Deleted partial file: $uri")
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to delete file", e)
        }
    }
}
