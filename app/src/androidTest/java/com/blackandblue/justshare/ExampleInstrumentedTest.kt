package com.blackandblue.justshare

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.blackandblue.justshare", appContext.packageName)
    }

    @Test
    fun transferServicesArePrivateForegroundDataSyncServices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(CommunicationService::class.java, AlterSendForegroundService::class.java).forEach { service ->
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(
                    ComponentName(context, service),
                    PackageManager.ComponentInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(ComponentName(context, service), 0)
            }

            assertFalse("${service.simpleName} must not be exported", info.exported)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertTrue(
                    "${service.simpleName} must declare dataSync foreground service type",
                    info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0
                )
            }
        }
    }
}
