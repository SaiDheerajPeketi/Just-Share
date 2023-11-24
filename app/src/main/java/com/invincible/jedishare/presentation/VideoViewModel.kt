package com.invincible.jedishare.presentation

import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class Video(
    val id: Long,
    val name: String,
    val uri: Uri
)
class VideoViewModel: ViewModel() {
    var videos by mutableStateOf(emptyList<Video>())
        private set

    fun updateVideos(videos: List<Video>){
        this.videos = videos
    }
}