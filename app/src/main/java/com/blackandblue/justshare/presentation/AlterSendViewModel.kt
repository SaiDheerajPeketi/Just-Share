package com.blackandblue.justshare.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.blackandblue.justshare.data.repository.AlterSendRepository
import com.blackandblue.justshare.domain.altersend.AlterSendUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AlterSendViewModel @Inject constructor(
    private val repository: AlterSendRepository
) : ViewModel() {
    val state: StateFlow<AlterSendUiState> = repository.state

    fun host(uris: List<Uri>) = repository.host(uris)

    fun join(topic: String) = repository.join(topic)

    fun reportRuntimeUnavailable() = repository.reportRuntimeUnavailable()

    fun acceptIncomingTransfer() = repository.acceptIncomingTransfer()

    fun rejectIncomingTransfer() = repository.rejectIncomingTransfer()

    fun reset() = repository.reset()
}
