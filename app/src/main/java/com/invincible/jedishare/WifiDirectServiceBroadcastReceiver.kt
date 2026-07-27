package com.invincible.jedishare

import timber.log.Timber

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives transfer progress broadcasts from [CommunicationService] (WiFi Direct path).
 * Logs progress — the Activity's LaunchedEffect already observes connection state via ViewModel.
 */
class WiFiDirectServiceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("WiFiDirectServiceBroadcastReceiver - onReceive called")
        intent ?: return
        if (intent.action != CommunicationService.BROADCAST_SENDING_UPDATE) return

        val progress  = intent.getIntExtra(CommunicationService.EXTRAS_PROGRESS_STATE, 0)
        val fileName  = intent.getStringExtra(CommunicationService.EXTRAS_FILE_NAME) ?: ""
        val fileSize  = intent.getLongExtra(CommunicationService.EXTRAS_FILE_SIZE, 0L)
        Log.d("WifiServiceReceiver", "Progress: $progress% — $fileName ($fileSize bytes)")
    }
}