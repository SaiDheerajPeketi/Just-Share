package com.invincible.jedishare.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

import androidx.compose.material.icons.filled.ArrowUpward

@Composable
fun RadarAnim(scanning: Boolean) {
    val colors = JediShareTheme.colors
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val activeScale = if (scanning) pulseScale else 1f

    Box(
        modifier = Modifier
            .size(120.dp)
            .graphicsLayer { scaleX = activeScale; scaleY = activeScale }
            .background(colors.red.copy(alpha = 0.05f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(colors.red.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(colors.red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = colors.white, modifier = Modifier.size(24.dp))
            }
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
        val scannedAddresses = btState.scannedDevices.map { it.address }.toSet()
        val allBtDevices = (btState.pairedDevices + btState.scannedDevices).distinctBy { it.address }
        allBtDevices.map { 
            UnifiedDevice(
                id = it.address,
                name = it.name ?: "Unknown Device",
                isWifiDirect = false,
                isPaired = it.address in pairedAddresses,
                isAvailable = it.address in scannedAddresses,
                btDevice = it
            )
        }.sortedByDescending { it.isAvailable }
    } else {
        wifiState.peers.map {
            UnifiedDevice(it.deviceAddress, it.deviceName ?: "Unknown Device", true, wifiDevice = it)
        }
    }

    LaunchedEffect(Unit) {
        if (transferMethod == "wifi") {
            wifiViewModel.setTransferRole(isSender)
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
            wifiViewModel.setTransferRole(isSender)
            // Note: startDiscovery() and startHosting() are automatically called 
            // by WifiDirectBroadcastReceiver once it confirms Wi-Fi is enabled.
        }
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            if (transferMethod == "bt") {
                repeat(if (isSender) 3 else 1) {
                    if (isSender) btViewModel.startScan()
                    delay(10000)
                }
                scanning = false
                if (isSender) btViewModel.stopScan()
            } else if (transferMethod == "wifi") {
                wifiViewModel.discoverPeers()
            }
        }
    }
    
    LaunchedEffect(wifiState.isDiscovering) {
        if (transferMethod == "wifi" && !wifiState.isDiscovering) {
            scanning = false
        }
    }
    
    val actualScanning = if (transferMethod == "wifi") wifiState.isDiscovering else scanning
    
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
            val uris = transferState.urisToShare
            if (uris.isNotEmpty() && isSender) {
                val intent = Intent(context, com.invincible.jedishare.CommunicationService::class.java).apply {
                    action = com.invincible.jedishare.CommunicationService.ACTION_SEND_MSG
                    putParcelableArrayListExtra("urilist", java.util.ArrayList(uris))
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
            onNavigateToScreen("transfer-progress")
        }
    }

    val displayTitle = if (isSender) "Send files" else "Receive files"

    com.invincible.jedishare.ui.components.RequireHardware(
        requireWifi = transferMethod == "wifi",
        requireBluetooth = transferMethod == "bt"
    ) {
        Column(
            modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(
            title = displayTitle,
            onBack = onBack,
            rightEl = null
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            val errorMessage = if (transferMethod == "bt") btState.errorMessage else wifiState.errorMessage
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(colors.red.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(text = errorMessage, color = colors.red, style = MaterialTheme.typography.body2)
                }
            }

            if (isSender) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RadarAnim(scanning = actualScanning)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (actualScanning) "Scanning for nearby devices…" else "Scan complete",
                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                        color = if (actualScanning) colors.red else colors.mutedFg
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
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                        .verticalScroll(rememberScrollState())
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
                                        if (!device.isAvailable) {
                                            android.widget.Toast.makeText(context, "Cannot connect: device is offline or not accepting connections.", android.widget.Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
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
                                    modifier = Modifier.size(40.dp).background(colors.red.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = device.name, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                                    Text(text = device.id, style = MaterialTheme.typography.caption, color = colors.mutedFg)
                                }
                                StatusDot(status = if (device.isAvailable) "online" else "offline")
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (scanning) {
                    com.invincible.jedishare.ui.components.PillButton(
                        label = "Stop Scan",
                        onClick = { 
                            scanning = false 
                            if (transferMethod == "bt") {
                                btViewModel.stopScan()
                            } else {
                                wifiViewModel.stopDiscovery()
                            }
                        },
                        variant = com.invincible.jedishare.ui.components.PillButtonVariant.OUTLINE,
                        size = com.invincible.jedishare.ui.components.PillButtonSize.LG,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )
                } else {
                    com.invincible.jedishare.ui.components.PillButton(
                        label = "Restart Scan",
                        onClick = { scanning = true },
                        size = com.invincible.jedishare.ui.components.PillButtonSize.LG,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )
                }
            } else {
                // Receiver UI
                // Receiver UI
                var btDiscoverable by remember { mutableStateOf(false) }
                var btTimeLeft by remember { mutableStateOf(0) }
                
                val isDiscoverable = if (transferMethod == "bt") {
                    btDiscoverable
                } else {
                    wifiState.connectionStatus == "hosting" || wifiState.isConnected
                }
                val timeLeft = if (transferMethod == "bt") btTimeLeft else -1 // -1 means infinite/managed by system for wifi
                
                if (transferMethod == "bt") {
                    DisposableEffect(Unit) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent?.action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                                    val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                                    if (mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                        btDiscoverable = true
                                        btTimeLeft = 300
                                    } else {
                                        btDiscoverable = false
                                        btTimeLeft = 0
                                    }
                                }
                            }
                        }
                        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
                        onDispose {
                            context.unregisterReceiver(receiver)
                        }
                    }
                    
                    LaunchedEffect(btDiscoverable) {
                        while (btDiscoverable && btTimeLeft > 0) {
                            delay(1000)
                            btTimeLeft--
                            if (btTimeLeft == 0) {
                                btDiscoverable = false
                            }
                        }
                    }
                }
                
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                val activeScale = if (isDiscoverable) pulseScale else 1f
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .graphicsLayer { scaleX = activeScale; scaleY = activeScale }
                            .background(colors.red.copy(alpha = 0.05f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(colors.red.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .background(colors.red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = colors.white, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = if (isDiscoverable) "Waiting for sender..." else "Visibility Stopped", 
                        style = MaterialTheme.typography.h1.copy(fontSize = 24.sp), 
                        color = colors.black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isDiscoverable) "This device is now visible to nearby ${if (transferMethod == "bt") "Bluetooth" else "Wi-Fi"} devices" else "Start visibility so other devices can find you",
                        style = MaterialTheme.typography.body2,
                        color = colors.mutedFg,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).background(colors.red.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = colors.red)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("VISIBLE AS", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = colors.mutedFg)
                                val deviceName = if (transferMethod == "wifi") wifiState.thisDeviceName.takeIf { it.isNotBlank() } else null
                                Text(deviceName ?: android.os.Build.MODEL ?: "This Device", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = colors.black)
                            }
                            val badgeBg = if (isDiscoverable) colors.green.copy(alpha = 0.1f) else colors.mutedFg.copy(alpha = 0.1f)
                            val badgeFg = if (isDiscoverable) colors.green else colors.mutedFg
                            Box(
                                modifier = Modifier.background(badgeBg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(badgeFg, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isDiscoverable) "Live" else "Hidden", color = badgeFg, style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Visibility", style = MaterialTheme.typography.caption, color = colors.mutedFg)
                                if (transferMethod == "bt") {
                                    val mins = timeLeft / 60
                                    val secs = timeLeft % 60
                                    Text(
                                        String.format("%02d:%02d", mins, secs),
                                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                                        color = if (timeLeft > 0) colors.red else colors.mutedFg
                                    )
                                } else {
                                    Text(
                                        if (isDiscoverable) "Active" else "Inactive",
                                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDiscoverable) colors.red else colors.mutedFg
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    if (!isDiscoverable || timeLeft == 0) {
                        com.invincible.jedishare.ui.components.PillButton(
                            label = "Restart Visibility",
                            onClick = { 
                                if (transferMethod == "bt") {
                                    btViewModel.requestDiscoverable()
                                } else {
                                    wifiViewModel.startHosting()
                                }
                            },
                            size = com.invincible.jedishare.ui.components.PillButtonSize.LG,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        com.invincible.jedishare.ui.components.PillButton(
                            label = "Stop Advertising",
                            onClick = { 
                                if (transferMethod == "bt") {
                                    btDiscoverable = false
                                    btTimeLeft = 0
                                } else {
                                    wifiViewModel.disconnectP2P()
                                }
                            },
                            variant = com.invincible.jedishare.ui.components.PillButtonVariant.OUTLINE,
                            size = com.invincible.jedishare.ui.components.PillButtonSize.LG,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
}
