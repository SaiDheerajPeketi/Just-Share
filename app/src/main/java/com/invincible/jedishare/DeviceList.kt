package com.invincible.jedishare

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.components.ChatScreen
import com.invincible.jedishare.presentation.components.DeviceScreen
import com.invincible.jedishare.ui.theme.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.lang.Thread.sleep

@AndroidEntryPoint
class DeviceList : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        if(intent.action == Intent.ACTION_SEND){
            val uri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            if(uri != null){
                Log.e("HELLOME",uri?.toString())
                var fileInfo = getFileDetailsFromUri(uri, contentResolver)
                Log.e("HELLOME",fileInfo.toString())
                Toast.makeText(this, fileInfo.fileName,Toast.LENGTH_SHORT).show()
            }
        }
        else if(intent.action == Intent.ACTION_SEND_MULTIPLE){
            val uriList: ArrayList<Uri>? = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)

            if (uriList != null && uriList.isNotEmpty()) {
                uriList.forEach { it ->
                    Log.e("HELLOME", it.toString())
                    var fileInfo = getFileDetailsFromUri(it, contentResolver)
                    Log.e("HELLOME",fileInfo.toString())
                    Toast.makeText(this, fileInfo.fileName,Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<BluetoothViewModel>()
                viewModel.contentResolver = this@DeviceList.contentResolver
                val state by viewModel.state.collectAsState()

                LaunchedEffect(key1 = state.errorMessage) {
                    state.errorMessage?.let { message ->
                        Toast.makeText(
                            applicationContext,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(key1 = state.isConnected) {
                    if(state.isConnected) {
                        Toast.makeText(
                            applicationContext,
                            "You're connected!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Surface(
                    color = MaterialTheme.colors.background
                ) {
                    when {
                        state.isConnecting -> {
//                            val list = intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>()
//                            ChatScreen(
//                                state = state,
//                                onDisconnect = viewModel::disconnectFromDevice,
//                                onSendMessage = viewModel::sendMessage,
//                                list,
//                                viewModel
//                            )
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedPreloader(modifier = Modifier.size(400.dp), R.raw.connecting_animation)
                                AnimatedPreloader(modifier = Modifier.size(300.dp).offset(y = 150.dp), R.raw.connecting_text_animation, 1)
                            }
                        }
                        state.isConnected -> {
                            val list = intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>()
                            ChatScreen(
                                state = state,
                                onDisconnect = viewModel::disconnectFromDevice,
                                onSendMessage = viewModel::sendMessage,
                                list,
                                viewModel,
                                intent,
                                contentResolver
                            )
                        }
                        else -> {
                            val list = intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>()

//                            ChatScreen(
//                                state = state,
//                                onDisconnect = viewModel::disconnectFromDevice,
//                                onSendMessage = viewModel::sendMessage,
//                                list,
//                                viewModel,
//                                intent,
//                                contentResolver
//                            )
                            DeviceScreen(
                                state = state,
                                onStartScan = viewModel::startScan,
                                onStopScan = viewModel::stopScan,
                                onDeviceClick = viewModel::connectToDevice,
                                onStartServer = viewModel::waitForIncomingConnections
                            )
                            val isFromReceive = intent.getBooleanExtra("source", false)
                            if(isFromReceive){
                                viewModel.waitForIncomingConnections()
                            }else{

                                viewModel.startScan()
                            }
                        }
                    }
                }
            }
        }
    }
}