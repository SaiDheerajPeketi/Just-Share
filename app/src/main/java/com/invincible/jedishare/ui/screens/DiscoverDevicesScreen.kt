package com.invincible.jedishare.ui.screens

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.TransferViewModel
import com.invincible.jedishare.presentation.UnifiedDevice
import com.invincible.jedishare.presentation.WifiDirectViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.StatusDot
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

@Composable
fun RadarAnim(scanning: Boolean) {
    val colors = JediShareTheme.colors
    val infiniteTransition = rememberInfiniteTransition()
    
    val size1 by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        if (scanning) {
            Box(
                modifier = Modifier
                    .size(size1.dp)
                    .alpha(alpha1)
                    .border(2.dp, colors.red, CircleShape)
            )
        }
        
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun DiscoverDevicesScreen(
    title: String,
    transferMethod: String,
    transferViewModel: TransferViewModel,
    btViewModel: BluetoothViewModel = hiltViewModel(),
    wifiViewModel: WifiDirectViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(true) }
    
    // Collect states based on method
    val btState by btViewModel.state.collectAsState()
    val wifiState by wifiViewModel.uiState.collectAsState()
    val transferState by transferViewModel.state.collectAsState()
    val isSender = transferState.urisToShare.isNotEmpty()

    // Unify discovered devices
    val discovered = if (transferMethod == "bt") {
        val pairedAddresses = btState.pairedDevices.map { it.address }.toSet()
        val allBtDevices = (btState.pairedDevices + btState.scannedDevices).distinctBy { it.address }
        allBtDevices.map { 
            UnifiedDevice(
                id = it.address,
                name = it.name ?: "Unknown Device",
                isWifiDirect = false,
                isPaired = it.address in pairedAddresses,
                btDevice = it
            )
        }
    } else {
        wifiState.peers.map {
            UnifiedDevice(it.deviceAddress, it.deviceName ?: "Unknown Device", true, wifiDevice = it)
        }
    }

    LaunchedEffect(Unit) {
        if (transferMethod == "wifi") {
            val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            if (manager != null) {
                val channel = manager.initialize(context, context.mainLooper, null)
                wifiViewModel.initialize(manager, channel)
            }
        }
        
        // Start appropriate service based on Sender/Receiver
        if (transferMethod == "bt") {
            if (isSender) {
                btViewModel.setUriList(transferState.urisToShare)
                btViewModel.startScan()
            } else {
                btViewModel.stopScan()
                btViewModel.requestDiscoverable()
                btViewModel.waitForIncomingConnections()
            }
        } else if (transferMethod == "wifi") {
            wifiViewModel.startDiscovery()
        }
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            repeat(if (transferMethod == "bt" && isSender) 3 else 1) {
                if (transferMethod == "bt" && isSender) btViewModel.startScan()
                else if (transferMethod == "wifi") wifiViewModel.startDiscovery()
                delay(10000)
            }
            scanning = false
            if (transferMethod == "bt" && isSender) btViewModel.stopScan()
        }
    }
    
    // Auto navigation when connected
    LaunchedEffect(btState.isConnected, wifiState.isConnected, transferState.hasTransferStarted) {
        if (btState.isConnected && transferMethod == "bt" && !transferState.hasTransferStarted) {
            val uris = transferState.urisToShare
            transferViewModel.markTransferStarted()
            if (uris.isNotEmpty()) {
                btViewModel.sendMessage("start") // triggers file sending
            }
            onNavigateToScreen("transfer-progress")
        } else if (wifiState.isConnected && transferMethod == "wifi" && !transferState.hasTransferStarted) {
            transferViewModel.markTransferStarted()
            // Wifi triggers sending via CommunicationService, usually done in connectionInfoListener
            onNavigateToScreen("transfer-progress")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(
            title = title,
            onBack = onBack,
            rightEl = {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { scanning = true }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (scanning) colors.red else colors.mutedFg,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadarAnim(scanning = scanning)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (scanning) "Scanning for nearby devices…" else "Scan complete",
                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                    color = if (scanning) colors.red else colors.mutedFg
                )
            }

            Text(
                text = "AVAILABLE DEVICES (${discovered.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedFg,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                if (discovered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No devices found", color = colors.mutedFg)
                    }
                } else {
                    discovered.forEachIndexed { index, device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    transferViewModel.setConnectedDeviceName(device.name)
                                    if (transferMethod == "bt") {
                                        device.btDevice?.let {
                                            if (device.isPaired) {
                                                btViewModel.connectToDevice(it)
                                            } else {
                                                btViewModel.pairDevice(it)
                                            }
                                        }
                                    } else {
                                        device.wifiDevice?.let { wifiViewModel.connectToDevice(it) }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(colors.lightRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = device.name, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                                Text(text = device.id, style = MaterialTheme.typography.caption, color = colors.mutedFg)
                            }
                            StatusDot(status = "online")
                            if (transferMethod == "bt" && !device.isPaired) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.BluetoothSearching, contentDescription = "Pair", tint = colors.red, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (index < discovered.lastIndex) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                        }
                    }
                }
            }
        }
    }
}
