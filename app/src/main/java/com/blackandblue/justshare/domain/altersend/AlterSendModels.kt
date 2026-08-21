package com.blackandblue.justshare.domain.altersend

import android.net.Uri

enum class AlterSendRole {
    Sender,
    Receiver
}

enum class AlterSendConnectionPhase {
    Idle,
    Hosting,
    Joining,
    Connecting,
    Connected,
    IncomingOffer,
    Transferring,
    Complete,
    Cancelled,
    Failed
}

/**
 * Indicates whether the active AlterSend Remote transfer is using a direct P2P
 * route (hole-punched) or routing through the relay server.
 *
 * Displayed as a badge on the progress screen alongside the existing "🔒 Encrypted" tag.
 */
enum class ConnectionMode {
    /** Direct peer-to-peer socket — no relay involved. */
    DIRECT,
    /** Data is flowing through the relay server (relay only ever sees ciphertext). */
    RELAY,
    /** Local AlterSend (same LAN / Bluetooth / Wi-Fi Direct) or not yet determined. */
    UNKNOWN
}

data class AlterSendFileOffer(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val uri: Uri? = null
)

data class AlterSendTransferProgress(
    val fileId: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val chunkBitmap: ByteArray? = null
) {
    val percent: Float
        get() = if (totalBytes <= 0L) 0f else (bytesTransferred * 100f / totalBytes).coerceIn(0f, 100f)
}

data class AlterSendUiState(
    val role: AlterSendRole? = null,
    val phase: AlterSendConnectionPhase = AlterSendConnectionPhase.Idle,
    val topicHex: String? = null,
    val remoteDeviceName: String? = null,
    val offers: List<AlterSendFileOffer> = emptyList(),
    val progress: AlterSendTransferProgress? = null,
    val errorMessage: String? = null,
    /** Set once the transport path is determined; shown as a Relay/Direct badge on the progress screen. */
    val connectionMode: ConnectionMode = ConnectionMode.UNKNOWN
) {
    val isEncrypted: Boolean
        get() = phase == AlterSendConnectionPhase.Connected ||
            phase == AlterSendConnectionPhase.IncomingOffer ||
            phase == AlterSendConnectionPhase.Transferring ||
            phase == AlterSendConnectionPhase.Complete
}
