package com.blackandblue.justshare

import android.Manifest
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class LocalTransferMethod(val label: String) {
    BLUETOOTH("Bluetooth"), WIFI("Wi-Fi Direct")
}

/** The permissions used by the selected local transport, not by the other one. */
fun localTransferPermissions(method: LocalTransferMethod, sdkInt: Int): List<String> {
    val nearby = when (method) {
        LocalTransferMethod.BLUETOOTH -> if (sdkInt >= 31) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        LocalTransferMethod.WIFI -> if (sdkInt >= 33) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    // Received files use shared storage on Android 9 and earlier. The document
    // picker itself does not require broad file or media-library access.
    return if (sdkInt <= 28) nearby + Manifest.permission.WRITE_EXTERNAL_STORAGE else nearby
}

fun hasLocalTransferPermissions(context: Context, method: LocalTransferMethod): Boolean =
    localTransferPermissions(method, Build.VERSION.SDK_INT).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Android 12+ requires the approximate/precise location choices in the same
 * request. Fine location remains the actual discovery requirement. */
fun localPermissionRequest(permissions: List<String>): List<String> =
    if (Manifest.permission.ACCESS_FINE_LOCATION in permissions) {
        (permissions + Manifest.permission.ACCESS_COARSE_LOCATION).distinct()
    } else permissions.distinct()
