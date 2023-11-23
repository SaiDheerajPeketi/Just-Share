package com.invincible.jedishare.presentation

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.invincible.jedishare.MainActivity
import com.invincible.jedishare.SharingApp
import com.invincible.jedishare.data.chat.toBluetoothMessage
import com.invincible.jedishare.data.chat.toFileInfo
import com.invincible.jedishare.domain.chat.BluetoothController
import com.invincible.jedishare.domain.chat.BluetoothDeviceDomain
import com.invincible.jedishare.domain.chat.ConnectionResult
import com.invincible.jedishare.getFileDetailsFromUri
import com.invincible.jedishare.presentation.components.CustomProgressIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
): ViewModel() {

    private val _state = MutableStateFlow(BluetoothUiState())
    var contentResolver: ContentResolver? = null
    val state = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDevices, state ->
        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDevices,
            messages = if(state.isConnected) state.messages else emptyList()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null

    init {
        bluetoothController.isConnected.onEach { isConnected ->
            _state.update { it.copy(isConnected = isConnected) }
        }.launchIn(viewModelScope)

        bluetoothController.errors.onEach { error ->
            _state.update { it.copy(
                errorMessage = error
            ) }
        }.launchIn(viewModelScope)
    }

    fun connectToDevice(device: BluetoothDeviceDomain) {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .connectToDevice(device, this@BluetoothViewModel)
            .listen()
    }

    fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update { it.copy(
            isConnecting = false,
            isConnected = false
        ) }
    }

    fun waitForIncomingConnections() {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController
            .startBluetoothServer(this@BluetoothViewModel)
            .listen()
    }

    private val _iterationCountFlow = MutableSharedFlow<Long>()
    fun getIterationCountFlow(): MutableSharedFlow<Long> = _iterationCountFlow

    private val _fileSizeState = MutableStateFlow<Long>(1)
    val fileInfoState: StateFlow<Long> = _fileSizeState
    fun setFileInfo(fileSize: Long?) {
        if (fileSize != null) {
            _fileSizeState.value = fileSize
        }
    }


    // Getting and Setting the UriList
    private val _uriList = mutableStateOf<List<Uri>>(emptyList())
    val uriList: State<List<Uri>> = _uriList

    fun setUriList(uris: List<Uri>) {
        _uriList.value = uris
    }

    fun getUriList(): List<Uri> {
        return _uriList.value
    }

    fun sendMessage(message: String){
        viewModelScope.launch {
            val iterationCountFlow = getIterationCountFlow() // Get the SharedFlow from the ViewModel
            val bluetoothMessage = bluetoothController.trySendMessage(message, iterationCountFlow, this@BluetoothViewModel)
            if(bluetoothMessage != null){
                _state.update { it.copy(
                    messages = it.messages + bluetoothMessage
                ) }
            }
        }
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    // Functions to send currSize to the progressIndicator
    private val _statee = MutableStateFlow(BluetoothUiState())
    val statee: StateFlow<BluetoothUiState> get() = _statee

    private fun updateState(newState: BluetoothUiState, ) {
        _statee.value = newState
    }

    private fun setGlobalSize(newCurrSize: Long, globalsize: Long?) {
        globalsize?.let { _statee.value.copy(currSize = newCurrSize, globalSize = it) }
            ?.let { updateState(it) }
//        updateState(globalsize?.let { _statee.value.copy(currSize = newCurrSize, globalSize = it) })
    }
    private fun updateCurrSize(newCurrSize: Long) {
        updateState(_statee.value.copy(currSize = newCurrSize))
    }

    var isFirst: Boolean = true

    private fun Flow<ConnectionResult>.listen(): Job {
        var currSize: Long = -1
        var fileUri: Uri? = null
        return onEach { result ->
            when(result) {
                ConnectionResult.ConnectionEstablished -> {
                    _state.update { it.copy(
                        isConnected = true,
                        isConnecting = false,
                        errorMessage = null
                    ) }
                }
                is ConnectionResult.TransferSucceeded -> {
                    _state.update { it.copy(
                        messages = it.messages + result.message.toString().toBluetoothMessage(false)
                    ) }
                    if(isFirst){
                        isFirst = false
                        currSize = 0
                        val fileInfo = result.message.toFileInfo()

                        val fileName = fileInfo?.fileName ?: ""
                        val format = fileInfo?.format ?: ""
                        val mimeType = fileInfo?.mimeType ?: ""

                        Log.e("HELLOME", fileInfo.toString())

                        // Setting globalSize
                        if (fileInfo != null) {
                            fileInfo.size?.let { setGlobalSize(1, it.toLong()) }
                        }

                        // Create a content values to store file information
                        val values = ContentValues().apply {
                            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$fileName")
                            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                when {
                                    mimeType.startsWith("image/") -> put(
                                        MediaStore.Images.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_PICTURES
                                    )
                                    mimeType.startsWith("audio/") -> put(
                                        MediaStore.Audio.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_MUSIC
                                    )
                                    mimeType.startsWith("video/") -> put(
                                        MediaStore.Video.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_MOVIES
                                    )
                                }
                            }
                        }

                        // Get the content URI for the new media entry
                        val contentUri = when {
                            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        }

                        fileUri = contentResolver?.insert(contentUri, values)

                    }
                    else{
                        try {
                            fileUri?.let {
//                                // Open an output stream to write file data
//                                delay(1)

                                contentResolver?.openOutputStream(it, "wa")?.use { outputStream ->
//                                    Log.e("HELLOME",result.message.size.toString())
//                                    Log.e("HELLOME",outputStream.toString())
                                    currSize = currSize + result.message.size
                                    outputStream.write(result.message)
//                                    outputStream.write(result.message, currSize.toInt(),result.message.size)


                                    updateCurrSize(currSize.toLong())
//
                                }
//
                                Log.e("HELLOME", "File saved to MediaStore: $it " + currSize)
//
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
//                            Log.e("HELLOME", "Failed to write to MediaStore" + e.toString())
                            Log.e("HELLOERROR", e.toString())
                        }
                    }
                }
                is ConnectionResult.Error -> {
                    _state.update { it.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = result.message
                    ) }
                }
            }
        }
            .catch { throwable ->
                bluetoothController.closeConnection()
                _state.update { it.copy(
                    isConnected = false,
                    isConnecting = false,
                ) }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}

class BluetoothViewModelFactory @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BluetoothViewModel(bluetoothController) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}