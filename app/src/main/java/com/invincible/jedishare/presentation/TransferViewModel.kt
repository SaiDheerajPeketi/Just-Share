package com.invincible.jedishare.presentation

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.CommunicationService
import com.invincible.jedishare.WifiTransferUpdate
import com.invincible.jedishare.domain.chat.BluetoothDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import androidx.core.content.ContextCompat
import com.invincible.jedishare.data.UserPreferencesDataStore

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
    val currentFileSizeBytes: Long = 0L,
    val incomingMimeType: String? = null,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val isTransferComplete: Boolean = false,
    val hasTransferStarted: Boolean = false
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    application: Application,
    private val dataStore: UserPreferencesDataStore
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UnifiedTransferState())
    val state: StateFlow<UnifiedTransferState> = _state.asStateFlow()

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CommunicationService.BROADCAST_SENDING_UPDATE) {
                applyWifiProgress(
                    WifiTransferUpdate(
                        progress = intent.getIntExtra(CommunicationService.EXTRAS_PROGRESS_STATE, 0),
                        fileName = intent.getStringExtra(CommunicationService.EXTRAS_FILE_NAME) ?: "",
                        fileSize = intent.getLongExtra(CommunicationService.EXTRAS_FILE_SIZE, 0L),
                        currentFileIndex = intent.getIntExtra(CommunicationService.EXTRAS_CURRENT_FILE_INDEX, 0),
                        totalFiles = intent.getIntExtra(CommunicationService.EXTRAS_TOTAL_FILES, 0),
                        remoteDeviceName = intent.getStringExtra(CommunicationService.EXTRAS_REMOTE_DEVICE_NAME),
                        mimeType = intent.getStringExtra(CommunicationService.EXTRAS_MIME_TYPE)
                    )
                )
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
        viewModelScope.launch {
            val initialMethod = dataStore.defaultTransferMethod.first()
            _state.update { it.copy(method = initialMethod) }
            
            CommunicationService.transferUpdates
                .filterNotNull()
                .collect(::applyWifiProgress)
        }
    }

    private fun applyWifiProgress(update: WifiTransferUpdate) {
        _state.update {
            it.copy(
                progressPercent = update.progress.toFloat(),
                currentFileName = update.fileName,
                currentFileSizeBytes = update.fileSize,
                incomingMimeType = update.mimeType ?: it.incomingMimeType,
                currentFileIndex = update.currentFileIndex,
                totalFiles = if (update.totalFiles > 0) update.totalFiles else it.totalFiles,
                connectedDeviceName = update.remoteDeviceName ?: it.connectedDeviceName,
                isTransferComplete = update.progress == 100 && (
                    update.totalFiles == 0 || update.currentFileIndex >= update.totalFiles - 1
                )
            )
        }
    }

    fun setMethod(method: String) {
        _state.update { it.copy(method = method, hasTransferStarted = false, isTransferComplete = false) }
        viewModelScope.launch {
            dataStore.setDefaultTransferMethod(method)
        }
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
                currentFileSizeBytes = 0L,
                totalFiles = uris.size,
                currentFileIndex = 0,
                isTransferComplete = false,
                hasTransferStarted = false
            )
        }
    }
    
    fun setConnectedDeviceName(name: String) {
        _state.update { it.copy(connectedDeviceName = name) }
    }

    fun markTransferStarted() {
        _state.update { it.copy(hasTransferStarted = true, isTransferComplete = false) }
    }

    fun resetTransfer() {
        CommunicationService.clearTransferUpdate()
        _state.update {
            it.copy(
                isConnected = false,
                connectedDeviceName = null,
                urisToShare = emptyList(),
                fileInfos = emptyList(),
                progressPercent = 0f,
                currentFileName = "",
                currentFileSizeBytes = 0L,
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
