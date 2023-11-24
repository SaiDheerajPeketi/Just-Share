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

class WiFiDirectBroadcastReceiver(
    private val activity: WifiDirectDeviceSelectActivity
): BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent != null){
            when(intent.action){
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.d(activity.TAG, "Wi-Fi P2P enabled")
                        activity.setWiFiDirectActive(true)
                    } else {
                        Log.d(activity.TAG, "Wi-Fi P2P disabled")
                        activity.setWiFiDirectActive(false)
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(activity.TAG, "Peer list changed")
                    activity.requestPeerList()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if(isInitialStickyBroadcast){
                        return
                    }
                    var networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if(networkInfo?.isConnected == true){
                        Log.d(activity.TAG, "Wi-fi direct new connection")
                        activity.newWiFiDirectConnection()
                    }
                    else{
                        Log.d(activity.TAG, "Wi-fi direct disconnected")
                        activity.disconnectP2P()
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    var device: WifiP2pDevice? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    if (device != null) {
                        activity.setWifiP2PdeviceName(device.deviceName)
                    }
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 10000)
                    if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                        Log.d(activity.TAG, "Wi-Fi P2P discovery started")
                        activity.setIsDiscovering(true)
                    } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                        Log.d(activity.TAG, "Wi-Fi P2P discovery stopped")
                        activity.setIsDiscovering(false)
                    } else {
                        Log.d(activity.TAG, "onReceive: $state")
                    }
                }
                LocationManager.PROVIDERS_CHANGED_ACTION -> {

                    Log.d(activity.TAG, "Location state change")
                    val locationManager =
                        context!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    activity.setLocationState(
                        LocationManagerCompat.isLocationEnabled(
                            locationManager
                        )
                    )
                }
            }
        }
    }

}
