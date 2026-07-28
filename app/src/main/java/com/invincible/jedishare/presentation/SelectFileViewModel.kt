package com.invincible.jedishare.presentation

import timber.log.Timber
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.domain.chat.FileInfo
import com.invincible.jedishare.getFileDetailsFromUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for the file selection screen.
 * Owns the selected URI list and resolves FileInfo.
 */
@HiltViewModel
class SelectFileViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── Selected Files (user's file picker selection) ───────────────────────────
    private val _selectedFiles = MutableStateFlow<List<Pair<Uri, FileInfo>>>(emptyList())
    val selectedFiles: StateFlow<List<Pair<Uri, FileInfo>>> = _selectedFiles.asStateFlow()

    /** Adds newly picked URIs to the existing selection, mapping them to FileInfo. */
    fun addUris(newUris: List<Uri>) {
        Timber.d("SelectFileViewModel - addUris called")
        val newFiles = newUris.map { uri ->
            uri to getFileDetailsFromUri(uri, context.contentResolver)
        }
        _selectedFiles.update { current ->
            (current + newFiles).distinctBy { it.first }
        }
    }

    /** Removes a specific URI from the selection. */
    fun removeUri(uri: Uri) {
        Timber.d("SelectFileViewModel - removeUri called")
        _selectedFiles.update { current ->
            current.filterNot { it.first == uri }
        }
    }

    /** Clears all selected files. */
    fun clearSelection() {
        Timber.d("SelectFileViewModel - clearSelection called")
        _selectedFiles.value = emptyList()
    }
}
