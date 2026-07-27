package com.invincible.jedishare.data.chat

import timber.log.Timber

import android.util.Log
import com.invincible.jedishare.domain.chat.FileInfo
import org.json.JSONObject

/**
 * Extension functions for serializing [FileInfo] over the wire.
 *
 * Fix (TODO-03): Replaced Java [ObjectInputStream]/[ObjectOutputStream] serialization
 * with JSON encoding via Android's built-in [org.json.JSONObject].
 *
 * Rationale:
 *  - Java serialization is unsafe (gadget chain attacks, class version mismatches).
 *  - JSONObject is available on all Android API levels without added dependencies.
 *  - The wire format is now human-readable and debuggable.
 */

private const val TAG = "BluetoothFileMapper"

/** Serializes this [FileInfo] to a compact JSON UTF-8 byte array. */
fun FileInfo.toByteArray(): ByteArray? = runCatching {
    Timber.d("version - toByteArray called")
    JSONObject().apply {
        put("fileName", fileName ?: JSONObject.NULL)
        put("format",   format   ?: JSONObject.NULL)
        put("size",     size     ?: JSONObject.NULL)
        put("mimeType", mimeType ?: JSONObject.NULL)
    }.toString().encodeToByteArray()
}.onFailure { Log.e(TAG, "FileInfo serialization failed", it) }.getOrNull()

/** Deserializes a UTF-8 JSON byte array back to [FileInfo], or null on error. */
fun ByteArray.toFileInfo(): FileInfo? = runCatching {
    Timber.d("version - toFileInfo called")
    val json = JSONObject(this.decodeToString())
    FileInfo(
        fileName = json.optString("fileName").takeIf { it.isNotEmpty() },
        format   = json.optString("format").takeIf   { it.isNotEmpty() },
        size     = json.optString("size").takeIf     { it.isNotEmpty() },
        mimeType = json.optString("mimeType").takeIf { it.isNotEmpty() }
    )
}.onFailure { Log.e(TAG, "FileInfo deserialization failed", it) }.getOrNull()