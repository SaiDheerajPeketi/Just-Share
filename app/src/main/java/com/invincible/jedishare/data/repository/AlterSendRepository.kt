package com.invincible.jedishare.data.repository

import android.content.Context
import android.net.Uri
import com.invincible.jedishare.domain.altersend.AlterSendConnectionPhase
import com.invincible.jedishare.domain.altersend.AlterSendFileOffer
import com.invincible.jedishare.domain.altersend.AlterSendProtocol
import com.invincible.jedishare.domain.altersend.AlterSendRole
import com.invincible.jedishare.domain.altersend.AlterSendUiState
import com.invincible.jedishare.getFileDetailsFromUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlterSendRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(AlterSendUiState())
    val state: StateFlow<AlterSendUiState> = _state.asStateFlow()

    fun host(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _state.update {
                it.copy(
                    role = AlterSendRole.Sender,
                    phase = AlterSendConnectionPhase.Failed,
                    errorMessage = "Select at least one file to share."
                )
            }
            return
        }

        val offers = uris.mapIndexed { index, uri ->
            val fileInfo = getFileDetailsFromUri(uri, context.contentResolver)
            AlterSendFileOffer(
                id = "file-$index",
                name = fileInfo.fileName ?: uri.lastPathSegment ?: "File ${index + 1}",
                sizeBytes = fileInfo.size?.toLongOrNull() ?: 0L,
                mimeType = fileInfo.mimeType,
                uri = uri
            )
        }

        _state.value = AlterSendUiState(
            role = AlterSendRole.Sender,
            phase = AlterSendConnectionPhase.Hosting,
            topicHex = AlterSendProtocol.generateTopicHex(),
            offers = offers
        )
    }

    fun join(topic: String) {
        val normalized = runCatching { AlterSendProtocol.normalizeTopicHex(topic) }
            .getOrElse {
                _state.value = AlterSendUiState(
                    role = AlterSendRole.Receiver,
                    phase = AlterSendConnectionPhase.Failed,
                    errorMessage = "Enter a valid 64-character AlterSend code."
                )
                return
            }

        _state.value = AlterSendUiState(
            role = AlterSendRole.Receiver,
            phase = AlterSendConnectionPhase.Joining,
            topicHex = normalized
        )
    }

    fun reportRuntimeUnavailable() {
        _state.update {
            it.copy(
                phase = AlterSendConnectionPhase.Failed,
                errorMessage = "AlterSend runtime is not bundled yet. Exact AlterSend requires the Bare worklet runtime."
            )
        }
    }

    fun reset() {
        _state.value = AlterSendUiState()
    }
}
