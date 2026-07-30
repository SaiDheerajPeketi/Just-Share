package com.invincible.jedishare.domain.altersend

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
    val errorMessage: String? = null
) {
    val isEncrypted: Boolean
        get() = phase == AlterSendConnectionPhase.Connected ||
            phase == AlterSendConnectionPhase.IncomingOffer ||
            phase == AlterSendConnectionPhase.Transferring ||
            phase == AlterSendConnectionPhase.Complete
}
