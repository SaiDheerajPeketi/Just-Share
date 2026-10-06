package com.blackandblue.justshare.presentation

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackandblue.justshare.CommunicationService
import com.blackandblue.justshare.WifiTransferUpdate
import com.blackandblue.justshare.domain.chat.BluetoothDevice
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
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.domain.chat.FileInfo

data class UnifiedDevice(
    val id: String,
    val name: String,
    val isWifiDirect: Boolean,
    val isPaired: Boolean = true,
    val isAvailable: Boolean = true,
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
    val fileInfos: List<com.blackandblue.justshare.domain.chat.FileInfo> = emptyList(),
    
    // Progress
    val progressPercent: Float = 0f,
    val currentFileName: String = "",
    val currentFileSizeBytes: Long = 0L,
    val incomingMimeType: String? = null,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val isTransferComplete: Boolean = false,
    val hasTransferStarted: Boolean = false,
    val permissionInterrupted: Boolean = false
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
                @Suppress("DEPRECATION")
                val manifest = intent.getSerializableExtra(CommunicationService.EXTRAS_MANIFEST) as? ArrayList<FileInfo>
                applyWifiProgress(
                    WifiTransferUpdate(
                        progress = intent.getIntExtra(CommunicationService.EXTRAS_PROGRESS_STATE, 0),
                        fileName = intent.getStringExtra(CommunicationService.EXTRAS_FILE_NAME) ?: "",
                        fileSize = intent.getLongExtra(CommunicationService.EXTRAS_FILE_SIZE, 0L),
                        currentFileIndex = intent.getIntExtra(CommunicationService.EXTRAS_CURRENT_FILE_INDEX, 0),
                        totalFiles = intent.getIntExtra(CommunicationService.EXTRAS_TOTAL_FILES, 0),
                        remoteDeviceName = intent.getStringExtra(CommunicationService.EXTRAS_REMOTE_DEVICE_NAME),
                        mimeType = intent.getStringExtra(CommunicationService.EXTRAS_MIME_TYPE),
                        manifest = manifest,
                        generation = intent.getLongExtra(CommunicationService.EXTRAS_PROGRESS_GENERATION, 0L)
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
        CommunicationService.withCurrentTransferUpdate(update.generation) {
            _state.update {
                val newFileInfos = if (update.manifest != null && it.fileInfos.isEmpty()) {
                    update.manifest
                } else {
                    it.fileInfos
                }
                val newTotalFiles = if (update.totalFiles > 0) update.totalFiles else it.totalFiles
                it.copy(
                    fileInfos = newFileInfos,
                    permissionInterrupted = false,
                    progressPercent = update.progress.toFloat(),
                    currentFileName = update.fileName,
                    currentFileSizeBytes = update.fileSize,
                    currentFileIndex = update.currentFileIndex,
                    totalFiles = newTotalFiles,
                    connectedDeviceName = update.remoteDeviceName ?: it.connectedDeviceName,
                    incomingMimeType = update.mimeType ?: it.incomingMimeType,
                    isTransferComplete = update.progress == 100 && (
                        newTotalFiles == 0 || update.currentFileIndex >= newTotalFiles - 1
                    )
                )
            }
        }
    }

    fun setMethod(method: String, save: Boolean = false) {
        _state.update { it.copy(method = method, hasTransferStarted = false, isTransferComplete = false, permissionInterrupted = false) }
        if (save) {
            viewModelScope.launch {
                dataStore.setDefaultTransferMethod(method)
            }
        }
    }

    fun setUris(uris: List<Uri>) {
        val contentResolver = getApplication<Application>().contentResolver
        val fileInfos = uris.map { com.blackandblue.justshare.getFileDetailsFromUri(it, contentResolver) }
        _state.update {
            it.copy(
                urisToShare = uris,
                permissionInterrupted = false,
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
        // Incoming progress can finish before discovery opens the progress screen.
        _state.update { it.copy(hasTransferStarted = true, permissionInterrupted = false,
            progressPercent = if (it.permissionInterrupted) 0f else it.progressPercent) }
    }

    /** Invalidates queued old service updates and preserves the selected files,
     * direction and metadata for recovery. Already-completed transfers stay done. */
    fun markPermissionInterrupted(alreadyComplete: Boolean = false) {
        val current = _state.value
        if (!current.hasTransferStarted && !current.isTransferComplete && !alreadyComplete) return
        CommunicationService.clearTransferUpdate()
        if (current.isTransferComplete || alreadyComplete) return
        _state.update {
            if (it.isTransferComplete) it else it.copy(hasTransferStarted = false,
                permissionInterrupted = true, progressPercent = -1f)
        }
    }

    fun clearTransferProgress() {
        CommunicationService.clearTransferUpdate()
        _state.update {
            it.copy(
                hasTransferStarted = false,
                permissionInterrupted = false,
                isTransferComplete = false,
                progressPercent = 0f,
                currentFileName = "",
                currentFileSizeBytes = 0L,
                currentFileIndex = 0
            )
        }
    }

    fun resetTransfer() {
        CommunicationService.clearTransferUpdate()
        _state.update {
            it.copy(
                isConnected = false,
                permissionInterrupted = false,
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
