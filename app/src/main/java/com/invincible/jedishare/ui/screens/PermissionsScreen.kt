package com.invincible.jedishare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current

    val storagePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    val btPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val wifiPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val notifPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    fun checkPerms(perms: List<String>) = perms.all { 
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
    }

    var storageGranted by remember { mutableStateOf(checkPerms(storagePerms)) }
    var btGranted by remember { mutableStateOf(checkPerms(btPerms)) }
    var wifiGranted by remember { mutableStateOf(checkPerms(wifiPerms)) }
    var notifGranted by remember { mutableStateOf(checkPerms(notifPerms)) }

    val hasRequired = storageGranted && (notifPerms.isEmpty() || notifGranted) && (btGranted || wifiGranted)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        storageGranted = checkPerms(storagePerms)
        btGranted = checkPerms(btPerms)
        wifiGranted = checkPerms(wifiPerms)
        notifGranted = checkPerms(notifPerms)
        
        if (storageGranted && (notifPerms.isEmpty() || notifGranted) && (btGranted || wifiGranted)) {
            onContinue()
        }
    }

    LaunchedEffect(hasRequired) {
        if (hasRequired) {
            onContinue()
        }
    }

    val permissions = listOf(
        Triple("Bluetooth", "Find & connect nearby devices", Icons.Default.Bluetooth) to btGranted,
        Triple("Wi-Fi Direct", "High-speed peer-to-peer transfers", Icons.Default.Wifi) to wifiGranted,
        Triple("Storage", "Read & save transferred files", Icons.Default.Image) to storageGranted,
        Triple("Notifications", "Transfer progress updates", Icons.Default.Notifications) to (notifPerms.isEmpty() || notifGranted)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(colors.red.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colors.red,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "A Few Permissions",
                color = colors.black,
                style = MaterialTheme.typography.h1.copy(fontSize = 30.sp, fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Just Share needs these to discover devices and transfer your files securely — all locally, never to the cloud.",
                color = colors.mutedFg,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            permissions.forEach { (perm, isGranted) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (isGranted) colors.red else colors.lightRed, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(perm.third, contentDescription = null, tint = if (isGranted) colors.white else colors.red, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = perm.first, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = colors.black)
                        Text(text = perm.second, style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), color = colors.mutedFg)
                    }
                    if (isGranted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp)) {
            PillButton(
                label = if (hasRequired) "Continue" else "Grant Permissions",
                onClick = {
                    if (hasRequired) {
                        onContinue()
                    } else {
                        val toRequest = (storagePerms + btPerms + wifiPerms + notifPerms).distinct()
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                },
                size = PillButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
