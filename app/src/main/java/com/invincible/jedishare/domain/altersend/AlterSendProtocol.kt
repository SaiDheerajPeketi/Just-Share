package com.invincible.jedishare.domain.altersend

import java.security.SecureRandom
import kotlin.math.ceil
import kotlin.math.min

object AlterSendProtocol {
    const val DRIVE_PROTOCOL = "altersend/drive"
    const val TOPIC_BYTES = 32
    const val TOPIC_HEX_LENGTH = TOPIC_BYTES * 2

    private const val KB = 1024L
    private const val MB = 1024L * KB
    private const val GB = 1024L * MB

    private val secureRandom = SecureRandom()
    private val topicRegex = Regex("^[0-9a-fA-F]{$TOPIC_HEX_LENGTH}$")

    fun generateTopicHex(): String {
        val bytes = ByteArray(TOPIC_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    fun isValidTopicHex(value: String): Boolean = topicRegex.matches(value)

    fun normalizeTopicHex(value: String): String {
        val normalized = value.trim().lowercase()
        require(isValidTopicHex(normalized)) { "Invalid Remote Transfer topic" }
        return normalized
    }

    fun selectChunkSize(fileSizeBytes: Long): Int = when {
        fileSizeBytes < 1L * MB -> (64L * KB).toInt()
        fileSizeBytes < 100L * MB -> (256L * KB).toInt()
        fileSizeBytes < 10L * GB -> (1L * MB).toInt()
        else -> (4L * MB).toInt()
    }

    fun chunkCount(fileSizeBytes: Long, chunkSizeBytes: Int = selectChunkSize(fileSizeBytes)): Int {
        if (fileSizeBytes <= 0L) return 0
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be positive" }
        return ceil(fileSizeBytes.toDouble() / chunkSizeBytes.toDouble()).toInt()
    }

    fun chunkRange(
        index: Int,
        fileSizeBytes: Long,
        chunkSizeBytes: Int = selectChunkSize(fileSizeBytes)
    ): AlterSendChunkRange {
        require(index >= 0) { "Chunk index must be non-negative" }
        require(fileSizeBytes >= 0L) { "File size must be non-negative" }
        require(chunkSizeBytes > 0) { "chunkSizeBytes must be positive" }

        val offset = index.toLong() * chunkSizeBytes.toLong()
        if (offset >= fileSizeBytes) {
            return AlterSendChunkRange(offset = fileSizeBytes, length = 0)
        }
        return AlterSendChunkRange(
            offset = offset,
            length = min(chunkSizeBytes.toLong(), fileSizeBytes - offset).toInt()
        )
    }
}

data class AlterSendChunkRange(
    val offset: Long,
    val length: Int
)

fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
