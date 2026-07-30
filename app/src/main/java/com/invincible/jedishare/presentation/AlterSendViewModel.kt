package com.invincible.jedishare.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.invincible.jedishare.data.repository.AlterSendRepository
import com.invincible.jedishare.domain.altersend.AlterSendUiState
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

    fun reset() = repository.reset()
}
