package com.blackandblue.justshare.presentation

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pInfo
import android.provider.Settings
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.ui.screens.AndroidNearbyPermissionAccess
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class WifiPermissionRecoveryTest {
    private class PermissionContext(base: Context) : ContextWrapper(base) {
        var allowed = false
        var serviceStarts = 0
        var serviceStops = 0
        var settingsIntent: Intent? = null
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (allowed) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        override fun startService(service: Intent): ComponentName? { serviceStarts++; return service.component }
        override fun startForegroundService(service: Intent): ComponentName? { serviceStarts++; return service.component }
        override fun stopService(service: Intent): Boolean { serviceStops++; return true }
        override fun startActivity(intent: Intent) { settingsIntent = intent }
    }
    private fun connectedInfo() = WifiP2pInfo().apply {
        groupFormed = true
        isGroupOwner = false
        groupOwnerAddress = InetAddress.getLoopbackAddress()
    }
    private fun withViewModel(block: (WifiDirectViewModel, PermissionContext) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = PermissionContext(instrumentation.targetContext)
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            val vm = WifiDirectViewModel(context)
            store.put("permission-recovery", vm)
            try { block(vm, context) } finally { store.clear() }
        }
    }

    @Test
    fun deniedLateConnectionAndDiscoveryCallbacksCannotStartLocalService() = withViewModel { vm, context ->
        vm.onWifiDirectEnabled(true)
        vm.startDiscovery()
        vm.startHosting()
        vm.connectionInfoListener.onConnectionInfoAvailable(connectedInfo())
        assertEquals(0, context.serviceStarts)
        assertFalse(vm.uiState.value.isConnected)
        assertFalse(vm.uiState.value.isDiscovering)
        assertEquals("Allow nearby sharing to continue.", vm.uiState.value.errorMessage)
    }

    @Test
    fun revocationStopsExistingServiceAndRejectsQueuedConnectionCallback() = withViewModel { vm, context ->
        context.allowed = true
        vm.connectionInfoListener.onConnectionInfoAvailable(connectedInfo())
        assertEquals(1, context.serviceStarts)
        assertTrue(vm.uiState.value.isConnected)
        context.allowed = false
        vm.onPermissionRevoked()
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(context.serviceStops > 0)
        vm.connectionInfoListener.onConnectionInfoAvailable(connectedInfo())
        assertEquals(1, context.serviceStarts)
    }

    @Test
    fun settingsRecoveryTargetsOnlyThisAppsPermissionPage() {
        val context = PermissionContext(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(AndroidNearbyPermissionAccess(context).openAppSettings())
        val intent = context.settingsIntent!!
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
    }
}
