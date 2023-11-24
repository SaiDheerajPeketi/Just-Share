package com.invincible.jedishare

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invincible.jedishare.presentation.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

class WifiDirectDeviceSelectActivity : ComponentActivity() {

    private var communicationService = CommunicationService()

    val TAG = "myDebugTag"

    var peers: List<WifiP2pDevice> = emptyList()
    var connectionText = ""

    private var isWiFiDirectActive = false
    private var isDiscovering = false
    private var wifiP2PdeviceName = ""
    private var connectionInfoAvailable = false

    private var receiver: WiFiDirectBroadcastReceiver? = null
    private val intentFilter: IntentFilter = IntentFilter()
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var actionListener: WifiP2pManager.ActionListener? = null
    var peerListListener: WifiP2pManager.PeerListListener? = null
    var connectionInfoListener: WifiP2pManager.ConnectionInfoListener? = null

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeWiFiDirect()

        // Indicates a change in the Wi-Fi Direct status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi Direct connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)

        receiver = WiFiDirectBroadcastReceiver(this)

        actionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                var errorMsg = "Wi-fi direct Failed: "
                errorMsg += when (reason) {
                    WifiP2pManager.BUSY -> "Framework busy"
                    WifiP2pManager.ERROR -> "Internal error"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
                    else -> "Unknown message"
                }
                disconnectP2P()
                Log.d(TAG, errorMsg)
            }
        }

        peerListListener = WifiP2pManager.PeerListListener { peerList ->
            peers = emptyList()
            Log.d(TAG, "onCreate: $peerList")
            var localList = mutableListOf<WifiP2pDevice>()
            peerList.deviceList.forEach { device ->
                localList.add(device)
            }
            peers = localList
        }

        connectionInfoListener = WifiP2pManager.ConnectionInfoListener { wifiP2pInfo ->
            Log.d("TAG", "onConnectionInfoAvailable : " + wifiP2pInfo.toString())
            connectionText = wifiP2pInfo.toString()

            if (wifiP2pInfo.groupFormed) {
                Log.d(TAG, "connectionInfoListener")
                var role: Int
                if (wifiP2pInfo.isGroupOwner) {
                    Log.d(TAG, "I am host")
                    role = communicationService.SERVER_ROLE
                } else {
                    Log.d(TAG, "I am client")
                    role = communicationService.CLIENT_ROLE
                }

                var groupOwnerAddress: String = wifiP2pInfo.groupOwnerAddress.hostAddress
                var groupOwnerPort: Int = 8888
                connectionInfoAvailable = true

                var i: Intent = Intent(applicationContext, CommunicationService::class.java)
                i.setAction(communicationService.ACTION_START_COMMUNICATION)
                i.putExtra(communicationService.EXTRAS_COMMUNICATION_ROLE, role)
                i.putExtra(communicationService.EXTRAS_GROUP_OWNER_ADDRESS, groupOwnerAddress)
                i.putExtra(communicationService.EXTRAS_GROUP_OWNER_PORT, groupOwnerPort)
                i.putExtra(communicationService.EXTRAS_DEVICE_NAME, wifiP2PdeviceName)

//                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    startForegroundService(i)
//                } else {
//                    startService(i)
//                }
                startService(i)
                Log.d(TAG, "connectionInfoListener Done")
            }
        }

        setContent {
            JediShareTheme {
                val permissionViewModel = viewModel<PermissionViewModel>()
                val dialogQueue = permissionViewModel.visiblePermissionDialogQueue
                var peerListInternal by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
                var connectionStatusValue by remember { mutableStateOf("Connection Status: ") }

                LaunchedEffect(key1 = Unit) {
                    while (true) {
                        delay(1000L)
                        peerListInternal = peers
                        connectionStatusValue = connectionText
                    }
                }

                val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        permissionsToRequest.forEach { permission ->
                            permissionViewModel.onPermissionResult(
                                permission = permission,
                                isGranted = perms[permission] == true
                            )
                        }
                    }
                )

                SideEffect {
                    multiplePermissionResultLauncher.launch(permissionsToRequest)
                }

                dialogQueue
                    .reversed()
                    .forEach { permission ->
                        PermissionDialog(
                            permissionTextProvider = when (permission) {
                                Manifest.permission.INTERNET -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_COARSE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_FINE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                else -> return@forEach
                            },
                            isPermanentlyDeclined = !shouldShowRequestPermissionRationale(
                                permission
                            ),
                            onDismiss = permissionViewModel::dismissDialog,
                            onOkClick = {
                                permissionViewModel.dismissDialog()
                                multiplePermissionResultLauncher.launch(
                                    arrayOf(permission)
                                )
                            },
                            onGoToAppSettingsClick = ::openAppSettings
                        )
                    }

                /*****************************************************************
                 * UI STARTS HERE *
                 ******************************************************************/

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(onClick = {
                        val wifiManager: WifiManager =
                            getSystemService<WifiManager>(WifiManager::class.java)
                        if (wifiManager.isWifiEnabled) {
                            Toast.makeText(this@WifiDirectDeviceSelectActivity, "Its On", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@WifiDirectDeviceSelectActivity, "Turn Wifi ON", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }) {
                        Text(text = "Turn Wifi On")
                    }
                    Button(onClick = {
                        startDiscovery()
                    }) {
                        Text(text = "Discover Peers")
                    }
                    Text(text = "read_msg_box")
                    Text(text = connectionStatusValue)

                    LazyColumn(
                        modifier = Modifier.defaultMinSize(minHeight = 100.dp)
                    ) {
                        //To be completed
                        items(peerListInternal) { peer ->
                            Text(
                                text = peer.deviceName, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (peer.status == WifiP2pDevice.CONNECTED && connectionInfoAvailable) {
                                            // here
                                            Log.d(TAG, "CASE 1")
                                        } else if (peer.status == WifiP2pDevice.CONNECTED) {
                                            // here
                                            Log.d(TAG, "CASE 2")
                                        } else if (peer.status == WifiP2pDevice.AVAILABLE) {
                                            Log.d(TAG, "CASE 3")
                                            val connectedDeviceName: String? = peer.deviceName
                                            if (connectedDeviceName != null) {
                                                var config: WifiP2pConfig = WifiP2pConfig()
                                                config.deviceAddress = peer.deviceAddress
                                                wifiP2pManager?.connect(
                                                    wifiP2pChannel,
                                                    config,
                                                    actionListener
                                                )
                                            } else {
                                                Log.d(TAG, "CASE 4")
                                                Toast
                                                    .makeText(
                                                        this@WifiDirectDeviceSelectActivity,
                                                        "You are already connected to another device. Disconnect to start another communication",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        } else if (peer.status == WifiP2pDevice.INVITED) {
                                            if (wifiP2pManager != null && wifiP2pChannel != null) {
                                                disconnectP2P()
                                            }
                                        }
                                    }, fontSize = 24.sp, textAlign = TextAlign.Center
                            )
                        }
                    }

                    var text by remember { mutableStateOf("Hello") }
                    TextField(
                        value = text,
                        onValueChange = { newText -> text = newText },
                        label = { Text("Enter text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Button(onClick = {
                        if (!text.trim { it <= ' ' }.isEmpty()) {
                            val i = Intent(applicationContext, CommunicationService::class.java)
                            i.action = communicationService.ACTION_SEND_MSG
                            i.putExtra(communicationService.EXTRAS_MSG_TYPE, 0)
                            i.putExtra(communicationService.EXTRAS_TEXT_CONTENT, text)
                            startService(i)
                        }
                    }) {
                        Text(text = "Send")
                    }
                }
            }
        }
    }
    private fun initializeWiFiDirect(): Boolean {
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        if (wifiP2pManager == null) {
            Log.e(TAG, "Cannot get Wi-Fi system service.")
            return false
        }
        wifiP2pChannel = wifiP2pManager!!.initialize(
            this, mainLooper
        ) { Log.d(TAG, "Wifi P2P Channel disconnected") }
        if (wifiP2pChannel == null) {
            Log.e(TAG, "Cannot initialize Wi-Fi Direct.")
            return false
        }
        wifiP2pManager!!.requestConnectionInfo(
            wifiP2pChannel
        ) { wifiP2pInfo ->
            if (wifiP2pInfo.groupOwnerAddress != null) {
                Log.d(TAG, "Info Available")
                connectionInfoAvailable = true
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (isWiFiDirectActive && !isDiscovering) {
            wifiP2pManager?.discoverPeers(wifiP2pChannel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeerList() {
        wifiP2pManager?.requestPeers(wifiP2pChannel, peerListListener)
    }

    fun newWiFiDirectConnection() {
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    fun disconnectP2P() {
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            wifiP2pManager!!.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "cancelConnect onSuccess -")
                }

                override fun onFailure(reason: Int) {
                    Log.d(TAG, "cancelConnect onFailure -$reason")
                }
            })
            wifiP2pManager!!.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "removeGroup onSuccess -")
                }

                override fun onFailure(reason: Int) {
                    Log.d(TAG, "removeGroup onFailure -$reason")
                }
            })
        }
        connectionInfoAvailable = false
        peers = emptyList()
    }

    fun setWiFiDirectActive(wiFiDirectActive: Boolean) {
        this.isWiFiDirectActive = wiFiDirectActive
        if (wiFiDirectActive) {
            startDiscovery()
            Log.d(TAG, "Its ON Its Working")
        } else {
            Log.d(TAG, "Wifi Direct is inactive")
            peers = emptyList()

        }
    }

    fun setWifiP2PdeviceName(wifiP2PdeviceName: String?) {
        this.wifiP2PdeviceName = wifiP2PdeviceName!!
    }

    fun setIsDiscovering(discovering: Boolean) {
        this.isDiscovering = discovering
    }

    fun setLocationState(locationEnabled: Boolean) {
        if (!locationEnabled) {
            disconnectP2P()
        }
    }

    fun isWiFiDirectActive(): Boolean {
        return isWiFiDirectActive
    }

    fun isDiscovering(): Boolean {
        return isDiscovering
    }

    override fun onResume() {
        super.onResume()
        if (receiver != null && intentFilter != null) registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        if (receiver != null && intentFilter != null) unregisterReceiver(receiver)
    }

}


fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}

