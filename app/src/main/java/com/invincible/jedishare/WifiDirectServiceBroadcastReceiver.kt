package com.invincible.jedishare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WiFiDirectServiceBroadcastReceiver(
    private val activity: WifiDirectDeviceSelectActivity
): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            when(intent.action){
                activity.SENDING_UPDATE -> {
                    val progress = intent.getIntExtra("com.invincible.jedishare.EXTRAS_PROGRESS_STATE", 100)
                    val fileName = intent.getStringExtra("com.invincible.jedishare.EXTRAS_FILE_NAME")
                    val filesize = intent.getStringExtra("com.invincible.jedishare.EXTRAS_FILE_SIZE")
                    activity.connectionText = progress.toString()
                    Log.d(activity.TAG, "onReceive: $progress")
                }
            }
        }
    }
}