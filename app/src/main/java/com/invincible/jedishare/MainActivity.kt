package com.invincible.jedishare

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import com.invincible.jedishare.data.UserPreferencesDataStore
import com.invincible.jedishare.navigation.AppNavGraph
import com.invincible.jedishare.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    private val transferViewModel: com.invincible.jedishare.presentation.TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("MainActivity - onCreate called")
        super.onCreate(savedInstanceState)

        var startRoute = intent.getStringExtra("start_route") ?: com.invincible.jedishare.navigation.Screen.Splash.route
        var initialUris = intent.getParcelableArrayListExtra<Uri>("urilist")?.toList().orEmpty()
        var initialMethod = when (startRoute) {
            com.invincible.jedishare.navigation.Screen.DiscoverWifi.route -> "wifi"
            com.invincible.jedishare.navigation.Screen.DiscoverBT.route -> "bt"
            else -> null
        }

        val action = intent.action
        val isShareIntent = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        
        if (isShareIntent) {
            val dataStore = UserPreferencesDataStore(this)
            val method = runBlocking { dataStore.defaultTransferMethod.first() }
            initialMethod = method
            startRoute = if (method == "wifi") com.invincible.jedishare.navigation.Screen.DiscoverWifi.route else com.invincible.jedishare.navigation.Screen.DiscoverBT.route
            
            initialUris = when (action) {
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) listOf(uri) else emptyList()
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
                }
                else -> emptyList()
            }

            // Most share-sheet grants are temporary. Retain them when the
            // sending provider explicitly allows a persistable document grant.
            if ((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                initialUris.forEach { persistReadUriPermission(this, it) }
            }
        }

        if (initialUris.isNotEmpty()) {
            transferViewModel.setUris(initialUris)
        }
        initialMethod?.let { transferViewModel.setMethod(it) }

        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    AppNavGraph(
                        startDestination = startRoute,
                        transferViewModel = transferViewModel,
                        initialUris = initialUris,
                        initialMethod = initialMethod
                    )
                }
            }
        }
    }
}
