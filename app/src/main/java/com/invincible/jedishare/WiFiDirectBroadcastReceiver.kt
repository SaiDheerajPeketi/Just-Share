package com.invincible.jedishare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.core.location.LocationManagerCompat
import com.invincible.jedishare.presentation.WifiDirectViewModel

/**
 * Handles WiFi Direct system broadcasts and delegates to [WifiDirectViewModel].
 *
 * Fix (TODO-18): Previously called Activity methods directly (e.g. activity.setWiFiDirectActive()).
 * Now delegates to ViewModel methods to respect the MVVM boundary.
 */
class WiFiDirectBroadcastReceiver(
    private val activity: WifiDirectDeviceSelectActivity,
    private val viewModel: WifiDirectViewModel
) : BroadcastReceiver() {

    private val TAG = "WifiDirectReceiver"

    @Suppress("DEPRECATION") // EXTRA_NETWORK_INFO deprecated in API 29 but needed for P2P
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED
                )
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d(TAG, "Wi-Fi P2P state: ${if (enabled) "enabled" else "disabled"}")
                viewModel.onWifiDirectEnabled(enabled)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d(TAG, "Peer list changed")
                viewModel.onPeersChanged()
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                if (isInitialStickyBroadcast) return
                val networkInfo: NetworkInfo? =
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    Log.d(TAG, "Wi-Fi Direct connected")
                    viewModel.onConnectionChanged()
                } else {
                    Log.d(TAG, "Wi-Fi Direct disconnected")
                    viewModel.disconnectP2P()
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device: WifiP2pDevice? =
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                viewModel.onThisDeviceChanged(device?.deviceName)
            }

            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                val discovering = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                Log.d(TAG, "Discovery: ${if (discovering) "started" else "stopped"}")
                viewModel.onDiscoveryStateChanged(discovering)
            }

            LocationManager.PROVIDERS_CHANGED_ACTION -> {
                val locationManager =
                    context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val locationEnabled = locationManager?.let {
                    LocationManagerCompat.isLocationEnabled(it)
                } ?: true
                Log.d(TAG, "Location state: $locationEnabled")
                if (!locationEnabled) viewModel.onLocationDisabled()
            }
        }
    }
}
