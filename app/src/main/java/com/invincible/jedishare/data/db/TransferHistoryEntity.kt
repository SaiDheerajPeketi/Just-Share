package com.invincible.jedishare.data.db

import timber.log.Timber

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single completed file transfer in the history log.
 */
@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Display name of the transferred file. */
    @ColumnInfo(name = "file_name")
    val fileName: String,

    /** MIME type of the transferred file (e.g. "image/jpeg"). */
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,

    /** File size in bytes. */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    /** Whether this device was the sender (true) or receiver (false). */
    @ColumnInfo(name = "is_sender")
    val isSender: Boolean,

    /** Transfer method: "Bluetooth" or "WiFi-Direct". */
    @ColumnInfo(name = "transfer_method")
    val transferMethod: String,

    /** Name of the remote device. */
    @ColumnInfo(name = "remote_device_name")
    val remoteDeviceName: String?,

    /** The URI of the file on the device, to allow opening it from history. */
    @ColumnInfo(name = "content_uri")
    val contentUri: String? = null,

    /** Unix epoch timestamp (ms) when the transfer completed. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long = System.currentTimeMillis()
)
