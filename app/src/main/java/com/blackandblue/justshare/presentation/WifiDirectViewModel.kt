package com.blackandblue.justshare.presentation

import timber.log.Timber

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackandblue.justshare.domain.transfer.TransferOrchestration
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.hasLocalTransferPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
    private var receiver: BroadcastReceiver? = null
    private var localSessionOpen = false
    private var localSessionId = 0L
    private var communicationServiceStarted = false
    private var isSenderRole = true
    private val p2pRetryDelaysMs = listOf(300L, 700L, 1_200L, 2_000L, 3_000L, 4_000L)
    private var activeConnectJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var discoveryJob: Job? = null
    private var hostingJob: Job? = null
    private var discoveryRefreshJob: Job? = null
    private var restartJob: Job? = null
    private var stopDiscoveryJob: Job? = null

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val DISCOVERY_REFRESH_MS = 10_000L
    }

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
        if (!requireWifiDirectPermission()) return@PeerListListener
        val peers = peerList.deviceList.toList()
        _uiState.update { it.copy(peers = peers) }
    }

    @SuppressLint("MissingPermission")
    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (!requireWifiDirectPermission()) return@ConnectionInfoListener
        Log.d(TAG, "ConnectionInfo: $info")
        if (!info.groupFormed) {
            val groupState = TransferOrchestration.wifiGroupState(
                senderRole = isSenderRole,
                groupFormed = false,
                groupOwner = info.isGroupOwner,
                connectedClientCount = 0
            )
            _uiState.update {
                it.copy(
                    isConnected = groupState.connected,
                    connectionStatus = groupState.status
                )
            }
            return@ConnectionInfoListener
        }

        cancelConnectTimeout()
        startCommunicationService(info)
        if (!info.isGroupOwner) {
            val groupState = TransferOrchestration.wifiGroupState(
                senderRole = isSenderRole,
                groupFormed = true,
                groupOwner = false,
                connectedClientCount = 0
            )
            _uiState.update {
                it.copy(
                    isConnected = groupState.connected,
                    connectionStatus = groupState.status,
                    errorMessage = null
                )
            }
            return@ConnectionInfoListener
        }

        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        if (manager == null || channel == null) {
            _uiState.update { it.copy(isConnected = false, connectionStatus = "hosting") }
            return@ConnectionInfoListener
        }

        val session = localSessionId
        try {
            manager.requestGroupInfo(channel) { group ->
                if (!isCurrentChannel(session, channel)) return@requestGroupInfo
                val connectedClientCount = group?.clientList?.size ?: 0
                val groupState = TransferOrchestration.wifiGroupState(
                    senderRole = isSenderRole,
                    groupFormed = true,
                    groupOwner = true,
                    connectedClientCount = connectedClientCount
                )
                val hasConnectedClient = groupState.connected
                Log.d(TAG, "GroupInfo: connectedClients=$connectedClientCount")
                val wasConnected = _uiState.value.isConnected
                _uiState.update {
                    it.copy(
                        // Only mark as "connected" once a real client has joined the group.
                        // Until then stay in "hosting" so we don't prematurely navigate.
                        isConnected = groupState.connected,
                        connectionStatus = groupState.status,
                        errorMessage = null
                    )
                }
                if (wasConnected && !hasConnectedClient) {
                    Log.d(TAG, "Client disconnected, resetting CommunicationService")
                    stopCommunicationService()
                    com.blackandblue.justshare.CommunicationService.clearTransferUpdate()
                    // Restart server socket to accept next connection
                    restartJob?.cancel()
                    restartJob = viewModelScope.launch {
                        delay(800)
                        if (isCurrentChannel(session, channel)) startCommunicationService(info)
                    }
                } else if (!wasConnected && hasConnectedClient) {
                    Log.d(TAG, "New client connected — communication service should already be running")
                }
            }
        } catch (_: SecurityException) {
            onPermissionRevoked()
        }
    }

    /** Opens only after the selected Wi-Fi grant is checked. Reuses the lease
     * across discovery/progress; a later entry gets a new lease after cleanup. */
    fun beginLocalSession(): Long? {
        if (!hasWifiDirectPermission()) {
            onPermissionRevoked()
            return null
        }
        if (!localSessionOpen) {
            localSessionId++
            localSessionOpen = true
            _uiState.update { it.copy(errorMessage = null) }
        }
        return localSessionId
    }

    /** A disposed old destination must never stop its replacement session. */
    fun releaseLocalSession(expectedSession: Long): Boolean {
        if (!localSessionOpen || expectedSession != localSessionId) return false
        closeLocalSession(errorMessage = null)
        return true
    }

    /** Called by allowed discovery content with a freshly initialized channel. */
    fun initialize(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        if (!requireWifiDirectPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { channel.close() }
            return
        }
        val previousChannel = wifiP2pChannel
        if (previousChannel != null && previousChannel !== channel) cancelLocalJobs()
        wifiP2pManager = manager
        wifiP2pChannel = channel
        if (previousChannel !== channel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching { previousChannel?.close() }
        }
        registerLocalReceiver()
        requestConnectionInfo(existingGroupOnly = true)
    }

    /** The registration boundary is shared with the fake-Context lifecycle tests.
     * A queued callback belongs to this lease and this receiver, never a later one. */
    internal fun registerLocalReceiver() {
        if (!requireWifiDirectPermission() || receiver != null) return
        val session = localSessionId
        val delegate = com.blackandblue.justshare.WiFiDirectBroadcastReceiver(this)
        val registered = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION && isInitialStickyBroadcast) return
                if (receiver === this && isCurrentSession(session)) delegate.onReceive(context, intent)
            }
        }
        val intentFilter = android.content.IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        receiver = registered
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(registered, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(registered, intentFilter)
            }
        } catch (_: SecurityException) {
            onPermissionRevoked()
        }
    }

    private fun isCurrentSession(session: Long): Boolean =
        localSessionOpen && localSessionId == session && requireWifiDirectPermission()

    private fun isCurrentChannel(session: Long, channel: WifiP2pManager.Channel): Boolean =
        wifiP2pChannel === channel && isCurrentSession(session)

    @SuppressLint("MissingPermission")
    private fun requestPeers(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        val session = localSessionId
        if (!isCurrentChannel(session, channel)) return
        try {
            manager.requestPeers(channel) { peers ->
                if (isCurrentChannel(session, channel)) peerListListener.onPeersAvailable(peers)
            }
        } catch (_: SecurityException) {
            onPermissionRevoked()
        }
    }

    // ── P2P State Updates (called from WiFiDirectBroadcastReceiver) ────────────

    fun setTransferRole(isSender: Boolean) {
        Timber.d("WifiDirectViewModel - setTransferRole called")
        isSenderRole = isSender
    }

    fun onWifiDirectEnabled(enabled: Boolean) {
        if (!requireWifiDirectPermission()) return
        Timber.d("WifiDirectViewModel - onWifiDirectEnabled called")
        _uiState.update { it.copy(isWifiDirectEnabled = enabled) }
        if (enabled) {
            if (isSenderRole) startDiscovery() else startHosting()
        }
        else {
            discoveryJob?.cancel()
            discoveryJob = null
            stopDiscoveryRefreshLoop()
            _uiState.update { it.copy(peers = emptyList(), isConnected = false, isDiscovering = false) }
            stopCommunicationService()
        }
    }

    fun onDiscoveryStateChanged(discovering: Boolean) {
        if (!requireWifiDirectPermission()) return
        Timber.d("WifiDirectViewModel - onDiscoveryStateChanged called")
        _uiState.update { it.copy(isDiscovering = discovering) }
    }

    fun onThisDeviceChanged(name: String?) {
        if (!requireWifiDirectPermission()) return
        Timber.d("WifiDirectViewModel - onThisDeviceChanged called")
        _uiState.update { it.copy(thisDeviceName = name ?: "") }
    }

    // Timestamp when a connect attempt was last *accepted* by the framework
    private var lastConnectAttemptTime = 0L

    fun onDisconnected() {
        if (!requireWifiDirectPermission()) return
        Timber.d("WifiDirectViewModel - onDisconnected called")
        // The receiver (host) handles its own lifecycle via requestGroupInfo, not here
        if (!isSenderRole && _uiState.value.connectionStatus == "hosting") {
            return
        }
        
        val currentStatus = _uiState.value.connectionStatus
        val msSinceConnect = System.currentTimeMillis() - lastConnectAttemptTime
        
        // If we initiated a connect very recently (< 2s ago), this disconnect is the
        // OS tearing down old P2P interfaces — NOT a real failure. Skip it.
        if (currentStatus == "connecting" && msSinceConnect < 2000) {
            Timber.d("WifiDirectViewModel - Ignoring spurious disconnect right after connect attempt (${msSinceConnect}ms)")
            return
        }
        
        val wasConnecting = currentStatus == "connecting"
        val errorMsg = if (wasConnecting) "Failed to connect: device did not respond." else null
        
        _uiState.update { it.copy(isConnected = false, connectionStatus = "", errorMessage = errorMsg ?: it.errorMessage) }
        stopCommunicationService()
        
        if (wasConnecting) {
            cancelConnectTimeout()
            activeConnectJob?.cancel()
            Timber.d("WifiDirectViewModel - Connection failed after ${msSinceConnect}ms, restarting discovery")
            // Cancel dangling connection locks before re-scanning
            wifiP2pManager?.let { mgr ->
                wifiP2pChannel?.let { ch ->
                    mgr.cancelConnect(ch, null)
                }
            }
            val session = localSessionId
            restartJob?.cancel()
            restartJob = viewModelScope.launch {
                delay(1000)
                if (isCurrentSession(session)) startDiscovery()
            }
        }
    }

    fun onPeersChanged() {
        Timber.d("WifiDirectViewModel - onPeersChanged called")
        if (!isLocationModeEnabled()) {
            _uiState.update {
                it.copy(
                    isDiscovering = false,
                    errorMessage = "Turn on Location to discover Wi-Fi Direct devices."
                )
            }
            return
        }
        if (!requireWifiDirectPermission()) return
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        requestPeers(manager, channel)
    }

    fun onConnectionChanged() {
        Timber.d("WifiDirectViewModel - onConnectionChanged called")
        requestConnectionInfo()
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        Timber.d("WifiDirectViewModel - startDiscovery called")
        if (!requireWifiDirectPermission()) return
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (!_uiState.value.isWifiDirectEnabled) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi is disabled. Please turn on Wi-Fi and try again.") }
            return
        }
        if (!isSenderRole || _uiState.value.isConnected) {
            return
        }
        if (!isLocationModeEnabled()) {
            _uiState.update {
                it.copy(
                    isDiscovering = false,
                    errorMessage = "Turn on Location to discover Wi-Fi Direct devices."
                )
            }
            return
        }
        if (discoveryJob?.isActive == true) {
            return
        }
        if (_uiState.value.connectionStatus == "connecting") {
            activeConnectJob?.cancel()
            cancelConnectTimeout()
        }
        _uiState.update { it.copy(connectionStatus = "") }
        discoveryJob = viewModelScope.launch {
            awaitP2pAction { manager.cancelConnect(channel, it) }
            if (_uiState.value.isDiscovering) {
                // Stop discovery first to clear system cache
                manager.stopPeerDiscovery(channel, null)
                kotlinx.coroutines.delay(300)
            }
            
            val failure = runP2pAction(
                label = "discoverPeers",
                retryReasons = setOf(WifiP2pManager.BUSY)
            ) { listener ->
                manager.discoverPeers(channel, listener)
            }
            
            if (failure == null) {
                _uiState.update { it.copy(isDiscovering = true, errorMessage = null) }
            } else {
                val msg = "Wi-Fi Direct failed: ${failureReason(failure)}"
                Log.e(TAG, msg)
                _uiState.update { it.copy(errorMessage = msg) }
            }
            
            // Manually request peers just in case the system doesn't broadcast a change
            if (!requireWifiDirectPermission()) return@launch
            requestPeers(manager, channel)
            if (failure == null) {
                startDiscoveryRefreshLoop()
            }
            discoveryJob = null
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!requireWifiDirectPermission()) return
        Timber.d("WifiDirectViewModel - stopDiscovery called")
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        stopDiscoveryRefreshLoop()
        if (_uiState.value.isDiscovering) {
            stopDiscoveryJob?.cancel()
            stopDiscoveryJob = viewModelScope.launch {
                val failure = runP2pAction(
                    label = "stopDiscovery",
                    retryReasons = setOf(WifiP2pManager.BUSY)
                ) { listener ->
                    manager.stopPeerDiscovery(channel, listener)
                }
                
                if (failure == null) {
                    _uiState.update { it.copy(isDiscovering = false) }
                } else {
                    Log.e(TAG, "Wi-Fi Direct stop discovery failed: ${failureReason(failure)}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startHosting() {
        Timber.d("WifiDirectViewModel - startHosting called")
        if (!requireWifiDirectPermission()) return
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
        hostingJob?.cancel()
        hostingJob = viewModelScope.launch {
            // Clean up any existing state to prevent BUSY error
            awaitP2pAction { manager.cancelConnect(channel, it) }
            awaitP2pAction { manager.stopPeerDiscovery(channel, it) }
            
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
        if (!requireWifiDirectPermission()) return
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        if (_uiState.value.isConnected || !isSenderRole) {
            return
        }
        // Prevent double-connect. But if stuck "connecting" > 8s, the attempt has timed out.
        val isStaleConnecting = _uiState.value.connectionStatus == "connecting" &&
            System.currentTimeMillis() - lastConnectAttemptTime > 8000
        if (_uiState.value.connectionStatus == "connecting" && !isStaleConnecting) {
            return
        }
        if (isStaleConnecting) {
            Log.d(TAG, "connectToDevice: stale 'connecting' state cleared, allowing retry")
            _uiState.update { it.copy(connectionStatus = "") }
        }

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
        if (currentPeer.status == WifiP2pDevice.UNAVAILABLE) {
            _uiState.update {
                it.copy(errorMessage = "Device is no longer visible. Ask the receiver to restart visibility, then scan again.")
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
        activeConnectJob?.cancel()
        activeConnectJob = viewModelScope.launch {
            prepareAndConnect(manager, channel, config)
        }
    }

    fun requestConnectionInfo(existingGroupOnly: Boolean = false) {
        Timber.d("WifiDirectViewModel - requestConnectionInfo called")
        if (!requireWifiDirectPermission()) return
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        val session = localSessionId
        try {
            manager.requestConnectionInfo(channel) { info ->
                if (isCurrentChannel(session, channel) && (!existingGroupOnly || info?.groupFormed == true)) {
                    connectionInfoListener.onConnectionInfoAvailable(info)
                }
            }
        } catch (_: SecurityException) {
            onPermissionRevoked()
        }
    }

    fun disconnectP2P() {
        Timber.d("WifiDirectViewModel - disconnectP2P called")
        restartJob?.cancel()
        restartJob = null
        stopDiscoveryJob?.cancel()
        stopDiscoveryJob = null
        hostingJob?.cancel()
        hostingJob = null
        activeConnectJob?.cancel()
        discoveryJob?.cancel()
        discoveryJob = null
        cancelConnectTimeout()
        stopDiscoveryRefreshLoop()
        stopCommunicationService()
        if (hasWifiDirectPermission()) wifiP2pManager?.let { mgr ->
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
        if (!requireWifiDirectPermission()) return
        if (communicationServiceStarted) return
        val role = if (info.isGroupOwner) {
            com.blackandblue.justshare.CommunicationService.SERVER_ROLE
        } else {
            com.blackandblue.justshare.CommunicationService.CLIENT_ROLE
        }
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress
        if (role == com.blackandblue.justshare.CommunicationService.CLIENT_ROLE && groupOwnerAddress.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi Direct group owner address is unavailable") }
            return
        }

        val intent = Intent(context, com.blackandblue.justshare.CommunicationService::class.java).apply {
            action = com.blackandblue.justshare.CommunicationService.ACTION_START_COMMUNICATION
            putExtra(com.blackandblue.justshare.CommunicationService.EXTRAS_COMMUNICATION_ROLE, role)
            groupOwnerAddress?.let { putExtra(com.blackandblue.justshare.CommunicationService.EXTRAS_GROUP_OWNER_ADDRESS, it) }
            putExtra(com.blackandblue.justshare.CommunicationService.EXTRAS_GROUP_OWNER_PORT, 8988)
            putExtra(com.blackandblue.justshare.CommunicationService.EXTRAS_DEVICE_NAME, _uiState.value.thisDeviceName.ifBlank { Build.MODEL })
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
            lastConnectAttemptTime = System.currentTimeMillis()
            _uiState.update { it.copy(connectionStatus = "connecting", errorMessage = null) }
            stopDiscoveryRefreshLoop()
            scheduleConnectTimeout(config.deviceAddress)
        } else {
            val msg = "Wi-Fi Direct connect failed: ${failureReason(connectFailure)}"
            Log.e(TAG, msg)
            removePeer(config.deviceAddress)
            _uiState.update { it.copy(connectionStatus = "", errorMessage = msg) }
            startDiscovery()
        }
    }

    private fun scheduleConnectTimeout(deviceAddress: String?) {
        cancelConnectTimeout()
        connectTimeoutJob = viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_uiState.value.connectionStatus != "connecting") return@launch
            Log.d(TAG, "connect timed out for $deviceAddress")
            wifiP2pManager?.let { mgr ->
                wifiP2pChannel?.let { ch ->
                    awaitP2pAction { listener -> mgr.cancelConnect(ch, listener) }
                }
            }
            removePeer(deviceAddress)
            _uiState.update {
                it.copy(
                    isConnected = false,
                    connectionStatus = "",
                    errorMessage = "Could not connect. Make sure the receiver is visible and accepts the invite."
                )
            }
            connectTimeoutJob = null
            startDiscovery()
        }
    }

    private fun cancelConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
    }

    private fun removePeer(deviceAddress: String?) {
        if (deviceAddress.isNullOrBlank()) return
        _uiState.update { state ->
            state.copy(peers = state.peers.filterNot { it.deviceAddress == deviceAddress })
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscoveryRefreshLoop() {
        if (!requireWifiDirectPermission()) return
        if (discoveryRefreshJob?.isActive == true) return
        discoveryRefreshJob = viewModelScope.launch {
            while (true) {
                delay(DISCOVERY_REFRESH_MS)
                if (!requireWifiDirectPermission()) return@launch
                val manager = wifiP2pManager ?: return@launch
                val channel = wifiP2pChannel ?: return@launch
                val state = _uiState.value
                if (!isSenderRole || state.isConnected || state.connectionStatus == "connecting" || !state.isDiscovering) {
                    continue
                }
                // A new discovery cycle briefly empties the framework peer list. Keep a
                // visible peer selectable; PEERS_CHANGED will remove it if it leaves.
                if (state.peers.isNotEmpty()) {
                    continue
                }
                if (!isLocationModeEnabled()) {
                    _uiState.update {
                        it.copy(
                            isDiscovering = false,
                            errorMessage = "Turn on Location to discover Wi-Fi Direct devices."
                        )
                    }
                    continue
                }
                Log.d(TAG, "refreshing Wi-Fi Direct discovery")
                awaitP2pAction { manager.stopPeerDiscovery(channel, it) }
                delay(300)
                val failure = runP2pAction(
                    label = "discoverPeersRefresh",
                    retryReasons = setOf(WifiP2pManager.BUSY)
                ) { listener ->
                    manager.discoverPeers(channel, listener)
                }
                if (failure == null) {
                    _uiState.update { it.copy(isDiscovering = true, errorMessage = null) }
                    if (!requireWifiDirectPermission()) return@launch
                    requestPeers(manager, channel)
                } else {
                    Log.e(TAG, "Wi-Fi Direct refresh failed: ${failureReason(failure)}")
                }
            }
        }
    }

    private fun stopDiscoveryRefreshLoop() {
        discoveryRefreshJob?.cancel()
        discoveryRefreshJob = null
    }

    private fun isLocationModeEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: true
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
    ): Int? {
        if (!requireWifiDirectPermission()) return WifiP2pManager.ERROR
        return suspendCancellableCoroutine { continuation ->
            try {
                action(object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onFailure(reason: Int) {
                        if (continuation.isActive) continuation.resume(reason)
                    }
                })
            } catch (_: SecurityException) {
                onPermissionRevoked()
                if (continuation.isActive) continuation.resume(WifiP2pManager.ERROR)
            }
        }
    }

    private fun failureReason(reason: Int): String = when (reason) {
        WifiP2pManager.BUSY -> "Framework busy"
        WifiP2pManager.ERROR -> "Internal error"
        WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
        else -> "Unknown ($reason)"
    }

    private fun stopCommunicationService() {
        if (!communicationServiceStarted) return
        if (!hasWifiDirectPermission()) {
            runCatching { context.stopService(Intent(context, com.blackandblue.justshare.CommunicationService::class.java)) }
            communicationServiceStarted = false
            return
        }
        val stopIntent = Intent(context, com.blackandblue.justshare.CommunicationService::class.java).apply {
            action = com.blackandblue.justshare.CommunicationService.ACTION_STOP_COMMUNICATION
        }
        runCatching {
            context.startService(stopIntent)
        }.onFailure { throwable ->
            Log.e(TAG, "Could not send Wi-Fi Direct stop command", throwable)
            context.stopService(Intent(context, com.blackandblue.justshare.CommunicationService::class.java))
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
            visiblePermissionDialogQueue.removeAt(0)
        }
    }

    override fun onCleared() {
        closeLocalSession(errorMessage = null)
        super.onCleared()
    }

    fun onPermissionRevoked() {
        closeLocalSession(errorMessage = "Allow nearby sharing to continue.")
    }

    private fun cancelLocalJobs() {
        activeConnectJob?.cancel()
        activeConnectJob = null
        hostingJob?.cancel()
        hostingJob = null
        discoveryJob?.cancel()
        discoveryJob = null
        restartJob?.cancel()
        restartJob = null
        stopDiscoveryJob?.cancel()
        stopDiscoveryJob = null
        cancelConnectTimeout()
        stopDiscoveryRefreshLoop()
    }

    private fun closeLocalSession(errorMessage: String?) {
        // Close before releasing resources so queued callbacks cannot restart work.
        localSessionOpen = false
        cancelLocalJobs()
        // Direct stop does not create a service while access is denied or leaving.
        if (communicationServiceStarted) {
            runCatching { context.stopService(Intent(context, com.blackandblue.justshare.CommunicationService::class.java)) }
        }
        communicationServiceStarted = false
        val previousReceiver = receiver
        receiver = null
        previousReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        val manager = wifiP2pManager
        val channel = wifiP2pChannel
        wifiP2pManager = null
        wifiP2pChannel = null
        // Nearby permission may remain when only legacy file access was revoked.
        val nearbyPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, nearbyPermission) == PackageManager.PERMISSION_GRANTED && manager != null && channel != null) {
            runCatching { manager.cancelConnect(channel, null) }
            runCatching { manager.stopPeerDiscovery(channel, null) }
            runCatching { manager.removeGroup(channel, null) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { channel?.close() }
        _uiState.update { it.copy(peers = emptyList(), isConnected = false, isWifiDirectEnabled = false,
            isDiscovering = false, connectionStatus = "", errorMessage = errorMessage) }
    }

    private fun requireWifiDirectPermission(): Boolean {
        if (!hasWifiDirectPermission()) {
            onPermissionRevoked()
            return false
        }
        return localSessionOpen
    }

    private fun hasWifiDirectPermission(): Boolean =
        hasLocalTransferPermissions(context, LocalTransferMethod.WIFI)
}
