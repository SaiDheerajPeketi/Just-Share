package com.invincible.jedishare.presentation

import timber.log.Timber

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** Shared ActionListener that logs failure reason. */
    private val actionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            Timber.d("WifiDirectViewModel - onSuccess called")
            Log.d(TAG, "P2P action succeeded")
        }
        override fun onFailure(reason: Int) {
            Timber.d("WifiDirectViewModel - onFailure called")
            val msg = "Wi-Fi Direct failed: " + when (reason) {
                WifiP2pManager.BUSY          -> "Framework busy"
                WifiP2pManager.ERROR         -> "Internal error"
                WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
                else                         -> "Unknown ($reason)"
            }
            Log.e(TAG, msg)
            _uiState.update { it.copy(errorMessage = msg) }
            disconnectP2P()
        }
    }

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val peers = peerList.deviceList.toList()
        _uiState.update { it.copy(peers = peers) }
    }

    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        Log.d(TAG, "ConnectionInfo: $info")
        if (info.groupFormed) {
            _uiState.update { it.copy(isConnected = true, connectionStatus = "connected") }
            
            val intent = Intent(context, com.invincible.jedishare.CommunicationService::class.java).apply {
                action = com.invincible.jedishare.CommunicationService.ACTION_START_COMMUNICATION
                val role = if (info.isGroupOwner) com.invincible.jedishare.CommunicationService.SERVER_ROLE else com.invincible.jedishare.CommunicationService.CLIENT_ROLE
                putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_COMMUNICATION_ROLE, role)
                info.groupOwnerAddress?.hostAddress?.let { address ->
                    putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_GROUP_OWNER_ADDRESS, address)
                }
                putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_GROUP_OWNER_PORT, 8988)
                putExtra(com.invincible.jedishare.CommunicationService.EXTRAS_DEVICE_NAME, _uiState.value.thisDeviceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
            if (info?.groupOwnerAddress != null) {
                Log.d(TAG, "Existing connection info found")
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
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
        }
    }

    // ── P2P State Updates (called from WiFiDirectBroadcastReceiver) ────────────

    fun onWifiDirectEnabled(enabled: Boolean) {
        Timber.d("WifiDirectViewModel - onWifiDirectEnabled called")
        _uiState.update { it.copy(isWifiDirectEnabled = enabled) }
        if (enabled) startDiscovery()
        else {
            _uiState.update { it.copy(peers = emptyList()) }
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
        _uiState.update { it.copy(isConnected = false, connectionStatus = "") }
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
        if (_uiState.value.isWifiDirectEnabled && !_uiState.value.isDiscovering) {
            wifiP2pManager?.discoverPeers(wifiP2pChannel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        Timber.d("WifiDirectViewModel - stopDiscovery called")
        if (_uiState.value.isDiscovering) {
            wifiP2pManager?.stopPeerDiscovery(wifiP2pChannel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        Timber.d("WifiDirectViewModel - connectToDevice called")
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        wifiP2pManager?.connect(wifiP2pChannel, config, actionListener)
    }

    fun requestConnectionInfo() {
        Timber.d("WifiDirectViewModel - requestConnectionInfo called")
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    fun disconnectP2P() {
        Timber.d("WifiDirectViewModel - disconnectP2P called")
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
