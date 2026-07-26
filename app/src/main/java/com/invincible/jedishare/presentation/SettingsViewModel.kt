package com.invincible.jedishare.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.data.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Reads/writes preferences via [UserPreferencesDataStore] (DataStore).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore
) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = prefs.isDarkModeEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val defaultTransferMethod: StateFlow<String> = prefs.defaultTransferMethod.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Bluetooth"
    )

    val chunkSizeKb: StateFlow<Int> = prefs.chunkSizeKb.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 8
    )

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }

    fun setDefaultTransferMethod(method: String) {
        viewModelScope.launch { prefs.setDefaultTransferMethod(method) }
    }

    fun setChunkSizeKb(size: Int) {
        viewModelScope.launch { prefs.setChunkSizeKb(size) }
    }
}
