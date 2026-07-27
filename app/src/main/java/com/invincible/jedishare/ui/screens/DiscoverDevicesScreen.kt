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

    val displayTitle = if (isSender) "Send files" else "Receive files"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(
            title = displayTitle,
            onBack = onBack,
            rightEl = if (isSender) {
                {
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
            } else null
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            if (isSender) {
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
            } else {
                // Receiver UI
                var isDiscoverable by remember { mutableStateOf(false) }
                var timeLeft by remember { mutableStateOf(0) }
                
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {
                                val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                                if (mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                    isDiscoverable = true
                                    timeLeft = 300
                                } else {
                                    isDiscoverable = false
                                    timeLeft = 0
                                }
                            }
                        }
                    }
                    context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
                    onDispose {
                        context.unregisterReceiver(receiver)
                    }
                }
                
                LaunchedEffect(isDiscoverable) {
                    while (isDiscoverable && timeLeft > 0) {
                        delay(1000)
                        timeLeft--
                        if (timeLeft == 0) {
                            isDiscoverable = false
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
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Waiting for sender...", style = MaterialTheme.typography.h1.copy(fontSize = 24.sp), color = colors.black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This device is now visible to nearby Bluetooth devices",
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
                                modifier = Modifier.size(48.dp).background(colors.lightRed, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, tint = colors.red)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("VISIBLE AS", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = colors.mutedFg)
                                Text(android.os.Build.MODEL ?: "This Device", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = colors.black)
                            }
                            Box(
                                modifier = Modifier.background(colors.green.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(colors.green, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live", color = colors.green, style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
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
                                val mins = timeLeft / 60
                                val secs = timeLeft % 60
                                Text(
                                    String.format("%02d:%02d", mins, secs),
                                    style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                                    color = if (timeLeft > 0) colors.red else colors.mutedFg
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    if (!isDiscoverable || timeLeft == 0) {
                        com.invincible.jedishare.ui.components.PillButton(
                            label = "Restart Visibility",
                            onClick = { 
                                btViewModel.requestDiscoverable()
                            },
                            size = com.invincible.jedishare.ui.components.PillButtonSize.LG,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        com.invincible.jedishare.ui.components.PillButton(
                            label = "Stop Advertising",
                            onClick = { 
                                isDiscoverable = false
                                timeLeft = 0
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
