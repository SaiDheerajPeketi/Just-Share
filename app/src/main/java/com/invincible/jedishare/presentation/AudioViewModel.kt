package com.invincible.jedishare.presentation

import timber.log.Timber

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
 * ViewModel for displaying the audio gallery.
 * Replaced the plain ViewModel + mutableStateOf pattern with Hilt + StateFlow.
 */
@HiltViewModel
class AudioViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _audios = MutableStateFlow<List<Audio>>(emptyList())
    val audios: StateFlow<List<Audio>> = _audios.asStateFlow()

    init { loadAudios() }

    fun loadAudios() {
        Timber.d("AudioViewModel - loadAudios called")
        viewModelScope.launch {
            _audios.value = mediaRepository.getAudio()
        }
    }
}