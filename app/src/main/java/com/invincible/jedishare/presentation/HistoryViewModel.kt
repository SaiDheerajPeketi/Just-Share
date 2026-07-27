package com.invincible.jedishare.presentation

import timber.log.Timber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.data.repository.TransferHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the History screen.
 * Exposes transfer history as a [StateFlow] of [TransferHistoryEntity] lists.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: TransferHistoryRepository
) : ViewModel() {

    /** All past transfers, most recent first. Backed by Room Flow. */
    val history: StateFlow<List<TransferHistoryEntity>> = repository.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteEntry(entry: TransferHistoryEntity) {
        Timber.d("HistoryViewModel - deleteEntry called")
        viewModelScope.launch { repository.deleteEntry(entry) }
    }

    fun clearAll() {
        Timber.d("HistoryViewModel - clearAll called")
        viewModelScope.launch { repository.clearAll() }
    }
}
