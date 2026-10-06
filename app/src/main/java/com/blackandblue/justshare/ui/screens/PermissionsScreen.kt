package com.blackandblue.justshare.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.localTransferPermissions
import com.blackandblue.justshare.localPermissionRequest
import com.blackandblue.justshare.hasLocalTransferPermissions
import com.blackandblue.justshare.ui.components.PillButton
import com.blackandblue.justshare.ui.theme.JediShareTheme

/** The Android boundary is injectable so lifecycle recovery can be exercised
 * without changing the device's actual permission grants. */
interface NearbyPermissionAccess {
    val sdkInt: Int
    fun isGranted(permission: String): Boolean
    fun openAppSettings(): Boolean
}

class AndroidNearbyPermissionAccess(private val context: Context) : NearbyPermissionAccess {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT
    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun openAppSettings(): Boolean = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.isSuccess
}

@Composable
internal fun rememberPermissionAccess(): NearbyPermissionAccess {
    val context = LocalContext.current
    return remember(context) { AndroidNearbyPermissionAccess(context) }
}

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    onNotNow: () -> Unit = onContinue,
    permissionAccess: NearbyPermissionAccess = rememberPermissionAccess()
) {
    PermissionRecovery(null, onContinue, onNotNow, permissionAccess)
}

/** Do not construct discovery content (including its default ViewModels) until
 * this exact transport is allowed. Recovery stays on the original destination. */
@Composable
fun LocalTransferPermissionGate(
    method: LocalTransferMethod,
    onNotNow: () -> Unit,
    permissionAccess: NearbyPermissionAccess = rememberPermissionAccess(),
    content: @Composable () -> Unit
) {
    PermissionRecovery(method, {}, onNotNow, permissionAccess, content)
}

@Composable
private fun PermissionRecovery(
    method: LocalTransferMethod?,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    access: NearbyPermissionAccess,
    allowedContent: (@Composable () -> Unit)? = null
) {
    val methods = remember(method) { method?.let { listOf(it) } ?: LocalTransferMethod.entries.toList() }
    val requested = remember(methods, access.sdkInt) {
        methods.flatMap { localTransferPermissions(it, access.sdkInt) }.distinct()
    }
    val notifications = if (access.sdkInt >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    fun snapshot() = (requested + notifications).associateWith(access::isGranted)
    var grants by remember(access, method) { mutableStateOf(snapshot()) }
    var settingsError by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState({ settingsError = false; grants = snapshot() })
    DisposableEffect(lifecycleOwner, access, method) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }
    // Also recheck before each composition of protected content. Lifecycle
    // events trigger recomposition, but another state change must not reuse an
    // old allow result after the grant has been revoked.
    val checkedGrants = grants.mapValues { access.isGranted(it.key) }
    val ready = methods.any { candidate ->
        localTransferPermissions(candidate, access.sdkInt).all { checkedGrants[it] == true }
    }
    if (ready && allowedContent != null) {
        allowedContent()
        return
    }
    BackHandler(onBack = onNotNow)

    val colors = JediShareTheme.colors
    Column(Modifier.fillMaxSize().background(colors.surface).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(96.dp).background(colors.red.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(if (method == LocalTransferMethod.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                    contentDescription = null, tint = colors.red, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = method?.let { "Allow ${it.label} sharing" } ?: "Share with nearby devices",
                style = MaterialTheme.typography.h1.copy(fontWeight = FontWeight.Black, fontSize = 30.sp),
                color = colors.black, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (method == null) "You can allow nearby sharing now or browse the app and choose later."
                    else "Allow nearby access to find devices and send or receive files. You can allow access in app settings, then return to this sharing step.",
                style = MaterialTheme.typography.body1, color = colors.mutedFg, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            methods.forEach { candidate ->
                val nearby = localTransferPermissions(candidate, access.sdkInt)
                    .filterNot { it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
                val description = if (Manifest.permission.ACCESS_FINE_LOCATION in nearby) {
                    if (access.sdkInt >= 31) "Allow Precise location to find nearby devices"
                    else "Allow Location to find nearby devices"
                } else "Find and connect to nearby devices"
                PermissionRow(candidate.label, description,
                    if (candidate == LocalTransferMethod.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                    nearby.all { checkedGrants[it] == true })
            }
            if (access.sdkInt <= 28) {
                PermissionRow("Save received files", "Save incoming files on this device", Icons.Default.Folder,
                    checkedGrants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)
            }
            if (notifications.isNotEmpty()) {
                PermissionRow("Notifications", "Optional transfer progress updates", Icons.Default.Notifications,
                    notifications.all { checkedGrants[it] == true }, optional = true)
                if (notifications.any { checkedGrants[it] != true }) {
                    TextButton(onClick = { launcher.launch(notifications.toTypedArray()) }) {
                        Text("Allow notifications", color = colors.red)
                    }
                }
            }
            if (!ready) {
                Spacer(Modifier.height(16.dp))
                Text("If Android no longer asks, open app settings and choose Permissions.",
                    style = MaterialTheme.typography.body2, color = colors.mutedFg, textAlign = TextAlign.Center)
            }
            if (settingsError) {
                Spacer(Modifier.height(12.dp))
                Text("App settings could not open. Try again, or open Just Share in your device's Settings.",
                    style = MaterialTheme.typography.body2, color = colors.red, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            PillButton(label = if (ready) "Continue" else "Allow nearby sharing", onClick = {
                val current = snapshot()
                grants = current
                val currentlyReady = methods.any { candidate ->
                    localTransferPermissions(candidate, access.sdkInt).all { current[it] == true }
                }
                if (currentlyReady) onContinue()
                else launcher.launch(localPermissionRequest(requested.filter { current[it] != true }).toTypedArray())
            }, modifier = Modifier.fillMaxWidth())
            if (!ready) {
                TextButton(onClick = { settingsError = !access.openAppSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings", color = colors.red)
                }
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text("Not now", color = colors.mutedFg)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, icon: ImageVector, granted: Boolean, optional: Boolean = false) {
    val colors = JediShareTheme.colors
    val status = if (granted) "Allowed" else if (optional) "Optional, off" else "Not allowed"
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {
        stateDescription = status
    }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(colors.red.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.red, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
            Text(description, style = MaterialTheme.typography.body2, color = colors.mutedFg)
            Text(status, style = MaterialTheme.typography.body2, color = if (granted) colors.red else colors.mutedFg)
        }
        if (granted) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.red, modifier = Modifier.size(24.dp))
        }
    }
}

/** Cancels retained local work when grants are revoked, including when the
 * contextual gate removes discovery content. Normal progress navigation keeps
 * its connection intact. */
@Composable
internal fun LocalTransferPermissionLossEffect(method: LocalTransferMethod, onPermissionLost: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onLoss by rememberUpdatedState(onPermissionLost)
    DisposableEffect(context, lifecycleOwner, method) {
        fun check() {
            if (!hasLocalTransferPermissions(context, method)) onLoss()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            check()
        }
    }
}
