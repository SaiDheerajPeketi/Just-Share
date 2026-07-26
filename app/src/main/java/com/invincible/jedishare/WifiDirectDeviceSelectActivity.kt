package com.invincible.jedishare

import android.Manifest
import android.annotation.SuppressLint
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invincible.jedishare.presentation.WifiDirectViewModel
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.Roboto
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import android.provider.Settings

/**
 * Wi-Fi Direct device selection Activity.
 *
 * Fix (TODO-18): All WifiP2pManager logic extracted to [WifiDirectViewModel].
 * This Activity only handles:
 *  - Lifecycle-aware broadcast receiver registration/unregistration.
 *  - The Compose UI connected to the ViewModel's StateFlow.
 *  - Starting CommunicationService once connected.
 *
 * Fix (TODO-19): [PermissionViewModel] removed — its dialog queue is now part of
 * [WifiDirectViewModel.visiblePermissionDialogQueue].
 */
@AndroidEntryPoint
class WifiDirectDeviceSelectActivity : ComponentActivity() {

    private val TAG = "WifiDirectActivity"

    private var receiver: WiFiDirectBroadcastReceiver? = null
    private var connectionUpdateReceiver: WiFiDirectServiceBroadcastReceiver? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
    }

    private val permissionsToRequest: Array<String> = buildList {
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CHANGE_NETWORK_STATE)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.FOREGROUND_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileUriList: List<Uri> =
            intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList()
        val isFromReceiver = intent.getStringExtra("source2") == "1"

        val wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        val wifiP2pChannel = wifiP2pManager?.initialize(this, mainLooper) {
            Log.d(TAG, "Wi-Fi P2P channel disconnected")
        }

        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<WifiDirectViewModel>()

                // Wire P2P manager into ViewModel once
                LaunchedEffect(Unit) {
                    if (wifiP2pManager != null && wifiP2pChannel != null) {
                        viewModel.initialize(wifiP2pManager, wifiP2pChannel)
                    }
                    // Set up broadcast receiver (needs ViewModel reference)
                    receiver = WiFiDirectBroadcastReceiver(this@WifiDirectDeviceSelectActivity, viewModel)
                    connectionUpdateReceiver =
                        WiFiDirectServiceBroadcastReceiver(this@WifiDirectDeviceSelectActivity)
                }

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val dialogQueue = viewModel.visiblePermissionDialogQueue

                // Navigate to Progress once connected
                var alreadyNavigated by remember { mutableStateOf(false) }
                LaunchedEffect(uiState.isConnected) {
                    if (uiState.isConnected && !alreadyNavigated && !isFromReceiver) {
                        alreadyNavigated = true
                        // Send files via CommunicationService
                        val serviceIntent = Intent(
                            applicationContext, CommunicationService::class.java
                        ).apply {
                            action = CommunicationService.ACTION_SEND_MSG
                            putParcelableArrayListExtra("urilist", ArrayList(fileUriList))
                        }
                        startService(serviceIntent)

                        // Open progress screen
                        startActivity(
                            Intent(
                                this@WifiDirectDeviceSelectActivity, Progress::class.java
                            ).apply {
                                putExtra("transferMethod", "Wifi-Direct")
                                putParcelableArrayListExtra("urilist", ArrayList(fileUriList))
                            }
                        )
                    }
                }

                // Permission launcher
                val permLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        permissionsToRequest.forEach { perm ->
                            viewModel.onPermissionResult(perm, perms[perm] == true)
                        }
                    }
                )
                SideEffect { permLauncher.launch(permissionsToRequest) }

                // Permission dialogs
                dialogQueue.reversed().forEach { permission ->
                    PermissionDialog(
                        permissionTextProvider = when (permission) {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION -> LocationPermissionTextProvider()
                            Manifest.permission.NEARBY_WIFI_DEVICES,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.CHANGE_WIFI_STATE -> WifiPermissionTextProvider()
                            else -> InternetPermissionTextProvider()
                        },
                        isPermanentlyDeclined = !shouldShowRequestPermissionRationale(permission),
                        onDismiss = viewModel::dismissPermissionDialog,
                        onOkClick = {
                            viewModel.dismissPermissionDialog()
                            permLauncher.launch(arrayOf(permission))
                        },
                        onGoToAppSettingsClick = ::openAppSettings
                    )
                }

                // ── UI ─────────────────────────────────────────────────────────
                if (isFromReceiver) {
                    // Receiver mode: show connecting animation
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedPreloader(
                            modifier = Modifier.size(400.dp),
                            drawable = R.raw.connecting_animation
                        )
                        AnimatedPreloader(
                            modifier = Modifier
                                .size(300.dp)
                                .offset(y = 150.dp),
                            drawable = R.raw.connecting_text_animation,
                            iterations = 1
                        )
                    }
                } else {
                    // Sender mode: show peer list
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Device Select",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                fontFamily = Roboto
                            )
                            Divider(
                                color = Color.LightGray,
                                thickness = 2.dp,
                                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MyRedSecondaryLight)
                                    .fillMaxWidth()
                                    .heightIn(max = 700.dp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                            ) {
                                if (uiState.peers.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (uiState.isWifiDirectEnabled)
                                                "Searching for peers…"
                                            else
                                                "Enable Wi-Fi to discover nearby devices",
                                            color = Color.Gray,
                                            fontFamily = Roboto,
                                            fontSize = 16.sp
                                        )
                                    }
                                } else {
                                    Column {
                                        Text(
                                            text = "Peers",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            fontFamily = Roboto,
                                            modifier = Modifier.padding(16.dp),
                                            textDecoration = TextDecoration.Underline
                                        )
                                        LazyColumn(
                                            modifier = Modifier
                                                .heightIn(max = 600.dp)
                                                .padding(horizontal = 8.dp),
                                            verticalArrangement = Arrangement.Top
                                        ) {
                                            items(uiState.peers, key = { it.deviceAddress }) { peer ->
                                                PeerItem(
                                                    device = peer,
                                                    onConnect = {
                                                        when (peer.status) {
                                                            WifiP2pDevice.AVAILABLE -> viewModel.connectToDevice(peer)
                                                            WifiP2pDevice.INVITED   -> viewModel.disconnectP2P()
                                                            WifiP2pDevice.CONNECTED -> viewModel.requestConnectionInfo()
                                                            else -> Toast.makeText(
                                                                this@WifiDirectDeviceSelectActivity,
                                                                "Already connected to another device",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        receiver?.let { registerReceiver(it, intentFilter) }
        connectionUpdateReceiver?.let {
            registerReceiver(it, IntentFilter(CommunicationService.BROADCAST_SENDING_UPDATE))
        }
    }

    override fun onPause() {
        super.onPause()
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        try { connectionUpdateReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    }
}

@Composable
private fun PeerItem(device: WifiP2pDevice, onConnect: () -> Unit) {
    val statusText = when (device.status) {
        WifiP2pDevice.AVAILABLE  -> "Available"
        WifiP2pDevice.INVITED    -> "Invited"
        WifiP2pDevice.CONNECTED  -> "Connected"
        WifiP2pDevice.FAILED     -> "Failed"
        WifiP2pDevice.UNAVAILABLE -> "Unavailable"
        else                     -> "Unknown"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.deviceName,
                fontSize = 18.sp,
                fontFamily = Roboto,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontFamily = Roboto,
                color = when (device.status) {
                    WifiP2pDevice.AVAILABLE -> Color(0xFF33A850)
                    WifiP2pDevice.CONNECTED -> Color(0xFF4187E6)
                    else                   -> Color.Gray
                }
            )
        }
    }
}

fun android.app.Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}
