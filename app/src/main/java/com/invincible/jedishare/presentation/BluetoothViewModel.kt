package com.invincible.jedishare.presentation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.data.chat.toBluetoothMessage
import com.invincible.jedishare.data.chat.toFileInfo
import com.invincible.jedishare.data.repository.FileTransferRepository
import com.invincible.jedishare.domain.chat.BluetoothController
import com.invincible.jedishare.domain.chat.BluetoothDeviceDomain
import com.invincible.jedishare.domain.chat.ConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Bluetooth transfer flow.
 *
 * MVVM architecture:
 * - All state is exposed as [StateFlow] / [SharedFlow] — no raw mutable public fields.
 * - File I/O is fully delegated to [FileTransferRepository].
 * - [BluetoothController] has no ViewModel reference; the ViewModel subscribes to its Flows.
 * - [viewModelScope] is the only coroutine scope used; lifecycle is automatically handled.
 */
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val fileTransferRepository: FileTransferRepository
) : ViewModel() {

    // ── Core Connection State ──────────────────────────────────────────────────

    private val _state = MutableStateFlow(BluetoothUiState())

    /** Main combined state: pairs BT controller device lists with UI state. */
    val state: StateFlow<BluetoothUiState> = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDevices, state ->
        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDevices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null

    init {
        bluetoothController.isConnected.onEach { isConnected ->
            _state.update { it.copy(isConnected = isConnected) }
        }.launchIn(viewModelScope)

        bluetoothController.errors.onEach { error ->
            _state.update { it.copy(errorMessage = error) }
        }.launchIn(viewModelScope)
    }

    // ── Transfer Progress State ────────────────────────────────────────────────

    /** Progress state for sender + receiver sides. */
    private val _transferProgress = MutableStateFlow(TransferProgressState())
    val transferProgress: StateFlow<TransferProgressState> = _transferProgress.asStateFlow()

    /** Which file index is currently being transferred (0-based). */
    private val _currFileCount = MutableStateFlow(0)
    val currFileCount: StateFlow<Int> = _currFileCount.asStateFlow()

    /** Total size of the current file (bytes), used to compute receiver progress %. */
    private val _currentFileSize = MutableStateFlow(1L)
    val fileInfoState: StateFlow<Long> = _currentFileSize.asStateFlow()

    /** Emits the chunk iteration count (sender side) for the progress bar. */
    private val _iterationCountFlow = MutableSharedFlow<Long>()
    fun getIterationCountFlow(): SharedFlow<Long> = _iterationCountFlow.asSharedFlow()

    // ── URI List ───────────────────────────────────────────────────────────────

    private val _uriList = MutableStateFlow<List<Uri>>(emptyList())
    val uriList: StateFlow<List<Uri>> = _uriList.asStateFlow()

    fun setUriList(uris: List<Uri>) { _uriList.value = uris }
    fun getUriList(): List<Uri> = _uriList.value

    // ── Actions ────────────────────────────────────────────────────────────────

    fun connectToDevice(device: BluetoothDeviceDomain) {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .connectToDevice(device)
            .listenForResults()
    }

    fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    fun waitForIncomingConnections() {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .startBluetoothServer()
            .listenForResults()
    }

    fun startScan() = bluetoothController.startDiscovery()
    fun stopScan() = bluetoothController.stopDiscovery()

    fun sendMessage(message: String) {
        viewModelScope.launch {
            val uris = _uriList.value
            if (uris.isEmpty()) {
                Log.w("BluetoothViewModel", "sendMessage called with empty URI list")
                return@launch
            }
            val bluetoothMessage = bluetoothController.trySendMessage(
                uriList = uris,
                iterationCountFlow = _iterationCountFlow,
                onFileSizeResolved = { size -> _currentFileSize.value = size },
                onFileCountUpdated = { count -> _currFileCount.value = count }
            )
            if (bluetoothMessage != null) {
                _state.update { it.copy(messages = it.messages + bluetoothMessage) }
            }
        }
    }

    /** Exposes for legacy UI compat. Use [transferProgress] for new UI. */
    val statee: StateFlow<BluetoothUiState> get() = _state.asStateFlow()

    // ── Internal: Flow Listener ────────────────────────────────────────────────

    /**
     * Collects a [ConnectionResult] Flow, routing results to state and I/O repository.
     * Handles multi-file transfers: [ConnectionResult.EndOfFile] triggers a new file slot.
     */
    private fun Flow<ConnectionResult>.listenForResults(): Job {
        var fileUri: android.net.Uri? = null
        var isFirstChunk = true

        return onEach { result ->
            when (result) {
                is ConnectionResult.ConnectionEstablished -> {
                    isFirstChunk = true
                    fileUri = null
                    _state.update {
                        it.copy(isConnected = true, isConnecting = false, errorMessage = null)
                    }
                    Log.d("BluetoothViewModel", "Connection established")
                }

                is ConnectionResult.TransferSucceeded -> {
                    if (isFirstChunk) {
                        // First chunk after connection/EOF is always FileInfo metadata
                        isFirstChunk = false
                        val fileInfo = result.message.toFileInfo()
                        Log.d("BluetoothViewModel", "Incoming file metadata: $fileInfo")
                        if (fileInfo != null) {
                            _currentFileSize.value = fileInfo.size?.toLong() ?: 1L
                            viewModelScope.launch {
                                fileUri = fileTransferRepository.createMediaStoreEntry(fileInfo)
                                Log.d("BluetoothViewModel", "MediaStore entry created: $fileUri")
                            }
                        }
                    } else {
                        // Subsequent chunks are raw file bytes
                        val currentUri = fileUri ?: return@onEach
                        viewModelScope.launch {
                            fileTransferRepository.appendChunkToFile(currentUri, result.message)
                            val received = _transferProgress.value.bytesReceived + result.message.size
                            _transferProgress.update { it.copy(bytesReceived = received) }
                        }
                    }
                }

                is ConnectionResult.EndOfFile -> {
                    // Prepare for next file in multi-file transfer
                    Log.d("BluetoothViewModel", "End of file received; ready for next file")
                    isFirstChunk = true
                    fileUri = null
                    val newCount = _currFileCount.value + 1
                    _currFileCount.value = newCount
                    _transferProgress.update {
                        it.copy(bytesReceived = 0L, totalBytes = 1L)
                    }
                }

                is ConnectionResult.Error -> {
                    Log.e("BluetoothViewModel", "Connection error: ${result.message}")
                    _state.update {
                        it.copy(isConnected = false, isConnecting = false, errorMessage = result.message)
                    }
                }
            }
        }.catch { throwable ->
            Log.e("BluetoothViewModel", "Connection flow error", throwable)
            bluetoothController.closeConnection()
            _state.update { it.copy(isConnected = false, isConnecting = false) }
        }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}

/** Tracks bytes sent / received for the active file transfer. */
data class TransferProgressState(
    val bytesSent: Long = 0L,
    val bytesReceived: Long = 0L,
    val totalBytes: Long = 1L
) {
    val sentPercent: Int get() = if (totalBytes > 0) ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    val receivedPercent: Int get() = if (totalBytes > 0) ((bytesReceived * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

/** Models for SelectFile screen — kept here to avoid extra files for small data classes. */
data class Image(val id: Long, val name: String, val uri: android.net.Uri)
data class Video(val id: Long, val name: String, val uri: android.net.Uri)
data class Audio(val id: Long, val name: String, val uri: android.net.Uri)