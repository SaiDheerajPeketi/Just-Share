package com.invincible.jedishare.data.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import com.invincible.jedishare.WifiDirectDeviceSelectActivity

class WiFiDirectBroadcastReceiver(
    activity: WifiDirectDeviceSelectActivity
): BroadcastReceiver() {
    private val activity: WifiDirectDeviceSelectActivity = activity

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {

                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {

                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {

                }

                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {

                }

                LocationManager.PROVIDERS_CHANGED_ACTION -> {

                }
            }
        }
    }
}