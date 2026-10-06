package com.blackandblue.justshare

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTransferPermissionsTest {
    @Test
    fun legacyDiscoveryNeedsPreciseLocationAndReceivingNeedsStorage() {
        LocalTransferMethod.entries.forEach { method ->
            val legacy = localTransferPermissions(method, 28)
            assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in legacy)
            assertTrue(Manifest.permission.WRITE_EXTERNAL_STORAGE in legacy)
            assertFalse(Manifest.permission.ACCESS_COARSE_LOCATION in legacy)
            assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in localTransferPermissions(method, 29))
        }
    }

    @Test
    fun android12BluetoothDoesNotGrantWifiDiscovery() {
        val bluetooth = localTransferPermissions(LocalTransferMethod.BLUETOOTH, 31)
        val wifi = localTransferPermissions(LocalTransferMethod.WIFI, 31)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in bluetooth)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in bluetooth)
        assertTrue(Manifest.permission.BLUETOOTH_ADVERTISE in bluetooth)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in bluetooth)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in wifi)
        assertFalse(wifi.all { it in bluetooth })
    }

    @Test
    fun android13WifiDoesNotGrantBluetoothAndNotificationsStayOptional() {
        val wifi = localTransferPermissions(LocalTransferMethod.WIFI, 33)
        val bluetooth = localTransferPermissions(LocalTransferMethod.BLUETOOTH, 33)
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in wifi)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in wifi)
        assertFalse(bluetooth.all { it in wifi })
        (wifi + bluetooth).forEach { permission ->
            assertFalse(permission == Manifest.permission.POST_NOTIFICATIONS)
            assertFalse(permission == Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    @Test
    fun locationRequestOffersBothChoicesButApproximateAloneCannotUnlockDiscovery() {
        val needed = localTransferPermissions(LocalTransferMethod.WIFI, 31)
        val request = localPermissionRequest(needed + needed)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in request)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in request)
        assertTrue(request.size == request.distinct().size)
        assertFalse(needed.all { it == Manifest.permission.ACCESS_COARSE_LOCATION })
        assertFalse(Manifest.permission.ACCESS_COARSE_LOCATION in
            localPermissionRequest(localTransferPermissions(LocalTransferMethod.BLUETOOTH, 33)))
    }

}
