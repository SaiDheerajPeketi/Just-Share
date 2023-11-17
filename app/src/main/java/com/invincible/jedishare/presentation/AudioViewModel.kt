package com.invincible.jedishare.presentation

import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class Audio(
    val id: Long,
    val name: String,
    val uri: Uri
)
class AudioViewModel: ViewModel(){
    var audios by mutableStateOf(emptyList<Audio>())
        private set

    fun updateAudios(audios: List<Audio>){
        this.audios = audios
    }
}