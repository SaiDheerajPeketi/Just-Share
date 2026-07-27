package com.invincible.jedishare.presentation

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import com.invincible.jedishare.CommunicationService
import com.invincible.jedishare.domain.chat.BluetoothDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import androidx.core.content.ContextCompat

data class UnifiedDevice(
    val id: String,
    val name: String,
    val isWifiDirect: Boolean,
    val isPaired: Boolean = true,
    val btDevice: BluetoothDevice? = null,
    val wifiDevice: WifiP2pDevice? = null
)

data class UnifiedTransferState(
    val method: String = "bt", // "bt" or "wifi"
    val isDiscovering: Boolean = false,
    val devices: List<UnifiedDevice> = emptyList(),
    val isConnected: Boolean = false,
    val connectedDeviceName: String? = null,
    val urisToShare: List<Uri> = emptyList(),
    val fileInfos: List<com.invincible.jedishare.domain.chat.FileInfo> = emptyList(),
    
    // Progress
    val progressPercent: Float = 0f,
    val currentFileName: String = "",
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val isTransferComplete: Boolean = false,
    val hasTransferStarted: Boolean = false
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UnifiedTransferState())
    val state: StateFlow<UnifiedTransferState> = _state.asStateFlow()

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CommunicationService.BROADCAST_SENDING_UPDATE) {
                val progress = intent.getIntExtra(CommunicationService.EXTRAS_PROGRESS_STATE, 0)
                val fileName = intent.getStringExtra(CommunicationService.EXTRAS_FILE_NAME) ?: ""
                _state.update { 
                    it.copy(
                        progressPercent = progress.toFloat(),
                        currentFileName = fileName,
                        isTransferComplete = progress == 100
                    ) 
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            application,
            progressReceiver,
            IntentFilter(CommunicationService.BROADCAST_SENDING_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun setMethod(method: String) {
        _state.update { it.copy(method = method) }
    }

    fun setUris(uris: List<Uri>) {
        val contentResolver = getApplication<Application>().contentResolver
        val fileInfos = uris.map { com.invincible.jedishare.getFileDetailsFromUri(it, contentResolver) }
        _state.update {
            it.copy(
                urisToShare = uris,
                fileInfos = fileInfos,
                progressPercent = 0f,
                currentFileName = "",
                currentFileIndex = 0,
                isTransferComplete = false,
                hasTransferStarted = false
            )
        }
    }
    
    fun setConnectedDeviceName(name: String) {
        _state.update { it.copy(connectedDeviceName = name, isConnected = true) }
    }

    fun markTransferStarted() {
        _state.update { it.copy(hasTransferStarted = true, isTransferComplete = false) }
    }

    fun resetTransfer() {
        _state.update {
            it.copy(
                isConnected = false,
                connectedDeviceName = null,
                urisToShare = emptyList(),
                fileInfos = emptyList(),
                progressPercent = 0f,
                currentFileName = "",
                totalFiles = 0,
                currentFileIndex = 0,
                isTransferComplete = false,
                hasTransferStarted = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(progressReceiver)
    }
}
