package com.invincible.jedishare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invincible.jedishare.presentation.PermissionViewModel
import com.invincible.jedishare.presentation.components.InternetPermissionTextProvider
import com.invincible.jedishare.presentation.components.LocationPermissionTextProvider
import com.invincible.jedishare.presentation.components.PermissionDialog
import com.invincible.jedishare.presentation.components.WifiPermissionTextProvider
import com.invincible.jedishare.presentation.ui.theme.JediShareTheme

class WifiDirectDeviceSelectActivity : ComponentActivity() {
    private val permissionsToRequest = if(Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }
    else if(Build.VERSION.SDK_INT >= 28){
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }
    else{
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFromReceive = intent.getBooleanExtra("source", false)


        setContent {
            JediShareTheme {
                val permissionViewModel = viewModel<PermissionViewModel>()
                val dialogQueue = permissionViewModel.visiblePermissionDialogQueue
                var peerListInternal by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
                var connectionStatusValue by remember { mutableStateOf("Connection Status: ") }
                var text by remember { mutableStateOf("Hello") }

                val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        permissionsToRequest.forEach { permission ->
                            permissionViewModel.onPermissionResult(
                                permission = permission,
                                isGranted = perms[permission] == true
                            )
                        }
                    }
                )

                SideEffect {
                    multiplePermissionResultLauncher.launch(permissionsToRequest)
                }

                dialogQueue
                    .reversed()
                    .forEach { permission ->
                        PermissionDialog(
                            permissionTextProvider = when (permission) {
                                Manifest.permission.INTERNET -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_COARSE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_FINE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                else -> return@forEach
                            },
                            isPermanentlyDeclined = !shouldShowRequestPermissionRationale(
                                permission
                            ),
                            onDismiss = permissionViewModel::dismissDialog,
                            onOkClick = {
                                permissionViewModel.dismissDialog()
                                multiplePermissionResultLauncher.launch(
                                    arrayOf(permission)
                                )
                            },
                            onGoToAppSettingsClick = ::openAppSettings
                        )
                    }

                /*****************************************************************
                                        * UI STARTS HERE *
                 ******************************************************************/

                Column (
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally){

                    LazyColumn(modifier = Modifier.defaultMinSize(minHeight = 100.dp)
                    ){
                        item {
                            Text(
                                text = "Scanned Devices",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        //To be completed
                        items(peerListInternal){ peer ->
                            Text(text = peer.deviceName, modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                }, fontSize = 24.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Text(text = connectionStatusValue,  fontSize = 24.sp, textAlign = TextAlign.Center)

                    TextField(
                        value = text,
                        onValueChange = { newText -> text = newText },
                        label = { Text("Enter text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Button(onClick = {

                        }) {
                            Text(text = "Discover Peers")
                        }

                        Button(onClick = {

                        }) {
                            Text(text = "Send")
                        }
                    }
                    }
                }
            }
        }
}


fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}