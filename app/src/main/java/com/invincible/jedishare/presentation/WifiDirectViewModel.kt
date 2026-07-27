package com.invincible.jedishare.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
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

    /** Shared ActionListener that logs failure reason. */
    private val actionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            Log.d(TAG, "P2P action succeeded")
        }
        override fun onFailure(reason: Int) {
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
        }
    }

    /**
     * Initialises the WifiP2pManager and channel.
     * Must be called from the Activity's [onCreate].
     */
    fun initialize(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        wifiP2pManager = manager
        wifiP2pChannel = channel
        // Request existing connection info
        manager.requestConnectionInfo(channel) { info ->
            if (info?.groupOwnerAddress != null) {
                Log.d(TAG, "Existing connection info found")
            }
        }
    }

    // ── P2P State Updates (called from WiFiDirectBroadcastReceiver) ────────────

    fun onWifiDirectEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isWifiDirectEnabled = enabled) }
        if (enabled) startDiscovery()
        else {
            _uiState.update { it.copy(peers = emptyList()) }
        }
    }

    fun onDiscoveryStateChanged(discovering: Boolean) {
        _uiState.update { it.copy(isDiscovering = discovering) }
    }

    fun onThisDeviceChanged(name: String?) {
        _uiState.update { it.copy(thisDeviceName = name ?: "") }
    }

    fun onPeersChanged() {
        wifiP2pManager?.requestPeers(wifiP2pChannel, peerListListener)
    }

    fun onConnectionChanged() {
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (_uiState.value.isWifiDirectEnabled && !_uiState.value.isDiscovering) {
            wifiP2pManager?.discoverPeers(wifiP2pChannel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        wifiP2pManager?.connect(wifiP2pChannel, config, actionListener)
    }

    fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    fun disconnectP2P() {
        wifiP2pManager?.let { mgr ->
            wifiP2pChannel?.let { ch ->
                mgr.cancelConnect(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Log.d(TAG, "cancelConnect success") }
                    override fun onFailure(r: Int) { Log.d(TAG, "cancelConnect failed: $r") }
                })
                mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { Log.d(TAG, "removeGroup success") }
                    override fun onFailure(r: Int) { Log.d(TAG, "removeGroup failed: $r") }
                })
            }
        }
        _uiState.update { it.copy(isConnected = false, peers = emptyList(), connectionStatus = "") }
    }

    fun onLocationDisabled() {
        disconnectP2P()
    }

    // ── Permission Dialog (replaces standalone PermissionViewModel) ─────────────

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        }
    }

    fun dismissPermissionDialog() {
        if (visiblePermissionDialogQueue.isNotEmpty()) {
            visiblePermissionDialogQueue.removeFirst()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiP2pChannel?.close()
    }
}
