package com.invincible.jedishare.presentation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel


data class Image(
    val id: Long,
    val name: String,
    val uri: Uri
)

class ImageViewModel: ViewModel() {
    var images by mutableStateOf(emptyList<Image>())
        private set

    fun updateImages(images: List<Image>){
        this.images = images
    }
}