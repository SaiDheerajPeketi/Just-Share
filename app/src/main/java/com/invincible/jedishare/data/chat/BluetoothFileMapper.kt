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
    val jsonObj = toJsonObject()
    jsonObj.toString().encodeToByteArray()
}.onFailure { Log.e(TAG, "FileInfo serialization failed", it) }.getOrNull()

private fun FileInfo.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("fileName", fileName ?: JSONObject.NULL)
        put("format",   format   ?: JSONObject.NULL)
        put("size",     size     ?: JSONObject.NULL)
        put("mimeType", mimeType ?: JSONObject.NULL)
        if (manifest != null) {
            val manifestArray = org.json.JSONArray()
            manifest.forEach { manifestArray.put(it.toJsonObject()) }
            put("manifest", manifestArray)
        }
    }
}

/** Deserializes a UTF-8 JSON byte array back to [FileInfo], or null on error. */
fun ByteArray.toFileInfo(): FileInfo? = runCatching {
    Timber.d("version - toFileInfo called")
    val json = JSONObject(this.decodeToString())
    FileInfo(
        fileName = json.optString("fileName").takeIf { it.isNotEmpty() && it != "null" },
        format   = json.optString("format").takeIf   { it.isNotEmpty() && it != "null" },
        size     = json.optString("size").takeIf     { it.isNotEmpty() && it != "null" },
        mimeType = json.optString("mimeType").takeIf { it.isNotEmpty() && it != "null" },
        manifest = json.optJSONArray("manifest")?.let { jsonArray ->
            val list = mutableListOf<FileInfo>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i)
                if (item != null) {
                    list.add(
                        FileInfo(
                            fileName = item.optString("fileName").takeIf { it.isNotEmpty() && it != "null" },
                            format   = item.optString("format").takeIf   { it.isNotEmpty() && it != "null" },
                            size     = item.optString("size").takeIf     { it.isNotEmpty() && it != "null" },
                            mimeType = item.optString("mimeType").takeIf { it.isNotEmpty() && it != "null" }
                        )
                    )
                }
            }
            list.ifEmpty { null }
        }
    )
}.onFailure { Log.e(TAG, "FileInfo deserialization failed", it) }.getOrNull()