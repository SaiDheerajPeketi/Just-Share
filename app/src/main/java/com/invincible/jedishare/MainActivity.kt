package com.invincible.jedishare

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import com.invincible.jedishare.navigation.AppNavGraph
import com.invincible.jedishare.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            Log.e("DEBUG", "${it.key} = ${it.value}")
        }
    }

    private val permissionsToRequest: Array<String> = buildList {
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CHANGE_NETWORK_STATE)
        add(Manifest.permission.INTERNET)
        add(Manifest.permission.FOREGROUND_SERVICE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("MainActivity - onCreate called")
        super.onCreate(savedInstanceState)

        // Request all required runtime permissions in one shot
        requestMultiplePermissions.launch(permissionsToRequest)

        val startRoute = intent.getStringExtra("start_route") ?: com.invincible.jedishare.navigation.Screen.Splash.route

        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    AppNavGraph(startDestination = startRoute)
                }
            }
        }
    }
}