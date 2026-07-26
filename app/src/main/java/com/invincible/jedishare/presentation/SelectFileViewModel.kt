package com.invincible.jedishare.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the file selection screen.
 * Owns the selected URI list and drives media loading via [MediaRepository].
 *
 * MVVM fix: SelectFile.kt (Activity) no longer queries MediaStore directly in onCreate.
 */
@HiltViewModel
class SelectFileViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    // ── Images ─────────────────────────────────────────────────────────────────
    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images: StateFlow<List<Image>> = _images.asStateFlow()

    // ── Videos ─────────────────────────────────────────────────────────────────
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    // ── Audio ──────────────────────────────────────────────────────────────────
    private val _audios = MutableStateFlow<List<Audio>>(emptyList())
    val audios: StateFlow<List<Audio>> = _audios.asStateFlow()

    // ── Selected URIs (user's file picker selection) ───────────────────────────
    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    init {
        loadMedia()
    }

    /** Adds newly picked URIs to the existing selection. */
    fun addUris(newUris: List<Uri>) {
        _selectedUris.value = _selectedUris.value + newUris
    }

    /** Removes a specific URI from the selection. */
    fun removeUri(uri: Uri) {
        _selectedUris.value = _selectedUris.value.filterNot { it == uri }
    }

    /** Clears all selected URIs. */
    fun clearSelection() {
        _selectedUris.value = emptyList()
    }

    /** Loads all media from MediaStore asynchronously. */
    fun loadMedia() {
        viewModelScope.launch {
            _images.value = mediaRepository.getImages()
            _videos.value = mediaRepository.getVideos()
            _audios.value = mediaRepository.getAudio()
        }
    }
}
