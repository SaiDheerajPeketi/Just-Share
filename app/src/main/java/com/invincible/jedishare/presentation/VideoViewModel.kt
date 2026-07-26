package com.invincible.jedishare.presentation

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
 * ViewModel for displaying the video gallery.
 * Replaced the plain ViewModel + mutableStateOf pattern with Hilt + StateFlow.
 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    init { loadVideos() }

    fun loadVideos() {
        viewModelScope.launch {
            _videos.value = mediaRepository.getVideos()
        }
    }
}