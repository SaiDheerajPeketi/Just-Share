package com.invincible.jedishare.presentation

import timber.log.Timber

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.data.chat.toBluetoothMessage
import com.invincible.jedishare.data.chat.toFileInfo
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.data.repository.FileTransferRepository
import com.invincible.jedishare.data.repository.TransferHistoryRepository
import com.invincible.jedishare.domain.chat.BluetoothController
import com.invincible.jedishare.domain.chat.BluetoothDeviceDomain
import com.invincible.jedishare.domain.chat.BluetoothMessage
import com.invincible.jedishare.domain.chat.ConnectionResult
import com.invincible.jedishare.domain.chat.FileInfo
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
 * - On [ConnectionResult.EndOfFile], a [TransferHistoryEntity] is inserted automatically (TODO-15).
 */
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val fileTransferRepository: FileTransferRepository,
    private val historyRepository: TransferHistoryRepository
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
    private val _currentFileSize = MutableStateFlow(0L)
    val fileInfoState: StateFlow<Long> = _currentFileSize.asStateFlow()

    private val _incomingFileNameState = MutableStateFlow<String?>(null)
    val incomingFileNameState: StateFlow<String?> = _incomingFileNameState.asStateFlow()

    private val _incomingMimeTypeState = MutableStateFlow<String?>(null)
    val incomingMimeTypeState: StateFlow<String?> = _incomingMimeTypeState.asStateFlow()

    private val _incomingManifestState = MutableStateFlow<List<FileInfo>?>(null)
    val incomingManifestState: StateFlow<List<FileInfo>?> = _incomingManifestState.asStateFlow()

    private val _connectedDeviceNameState = MutableStateFlow<String?>(null)
    val connectedDeviceNameState: StateFlow<String?> = _connectedDeviceNameState.asStateFlow()

    /** Emits the chunk iteration count (sender side) for the progress bar. */
    private val _iterationCountFlow = MutableSharedFlow<Long>()
    fun getIterationCountFlow(): SharedFlow<Long> = _iterationCountFlow.asSharedFlow()

    // ── URI List ───────────────────────────────────────────────────────────────

    private val _uriList = MutableStateFlow<List<Uri>>(emptyList())
    val uriList: StateFlow<List<Uri>> = _uriList.asStateFlow()

    fun setUriList(uris: List<Uri>) { 
        Timber.d("BluetoothViewModel - setUriList called")
        _uriList.value = uris 
    }
    fun getUriList(): List<Uri> = _uriList.value

    fun resetTransferState() {
        _uriList.value = emptyList()
        _incomingFileName = null
        _incomingFileNameState.value = null
        _incomingMimeType = null
        _incomingMimeTypeState.value = null
        _incomingManifestState.value = null
        _incomingFileSize = 0L
        _currentFileSize.value = 0L
        _currFileCount.value = 0
        _transferProgress.value = TransferProgressState()
    }

    // ── Tracks current incoming file metadata (for history logging) ────────────
    private var _incomingFileName: String? = null
    private var _incomingMimeType: String? = null
    private var _incomingFileSize: Long = 0L
    private var _connectedDeviceName: String? = null

    // ── Actions ────────────────────────────────────────────────────────────────

    fun connectToDevice(device: BluetoothDeviceDomain) {
        Timber.d("BluetoothViewModel - connectToDevice called")
        _connectedDeviceName = device.name
        _connectedDeviceNameState.value = device.name
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .connectToDevice(device)
            .listenForResults(isSender = false)
    }

    fun disconnectFromDevice() {
        Timber.d("BluetoothViewModel - disconnectFromDevice called")
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    fun waitForIncomingConnections() {
        Timber.d("BluetoothViewModel - waitForIncomingConnections called")
        deviceConnectionJob?.cancel()
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .startBluetoothServer()
            .listenForResults(isSender = false)
    }

    fun startScan() = bluetoothController.startDiscovery()
    fun stopScan() = bluetoothController.stopDiscovery()
    fun pairDevice(device: BluetoothDeviceDomain) = bluetoothController.pairDevice(device)
    fun requestDiscoverable(durationSeconds: Int = 300) = bluetoothController.requestDiscoverable(durationSeconds)

    fun sendMessage(message: String) {
        Timber.d("BluetoothViewModel - stopScan called")
        viewModelScope.launch {
            val uris = _uriList.value
            if (uris.isEmpty()) {
                Log.w("BluetoothViewModel", "sendMessage called with empty URI list")
                return@launch
            }

            val bluetoothMessage = bluetoothController.trySendMessage(
                uriList = uris,
                iterationCountFlow = _iterationCountFlow,
                onFileSizeResolved = { size -> 
                    _currentFileSize.value = size
                    _transferProgress.update { it.copy(totalBytes = size, bytesSent = 0L) }
                },
                onFileCountUpdated = { count -> _currFileCount.value = count },
                onFileInfoResolved = { fileInfo ->
                    _incomingFileNameState.value = fileInfo.fileName
                    _incomingFileName = fileInfo.fileName
                    _incomingMimeType = fileInfo.mimeType
                    _incomingMimeTypeState.value = fileInfo.mimeType
                    _incomingFileSize = fileInfo.size?.toLongOrNull() ?: 0L
                },
                onBytesSent = { absoluteBytesSent ->
                    _transferProgress.update { it.copy(bytesSent = absoluteBytesSent) }
                },
                onFileSent = { fileInfo ->
                    viewModelScope.launch {
                        historyRepository.addEntry(
                            TransferHistoryEntity(
                                fileName = fileInfo.fileName ?: "Unknown",
                                mimeType = fileInfo.mimeType,
                                fileSizeBytes = fileInfo.size?.toLongOrNull() ?: 0L,
                                isSender = true,
                                transferMethod = "bt",
                                remoteDeviceName = _connectedDeviceName ?: "Unknown Device",
                                contentUri = fileInfo.uri
                            )
                        )
                        Log.d("BluetoothViewModel", "Sender history entry saved: ${fileInfo.fileName}")
                    }
                }
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
     * Handles multi-file transfers: [ConnectionResult.EndOfFile] triggers a new file slot
     * and writes a [TransferHistoryEntity] entry for the completed file (TODO-15).
     *
     * @param isSender True if this device is sending files (for history record).
     */
    private fun Flow<ConnectionResult>.listenForResults(isSender: Boolean): Job {
        var fileUri: Uri? = null
        var isFirstChunk = true

        return onEach { result ->
            when (result) {
                is ConnectionResult.ConnectionEstablished -> {
                    result.remoteDeviceName?.let { remoteName ->
                        _connectedDeviceName = remoteName
                        _connectedDeviceNameState.value = remoteName
                    }
                    isFirstChunk = true
                    fileUri = null
                    _incomingFileName = null
                    _incomingFileNameState.value = null
                    _incomingMimeType = null
                    _incomingMimeTypeState.value = null
                    _incomingManifestState.value = null
                    _incomingFileSize = 0L
                    _currentFileSize.value = 0L
                    _currFileCount.value = 0
                    _transferProgress.update { it.copy(totalBytes = 1L, bytesReceived = 0L, bytesSent = 0L) }
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
                            _incomingFileName = fileInfo.fileName
                            _incomingFileNameState.value = fileInfo.fileName
                            _incomingMimeType = fileInfo.mimeType
                            _incomingMimeTypeState.value = fileInfo.mimeType
                            if (fileInfo.manifest != null && _incomingManifestState.value == null) {
                                val totalSizeNeeded = fileInfo.manifest.sumOf { it.size?.toLongOrNull() ?: 0L }
                                val availableSpace = android.os.Environment.getExternalStorageDirectory().usableSpace
                                if (totalSizeNeeded > availableSpace) {
                                    Log.e("BluetoothViewModel", "Not enough storage. Needed: $totalSizeNeeded, Available: $availableSpace")
                                    bluetoothController.closeConnection()
                                    _state.update { it.copy(errorMessage = "Not enough storage on receiver device") }
                                    return@onEach
                                }
                                _incomingManifestState.value = fileInfo.manifest
                            }
                            _incomingFileSize = fileInfo.size?.toLongOrNull() ?: 0L
                            _currentFileSize.value = _incomingFileSize
                            _transferProgress.update { it.copy(totalBytes = _incomingFileSize, bytesReceived = 0L) }
                            fileUri = fileTransferRepository.createMediaStoreEntry(fileInfo)
                            Log.d("BluetoothViewModel", "MediaStore entry created: $fileUri")
                            fileUri?.let { fileTransferRepository.openFile(it) }
                        }
                    } else {
                        // Subsequent chunks are raw file bytes
                        if (fileUri == null) return@onEach
                        val success = fileTransferRepository.appendChunkToFile(result.message)
                        if (success) {
                            val received = _transferProgress.value.bytesReceived + result.message.size
                            _transferProgress.update { it.copy(bytesReceived = received) }
                        }
                    }
                }

                is ConnectionResult.EndOfFile -> {
                    // TODO-15: Log completed transfer to history DB
                    val fileName = _incomingFileName ?: "Unknown"
                    val fileSize = _incomingFileSize
                    val mimeType = _incomingMimeType
                    val deviceName = _connectedDeviceName
                    viewModelScope.launch {
                        fileTransferRepository.closeFile()
                        historyRepository.addEntry(
                            TransferHistoryEntity(
                                fileName        = fileName,
                                mimeType        = mimeType,
                                fileSizeBytes   = fileSize,
                                isSender        = isSender,
                                transferMethod  = "bt",
                                remoteDeviceName = deviceName,
                                contentUri      = fileUri?.toString()
                            )
                        )
                        Log.d("BluetoothViewModel", "History entry saved: $fileName")
                    }

                    // Prepare for next file in multi-file transfer
                    Log.d("BluetoothViewModel", "End of file received; ready for next file")
                    isFirstChunk = true
                    fileUri = null
                    val newCount = _currFileCount.value + 1
                    _currFileCount.value = newCount
                    // Do not clear metadata so UI can show 100% completion
                    _transferProgress.update { it.copy(bytesReceived = it.totalBytes) }
                }

                is ConnectionResult.FileSkipped -> {
                    Log.e("BluetoothViewModel", "File skipped by sender")
                    isFirstChunk = true
                    fileUri = null
                    _currFileCount.value = _currFileCount.value + 1
                    _transferProgress.update { it.copy(bytesReceived = -1L) } // -1 indicates failed/skipped
                }

                is ConnectionResult.Error -> {
                    Log.e("BluetoothViewModel", "Connection error: ${result.message}")
                    fileTransferRepository.closeFile()
                    fileUri?.let { uri -> 
                        viewModelScope.launch { fileTransferRepository.deleteFile(uri) }
                    }
                    _state.update {
                        it.copy(isConnected = false, isConnecting = false, errorMessage = result.message)
                    }
                }
            }
        }.catch { throwable ->
            Log.e("BluetoothViewModel", "Connection flow error", throwable)
            fileTransferRepository.closeFile()
            fileUri?.let { uri -> 
                viewModelScope.launch { fileTransferRepository.deleteFile(uri) }
            }
            bluetoothController.closeConnection()
            _state.update { it.copy(isConnected = false, isConnecting = false) }
        }.launchIn(kotlinx.coroutines.CoroutineScope(viewModelScope.coroutineContext + kotlinx.coroutines.Dispatchers.IO))
    }

    override fun onCleared() {
        Timber.d("BluetoothViewModel - onCleared called")
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
data class Image(val id: Long, val name: String, val uri: Uri)
data class Video(val id: Long, val name: String, val uri: Uri)
data class Audio(val id: Long, val name: String, val uri: Uri)
