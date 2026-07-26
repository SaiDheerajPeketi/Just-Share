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
 * ViewModel for displaying the image gallery.
 * Replaced the plain ViewModel + mutableStateOf pattern with Hilt + StateFlow.
 */
@HiltViewModel
class ImageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images: StateFlow<List<Image>> = _images.asStateFlow()

    init { loadImages() }

    fun loadImages() {
        viewModelScope.launch {
            _images.value = mediaRepository.getImages()
        }
    }
}