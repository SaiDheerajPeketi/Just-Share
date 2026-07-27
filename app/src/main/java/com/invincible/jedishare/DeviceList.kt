package com.invincible.jedishare

import timber.log.Timber

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.components.ChatScreen
import com.invincible.jedishare.presentation.components.DeviceScreen
import com.invincible.jedishare.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for Bluetooth device selection and file transfer.
 *
 * Fix: Removed `viewModel.contentResolver = this.contentResolver` —
 * ContentResolver is now provided via Hilt DI from [AppModule].
 * DeviceList.kt no longer injects Android-specific objects into the ViewModel.
 */
@AndroidEntryPoint
class DeviceList : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("DeviceList - onCreate called")
        super.onCreate(savedInstanceState)

        // Log share-sheet URIs if launched via ACTION_SEND / ACTION_SEND_MULTIPLE
        when (intent.action) {
            android.content.Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)
                uri?.let {
                    Log.d("DeviceList", "Shared single file: ${getFileDetailsFromUri(it, contentResolver)}")
                }
            }
            android.content.Intent.ACTION_SEND_MULTIPLE -> {
                val uriList = intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)
                uriList?.forEach { uri ->
                    Log.d("DeviceList", "Shared file: ${getFileDetailsFromUri(uri, contentResolver)}")
                }
            }
        }

        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<BluetoothViewModel>()
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.errorMessage) {
                    state.errorMessage?.let { message ->
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(state.isConnected) {
                    if (state.isConnected) {
                        Toast.makeText(applicationContext, "You're connected!", Toast.LENGTH_LONG).show()
                    }
                }

                Surface(color = MaterialTheme.colors.background) {
                    when {
                        state.isConnecting -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedPreloader(
                                    modifier = Modifier.size(400.dp),
                                    R.raw.connecting_animation
                                )
                                AnimatedPreloader(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .offset(y = 150.dp),
                                    R.raw.connecting_text_animation,
                                    1
                                )
                            }
                        }

                        state.isConnected -> {
                            val list = intent.getParcelableArrayListExtra<Uri>("urilist")
                                ?: emptyList<Uri>()
                            ChatScreen(
                                state = state,
                                onDisconnect = viewModel::disconnectFromDevice,
                                onSendMessage = viewModel::sendMessage,
                                uriList = list,
                                viewModel = viewModel,
                                contentResolver = contentResolver
                            )
                        }

                        else -> {
                            val list = intent.getParcelableArrayListExtra<Uri>("urilist")
                                ?: emptyList<Uri>()
                            DeviceScreen(
                                state = state,
                                onStartScan = viewModel::startScan,
                                onStopScan = viewModel::stopScan,
                                onDeviceClick = viewModel::connectToDevice,
                                onStartServer = viewModel::waitForIncomingConnections
                            )
                            val isFromReceive = intent.getBooleanExtra("source", false)
                            LaunchedEffect(Unit) {
                                if (isFromReceive) {
                                    viewModel.waitForIncomingConnections()
                                } else {
                                    viewModel.startScan()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
