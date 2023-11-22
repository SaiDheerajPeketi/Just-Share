package com.invincible.jedishare

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
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
        setContent {
            JediShareTheme {
                val permissionViewModel = viewModel<PermissionViewModel>()
                val dialogQueue = permissionViewModel.visiblePermissionDialogQueue

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