package com.invincible.jedishare.data.repository

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import com.invincible.jedishare.AlterSendForegroundService
import com.invincible.jedishare.data.altersend.AlterSendSocketTransfer
import com.invincible.jedishare.domain.altersend.AlterSendConnectionPhase
import com.invincible.jedishare.domain.altersend.AlterSendFileOffer
import com.invincible.jedishare.domain.altersend.AlterSendInvite
import com.invincible.jedishare.domain.altersend.AlterSendProtocol
import com.invincible.jedishare.domain.altersend.AlterSendRole
import com.invincible.jedishare.domain.altersend.AlterSendUiState
import com.invincible.jedishare.getFileDetailsFromUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlterSendRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: TransferHistoryRepository
) {
    private val _state = MutableStateFlow(AlterSendUiState())
    val state: StateFlow<AlterSendUiState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transferJob: Job? = null
    private var activeTransfer: AlterSendSocketTransfer? = null
    private var incomingDecision: CompletableDeferred<Boolean>? = null

    fun host(uris: List<Uri>) {
        resetActiveTransfer()
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
            topicHex = "Preparing connection code...",
            offers = offers
        )

        val topicHex = AlterSendProtocol.generateTopicHex()
        val transfer = AlterSendSocketTransfer(
            context = context,
            historyRepository = historyRepository,
            onState = { next ->
                _state.update {
                    next.copy(
                        role = AlterSendRole.Sender,
                        remoteDeviceName = next.remoteDeviceName ?: it.remoteDeviceName,
                        offers = next.offers.ifEmpty { it.offers }
                    )
                }
            },
            awaitIncomingDecision = {
                CompletableDeferred<Boolean>().also { decision ->
                    incomingDecision = decision
                }.await()
            }
        )
        activeTransfer = transfer
        startForegroundTransfer()
        transferJob = scope.launch {
            runCatching {
                transfer.host(topicHex, offers)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.update {
                    it.copy(
                        phase = AlterSendConnectionPhase.Failed,
                        errorMessage = error.localizedMessage ?: "Remote Transfer failed"
                    )
                }
            }.also {
                stopForegroundTransfer()
            }
        }
    }

    fun join(topic: String) {
        resetActiveTransfer()
        val invite = runCatching { AlterSendInvite.decode(topic) }
            .getOrElse {
                _state.value = AlterSendUiState(
                    role = AlterSendRole.Receiver,
                    phase = AlterSendConnectionPhase.Failed,
                    errorMessage = "Enter a valid Remote Transfer connection code."
                )
                return
            }

        _state.value = AlterSendUiState(
            role = AlterSendRole.Receiver,
            phase = AlterSendConnectionPhase.Joining,
            topicHex = invite.encode()
        )

        val transfer = AlterSendSocketTransfer(
            context = context,
            historyRepository = historyRepository,
            onState = { next ->
                _state.update {
                    next.copy(
                        role = AlterSendRole.Receiver,
                        topicHex = next.topicHex ?: it.topicHex,
                        offers = next.offers.ifEmpty { it.offers }
                    )
                }
            }
        )
        activeTransfer = transfer
        startForegroundTransfer()
        transferJob = scope.launch {
            runCatching {
                transfer.join(invite)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.update {
                    it.copy(
                        phase = AlterSendConnectionPhase.Failed,
                        errorMessage = error.localizedMessage ?: "Remote Transfer failed"
                    )
                }
            }.also {
                stopForegroundTransfer()
            }
        }
    }

    fun reportRuntimeUnavailable() {
        // No-op retained for older UI calls.
    }

    fun acceptIncomingTransfer() {
        incomingDecision?.complete(true)
        incomingDecision = null
    }

    fun rejectIncomingTransfer() {
        incomingDecision?.complete(false)
        incomingDecision = null
    }

    fun reset() {
        resetActiveTransfer()
        _state.value = AlterSendUiState()
    }

    private fun resetActiveTransfer() {
        incomingDecision?.cancel()
        incomingDecision = null
        transferJob?.cancel()
        transferJob = null
        activeTransfer?.close()
        activeTransfer = null
        stopForegroundTransfer()
    }

    private fun startForegroundTransfer() {
        val intent = Intent(context, AlterSendForegroundService::class.java).apply {
            action = AlterSendForegroundService.ACTION_START
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopForegroundTransfer() {
        runCatching { context.stopService(Intent(context, AlterSendForegroundService::class.java)) }
    }
}
