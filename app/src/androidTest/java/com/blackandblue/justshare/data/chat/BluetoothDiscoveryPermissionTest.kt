package com.blackandblue.justshare.data.chat

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.domain.chat.BluetoothDeviceDomain
import org.junit.Assert.*
import org.junit.Test

/** Actual controller callback handler/registration with lazy protected metadata
 * probes. The fake Context supplies no BluetoothManager, so no radio is used. */
class BluetoothDiscoveryPermissionTest {
    private class PermissionContext(base: Context) : ContextWrapper(base) {
        var deniedPermission: String? = null
        val receivers = mutableMapOf<BroadcastReceiver, IntentFilter>()
        var foundRegistrations = 0
        var foundRemovals = 0
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission == deniedPermission) PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED
        override fun checkSelfPermission(permission: String): Int = checkPermission(permission, 0, 0)
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
            if (receiver != null) {
                receivers[receiver] = filter
                if (filter.hasAction(BluetoothDevice.ACTION_FOUND)) foundRegistrations++
            }
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            val filter = receivers.remove(receiver) ?: throw IllegalArgumentException("Receiver already inactive")
            if (filter.hasAction(BluetoothDevice.ACTION_FOUND)) foundRemovals++
        }
        fun activeFoundReceivers() = receivers.values.count { it.hasAction(BluetoothDevice.ACTION_FOUND) }
    }
    private fun withController(block: (AndroidBluetoothController, PermissionContext) -> Unit) {
        val context = PermissionContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val controller = AndroidBluetoothController(context)
        try { block(controller, context) } finally { controller.release() }
    }

    @Test
    fun liveGrantRevocationRejectsProtectedMetadataAndUnregistersFoundReceiver() = withController { controller, context ->
        controller.startDiscovery()
        assertEquals(1, context.activeFoundReceivers())
        var reads = 0
        controller.handleFoundDevice { reads++; BluetoothDeviceDomain("Fictional phone", "00:11:22:33:44:55") }
        assertEquals(1, controller.scannedDevices.value.size)
        context.deniedPermission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT
            else Manifest.permission.ACCESS_FINE_LOCATION
        controller.handleFoundDevice { reads++; throw AssertionError("Protected metadata must not be read") }
        assertEquals(1, reads)
        assertEquals(0, context.activeFoundReceivers())
        assertEquals(1, context.foundRemovals)
    }

    @Test
    fun protectedGetterRaceIsCaughtAndQueuedCallbacksStayInactive() = withController { controller, context ->
        controller.startDiscovery()
        var reads = 0
        controller.handleFoundDevice { reads++; throw SecurityException("Grant revoked after the check") }
        assertEquals(1, reads)
        assertTrue(controller.scannedDevices.value.isEmpty())
        assertEquals(0, context.activeFoundReceivers())
        controller.handleFoundDevice { reads++; BluetoothDeviceDomain("Late phone", "00:11:22:33:44:66") }
        assertEquals(1, reads)
        assertTrue(controller.scannedDevices.value.isEmpty())
    }

    @Test
    fun stopWithoutScanGrantUnregistersAndRegrantRegistersOnceAgain() = withController { controller, context ->
        controller.startDiscovery()
        context.deniedPermission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN
            else Manifest.permission.BLUETOOTH_ADMIN
        controller.stopDiscovery()
        assertEquals(0, context.activeFoundReceivers())
        controller.stopDiscovery()
        assertEquals(1, context.foundRemovals)
        context.deniedPermission = null
        controller.startDiscovery()
        assertEquals(1, context.activeFoundReceivers())
        assertEquals(2, context.foundRegistrations)
        controller.closeConnection()
        assertEquals(0, context.activeFoundReceivers())
    }
}
