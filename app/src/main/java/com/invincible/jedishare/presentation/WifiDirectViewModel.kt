package com.invincible.jedishare.presentation

import timber.log.Timber

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject

/**
 * UI state for the WiFi Direct device selection screen.
 */
data class WifiDirectUiState(
    val peers: List<WifiP2pDevice> = emptyList(),
    val isConnected: Boolean = false,
    val isWifiDirectEnabled: Boolean = false,
    val isDiscovering: Boolean = false,
    val connectionStatus: String = "",
    val thisDeviceName: String = "",
    val errorMessage: String? = null
)

/**
 * ViewModel for WiFi Direct peer discovery and connection management.
 *
 * Fix (TODO-18): Extracted WifiP2pManager calls from [WifiDirectDeviceSelectActivity]
 * into this ViewModel. The Activity now only handles lifecycle events (register/unregister
 * broadcast receiver) and delegates all P2P logic here.
 */
@HiltViewModel
class WifiDirectViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val TAG = "WifiDirectVM"

    private val _uiState = MutableStateFlow(WifiDirectUiState())
    val uiState: StateFlow<WifiDirectUiState> = _uiState.asStateFlow()

    /** Permission dialog queue — used by PermissionDialog composable. */
    val visiblePermissionDialogQueue = mutableStateListOf<String>()

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var receiver: com.invincible.jedishare.WiFiDirectBroadcastReceiver? = null
    private var communicationServiceStarted = false
    private var isSenderRole = true
    private val p2pRetryDelaysMs = listOf(300L, 700L, 1_200L, 2_000L)

    /** Shared ActionListener for discovery operations. */
    private val actionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            Timber.d("WifiDirectViewModel - onSuccess called")
            Log.d(TAG, "P2P action succeeded")
        }
        override fun onFailure(reason: Int) {
            Timber.d("WifiDirectViewModel - onFailure called")
            val msg = "Wi-Fi Direct failed: ${failureReason(reason)}"
            Log.e(TAG, msg)
            _uiState.update { it.copy(errorMessage = msg) }
        }
    }

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList.toList()
        _uiState.update { it.copy(peers = peers) }
    }

    @SuppressLint("MissingPermission")
    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        Log.d(TAG, "ConnectionInfo: $info")
        if (!info.groupFormed) {
            _uiState.update {
                it.copy(
                    isConnected = false,
                    connectionStatus = if (isSenderRole) "" else "hosting"
                )
            }
            return@ConnectionInfoListener
        }

        startCommunicationService(info)
        if (!info.isGroupOwner) {
            _uiState.update {
                it.copy(isConnected = true, connectionStatus = "connected", errorMessage = null)
            }
            return@ConnectionInfoListener
        }

        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        if (manager == null || channel == null) {
            _uiState.update { it.copy(isConnected = false, connectionStatus = "hosting") }
            return@ConnectionInfoListener
        }

        manager.requestGroupInfo(channel) { group ->
            val hasConnectedClient = group?.clientList?.isNotEmpty() == true
            Log.d(TAG, "GroupInfo: connectedClients=${group?.clientList?.size ?: 0}")
            _uiState.update {
                it.copy(
                    isConnected = hasConnectedClient,
                    connectionStatus = if (hasConnectedClient) "connected" else "hosting",
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Initialises the WifiP2pManager and channel.
     * Must be called from the Activity's [onCreate].
     */
    fun initialize(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        Timber.d("WifiDirectViewModel - initialize called")
        wifiP2pManager = manager
        wifiP2pChannel = channel
        // Request existing connection info
        manager.requestConnectionInfo(channel) { info ->
            if (info?.groupFormed == true) {
                Log.d(TAG, "Existing connection info found")
                connectionInfoListener.onConnectionInfoAvailable(info)
            }
        }
        
        if (receiver == null) {
            receiver = com.invincible.jedishare.WiFiDirectBroadcastReceiver(this)
            val intentFilter = android.content.IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            }
            // Register receiver with the application context
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
        }
    }

    // ── P2P State Updates (called from WiFiDirectBroadcastReceiver) ────────────

    fun setTransferRole(isSender: Boolean) {
        Timber.d("WifiDirectViewModel - setTransferRole called")
        isSenderRole = isSender
    }

    fun onWifiDirectEnabled(enabled: Boolean) {
        Timber.d("WifiDirectViewModel - onWifiDirectEnabled called")
        _uiState.update { it.copy(isWifiDirectEnabled = enabled) }
        if (enabled) {
            if (isSenderRole) startDiscovery() else startHosting()
        }
        else {
            _uiState.update { it.copy(peers = emptyList(), isConnected = false, isDiscovering = false) }
            stopCommunicationService()
        }
    }

    fun onDiscoveryStateChanged(discovering: Boolean) {
        Timber.d("WifiDirectViewModel - onDiscoveryStateChanged called")
        _uiState.update { it.copy(isDiscovering = discovering) }
    }

    fun onThisDeviceChanged(name: String?) {
        Timber.d("WifiDirectViewModel - onThisDeviceChanged called")
        _uiState.update { it.copy(thisDeviceName = name ?: "") }
    }

    fun onDisconnected() {
        Timber.d("WifiDirectViewModel - onDisconnected called")
        if (!isSenderRole && _uiState.value.connectionStatus == "hosting") {
            return
        }
        _uiState.update { it.copy(isConnected = false, connectionStatus = "") }
        stopCommunicationService()
    }

    fun onPeersChanged() {
        Timber.d("WifiDirectViewModel - onPeersChanged called")
        wifiP2pManager?.requestPeers(wifiP2pChannel, peerListListener)
    }

    fun onConnectionChanged() {
        Timber.d("WifiDirectViewModel - onConnectionChanged called")
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        Timber.d("WifiDirectViewModel - startDiscovery called")
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (!_uiState.value.isWifiDirectEnabled) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi is disabled. Please turn on Wi-Fi and try again.") }
            return
        }
        if (!_uiState.value.isDiscovering &&
            !_uiState.value.isConnected
        ) {
            manager.discoverPeers(channel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        Timber.d("WifiDirectViewModel - stopDiscovery called")
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (_uiState.value.isDiscovering) {
            manager.stopPeerDiscovery(channel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun startHosting() {
        Timber.d("WifiDirectViewModel - startHosting called")
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (!_uiState.value.isWifiDirectEnabled) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi is disabled. Please turn on Wi-Fi and try again.") }
            return
        }
        if (_uiState.value.isConnected || _uiState.value.connectionStatus == "hosting") {
            return
        }
        _uiState.update { it.copy(connectionStatus = "hosting", errorMessage = null) }
        viewModelScope.launch {
            val failure = runP2pAction(
                label = "createGroup",
                retryReasons = setOf(WifiP2pManager.BUSY)
            ) { listener ->
                manager.createGroup(channel, listener)
            }

            if (failure == null) {
                requestConnectionInfo()
            } else {
                val msg = "Wi-Fi Direct hosting failed: ${failureReason(failure)}"
                Log.e(TAG, msg)
                _uiState.update { it.copy(connectionStatus = "", errorMessage = msg) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        Timber.d("WifiDirectViewModel - connectToDevice called")
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (_uiState.value.isConnected || _uiState.value.connectionStatus == "connecting") return

        val currentPeer = _uiState.value.peers.firstOrNull {
            it.deviceAddress == device.deviceAddress
        }
        if (currentPeer == null) {
            _uiState.update {
                it.copy(errorMessage = "Device is no longer available. Refreshing nearby devices.")
            }
            startDiscovery()
            return
        }

        _uiState.update { it.copy(connectionStatus = "connecting", errorMessage = null) }
        val config = WifiP2pConfig().apply {
            deviceAddress = currentPeer.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = WifiP2pConfig.GROUP_OWNER_INTENT_MIN
        }
        viewModelScope.launch {
            prepareAndConnect(manager, channel, config)
        }
    }

    fun requestConnectionInfo() {
        Timber.d("WifiDirectViewModel - requestConnectionInfo called")
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    fun disconnectP2P() {
        Timber.d("WifiDirectViewModel - disconnectP2P called")
        stopCommunicationService()
        wifiP2pManager?.let { mgr ->
            wifiP2pChannel?.let { ch ->
                mgr.cancelConnect(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { 
                        Timber.d("WifiDirectViewModel - onSuccess called")
                        Log.d(TAG, "cancelConnect success") 
                    }
                    override fun onFailure(r: Int) { 
                        Timber.d("WifiDirectViewModel - onFailure called")
                        Log.d(TAG, "cancelConnect failed: $r") 
                    }
                })
                mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { 
                        Timber.d("WifiDirectViewModel - onSuccess called")
                        Log.d(TAG, "removeGroup success") 
                    }
                    override fun onFailure(r: Int) { 
                        Timber.d("WifiDirectViewModel - onFailure called")
                        Log.d(TAG, "removeGroup failed: $r") 
                    }
                })
            }
        }
        _uiState.update { it.copy(isConnected = false, peers = emptyList(), connectionStatus = "") }
    }

    private fun startCommunicationService(info: android.net.wifi.p2p.WifiP2pInfo) {
        if (communicationServiceStarted) return
        val role = if (info.isGroupOwner) {
            com.invincible.jedishare.CommunicationService.SERVER_ROLE
        } else {
            com.invincible.jedishare.CommunicationService.CLIENT_ROLE
        }
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
        if (role == com.invincible.jedishare.CommunicationService.CLIENT_ROLE && groupOwnerAddress.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi Direct group owner address is unavailable") }
            return
        }

        val intent = Intent(context, com.invincible.jedishare.CommunicationService::class.java).apply {
            action = com.invincible.jedishare.CommunicationService.ACTION_START_COMMUNICATION
            putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_COMMUNICATION_ROLE, role)
            groupOwnerAddress?.let { putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_GROUP_OWNER_ADDRESS, it) }
            putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_GROUP_OWNER_PORT, 8988)
            putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_DEVICE_NAME, _uiState.value.thisDeviceName.ifBlank { Build.MODEL })
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onSuccess {
            communicationServiceStarted = true
        }.onFailure { throwable ->
            Log.e(TAG, "Could not start Wi-Fi Direct communication service", throwable)
            _uiState.update { it.copy(errorMessage = "Could not start Wi-Fi Direct transfer") }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun prepareAndConnect(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        config: WifiP2pConfig
    ) {
        val connectFailure = runP2pAction(
            label = "connect",
            retryReasons = setOf(WifiP2pManager.BUSY)
        ) { listener ->
            manager.connect(channel, config, listener)
        }

        if (connectFailure == null) {
            Log.d(TAG, "connect request accepted")
            _uiState.update { it.copy(connectionStatus = "connecting", errorMessage = null) }
        } else {
            val msg = "Wi-Fi Direct connect failed: ${failureReason(connectFailure)}"
            Log.e(TAG, msg)
            _uiState.update { it.copy(connectionStatus = "", errorMessage = msg) }
            startDiscovery()
        }
    }

    private suspend fun runP2pAction(
        label: String,
        retryReasons: Set<Int>,
        action: (WifiP2pManager.ActionListener) -> Unit
    ): Int? {
        var failureReason: Int? = null
        repeat(p2pRetryDelaysMs.size + 1) { attempt ->
            failureReason = awaitP2pAction(action)
            val reason = failureReason
            if (reason == null) {
                Log.d(TAG, "$label success")
                return null
            }

            Log.d(TAG, "$label failed: ${failureReason(reason)}")
            val shouldRetry = reason in retryReasons && attempt < p2pRetryDelaysMs.size
            if (!shouldRetry) {
                return reason
            }

            delay(p2pRetryDelaysMs[attempt])
        }
        return failureReason
    }

    private suspend fun awaitP2pAction(
        action: (WifiP2pManager.ActionListener) -> Unit
    ): Int? = suspendCancellableCoroutine { continuation ->
        action(object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onFailure(reason: Int) {
                if (continuation.isActive) continuation.resume(reason)
            }
        })
    }

    private fun failureReason(reason: Int): String = when (reason) {
        WifiP2pManager.BUSY -> "Framework busy"
        WifiP2pManager.ERROR -> "Internal error"
        WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
        else -> "Unknown ($reason)"
    }

    private fun stopCommunicationService() {
        if (!communicationServiceStarted) return
        val intent = Intent(context, com.invincible.jedishare.CommunicationService::class.java).apply {
            action = com.invincible.jedishare.CommunicationService.ACTION_STOP_COMMUNICATION
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Could not stop Wi-Fi Direct communication service", throwable)
        }
        communicationServiceStarted = false
    }

    fun onLocationDisabled() {
        Timber.d("WifiDirectViewModel - onLocationDisabled called")
        disconnectP2P()
    }

    // ── Permission Dialog (replaces standalone PermissionViewModel) ─────────────

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        Timber.d("WifiDirectViewModel - onPermissionResult called")
        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        }
    }

    fun dismissPermissionDialog() {
        Timber.d("WifiDirectViewModel - dismissPermissionDialog called")
        if (visiblePermissionDialogQueue.isNotEmpty()) {
            visiblePermissionDialogQueue.removeFirst()
        }
    }

    override fun onCleared() {
        Timber.d("WifiDirectViewModel - onCleared called")
        super.onCleared()
        disconnectP2P()
        receiver?.let { 
            try { 
                context.unregisterReceiver(it) 
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister receiver: ${e.message}")
            } 
        }
        receiver = null
        wifiP2pChannel?.close()
    }
}
