package com.invincible.jedishare

import timber.log.Timber

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.components.ChatScreen
import com.invincible.jedishare.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Progress screen: shown during/after file transfer for both Bluetooth and WiFi-Direct.
 *
 * Fix: Previously passed null for viewModel and contentResolver when isFromWifi=true,
 * which meant the progress bar never worked on the WiFi Direct path.
 * Now always passes the ViewModel and contentResolver so the UI works on both paths.
 */
@AndroidEntryPoint
class Progress : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("Progress - onCreate called")
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<BluetoothViewModel>()
                val transferMethod = intent.getStringExtra("transferMethod")
                val isFromWifi = transferMethod == "Wifi-Direct"
                val uriList = intent.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>()

                ChatScreen(
                    onDisconnect = { finish() },
                    onSendMessage = viewModel::sendMessage,
                    uriList = uriList,
                    viewModel = viewModel,
                    contentResolver = contentResolver,
                    isFromWifi = isFromWifi
                )
            }
        }
    }
}
