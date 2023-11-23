package com.invincible.jedishare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WiFiDirectBroadcastReceiver(
    activity: MainActivity
): BroadcastReceiver() {
    private val activity: MainActivity = activity

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
            }
        }
    }

}
