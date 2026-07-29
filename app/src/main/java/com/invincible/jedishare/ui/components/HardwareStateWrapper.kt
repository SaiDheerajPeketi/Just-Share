package com.invincible.jedishare.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun RequireHardware(
    requireWifi: Boolean = true,
    isWifiEnabledOverride: Boolean? = null,
    requireBluetooth: Boolean = true,
    isBluetoothEnabledOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    
    var isStandardWifiEnabled by remember { mutableStateOf(wifiManager?.isWifiEnabled == true) }
    var isP2pWifiEnabled by remember { mutableStateOf(false) }
    var isBluetoothEnabledState by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    val isWifiEnabled = isWifiEnabledOverride ?: (isStandardWifiEnabled || isP2pWifiEnabled)
    val isBluetoothEnabled = isBluetoothEnabledOverride ?: isBluetoothEnabledState

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        isStandardWifiEnabled = state == WifiManager.WIFI_STATE_ENABLED
                    }
                    android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        isP2pWifiEnabled = state == android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        isBluetoothEnabledState = state == BluetoothAdapter.STATE_ON
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (requireWifi && !isWifiEnabled) {
            HardwareDialog(
                title = "Wi-Fi is Off",
                message = "Please turn on Wi-Fi from your quick settings to use high-speed transfers.",
                icon = Icons.Default.Wifi
            )
        } else if (requireBluetooth && !isBluetoothEnabled) {
            HardwareDialog(
                title = "Bluetooth is Off",
                message = "Please turn on Bluetooth from your quick settings to discover and connect to devices.",
                icon = Icons.Default.Bluetooth
            )
        }
    }
}

@Composable
private fun HardwareDialog(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colors = JediShareTheme.colors
    Dialog(
        onDismissRequest = { /* Cannot dismiss manually */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, shape = MaterialTheme.shapes.medium)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.red,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                color = colors.black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                color = colors.mutedFg,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
