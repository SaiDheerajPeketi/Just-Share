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
                when {
                    mimeType.startsWith("image/") -> put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/JediShare"
                    )
                    mimeType.startsWith("audio/") -> put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/JediShare"
                    )
                    mimeType.startsWith("video/") -> put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/JediShare"
                    )
                    else -> put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/JediShare"
                    )
                }
            }
        }

        val contentUri = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }

        try {
            contentResolver.insert(contentUri, values)
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to create MediaStore entry", e)
            null
        }
    }

    /**
     * Appends a chunk of bytes to an existing MediaStore entry in append mode ("wa").
     *
     * @param fileUri The URI returned by [createMediaStoreEntry].
     * @param chunk The byte array chunk to append.
     * @return True if the write succeeded, false otherwise.
     */
    suspend fun appendChunkToFile(fileUri: Uri, chunk: ByteArray): Boolean = withContext(Dispatchers.IO) {
        Timber.d("FileTransferRepository - appendChunkToFile called")
        try {
            contentResolver.openOutputStream(fileUri, "wa")?.use { outputStream ->
                outputStream.write(chunk)
            }
            true
        } catch (e: Exception) {
            Log.e("FileTransferRepository", "Failed to write chunk to $fileUri", e)
            false
        }
    }
}
