package com.blackandblue.justshare.navigation

import android.app.Application
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.CommunicationService
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.presentation.TransferViewModel
import com.blackandblue.justshare.presentation.WifiDirectViewModel
import com.blackandblue.justshare.ui.screens.NearbyPermissionAccess
import com.blackandblue.justshare.ui.screens.TransferProgressPermissionRecovery
import com.blackandblue.justshare.ui.screens.TransferProgressSummary
import com.blackandblue.justshare.ui.theme.JediShareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress

/** Real Compose navigation, production owner resolution, progress permission
 * effect and summary, and actual ViewModels. Platform grant/service calls are
 * fake; no radio, hardware wrapper or service process is used. */
class LocalTransportOwnershipTest {
    @get:Rule val composeRule = createComposeRule()
    private class PermissionContext(base: Context) : ContextWrapper(base), NearbyPermissionAccess {
        var allowed = false
        var serviceStarts = 0
        var serviceStops = 0
        val registeredReceivers = mutableListOf<BroadcastReceiver>()
        val activeReceivers = mutableSetOf<BroadcastReceiver>()
        var receiverRemovals = 0
        override val sdkInt: Int get() = Build.VERSION.SDK_INT
        override fun isGranted(permission: String) = allowed
        override fun openAppSettings() = true
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (allowed) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        override fun checkSelfPermission(permission: String): Int = checkPermission(permission, 0, 0)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? = register(receiver)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? = register(receiver)
        private fun register(receiver: BroadcastReceiver?): Intent? {
            check(receiver != null)
            check(activeReceivers.add(receiver))
            registeredReceivers.add(receiver)
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            check(activeReceivers.remove(receiver))
            receiverRemovals++
        }
        override fun startService(service: Intent): ComponentName? { serviceStarts++; return service.component }
        override fun startForegroundService(service: Intent): ComponentName? { serviceStarts++; return service.component }
        override fun stopService(service: Intent): Boolean { serviceStops++; return true }
    }
    private class Owner : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun senderProgressReusesDiscoveryOwnerAndRevocationStopsItWithoutLosingFiles() = exercise(sender = true)

    @Test
    fun receiverProgressReusesDiscoveryOwnerAndRevocationKeepsReceiveDirection() = exercise(sender = false)

    @Test
    fun immediateHomeReentryReleasesOldLeaseWhileOutgoingDiscoveryStillFades() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val context = PermissionContext(application).also { it.allowed = true }
        val mounted = mutableStateOf(true)
        val composedEntries = mutableSetOf<String>()
        val leases = mutableMapOf<String, Long>()
        lateinit var owner: Owner
        var ownerCreated = false
        lateinit var transfer: TransferViewModel
        lateinit var wifi: WifiDirectViewModel
        lateinit var nav: NavHostController
        var constructions = 0
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == WifiDirectViewModel::class.java)
                constructions++
                @Suppress("UNCHECKED_CAST")
                return WifiDirectViewModel(context) as T
            }
        }
        try {
            composeRule.runOnIdle {
                owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
                ownerCreated = true
                CommunicationService.clearTransferUpdate()
                transfer = TransferViewModel(application, UserPreferencesDataStore(application))
                owner.viewModelStore.put("overlap-transfer", transfer)
                transfer.resetTransfer()
                transfer.setMethod("wifi")
            }
            composeRule.setContent {
                if (mounted.value) {
                    CompositionLocalProvider(LocalContext provides context, LocalViewModelStoreOwner provides owner) {
                        JediShareTheme {
                            val transportOwner = checkNotNull(LocalViewModelStoreOwner.current)
                            nav = rememberNavController()
                            NavHost(
                                nav, startDestination = Screen.DiscoverWifi.route,
                                enterTransition = { fadeIn(tween(300)) },
                                exitTransition = { fadeOut(tween(300)) }
                            ) {
                                composable(
                                    Screen.Home.route,
                                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                                    exitTransition = { androidx.compose.animation.ExitTransition.None }
                                ) { Text("Overlap home") }
                                composable(Screen.DiscoverWifi.route) { entry ->
                                    DisposableEffect(entry.id) {
                                        composedEntries.add(entry.id)
                                        onDispose { composedEntries.remove(entry.id) }
                                    }
                                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                                        LocalDiscoveryDestination(LocalTransferMethod.WIFI, nav, context) {
                                            wifi = sharedWifiTransferViewModel(transportOwner, factory)
                                            WifiLocalTransferLifetime(wifi, nav, transfer) {
                                                LaunchedEffect(entry.id) {
                                                    wifi.registerLocalReceiver()
                                                    leases[entry.id] = checkNotNull(wifi.beginLocalSession())
                                                }
                                                Text("Overlap discovery")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            composeRule.onNodeWithText("Overlap discovery").assertIsDisplayed()
            composeRule.waitForIdle()
            // Hold the 300ms outgoing transition; Home and replacement enter
            // without advancing the frame clock to old-page disposal.
            composeRule.mainClock.autoAdvance = false
            val overlapStarted = composeRule.mainClock.currentTime
            lateinit var oldEntry: String
            lateinit var oldReceiver: BroadcastReceiver
            var oldLease = 0L
            lateinit var originalOwner: WifiDirectViewModel
            composeRule.runOnIdle {
                oldEntry = checkNotNull(nav.currentBackStackEntry).id
                oldLease = checkNotNull(leases[oldEntry])
                oldReceiver = context.activeReceivers.single()
                originalOwner = wifi
                wifi.setTransferRole(false)
                wifi.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply {
                    groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getLoopbackAddress()
                })
                assertEquals(1, context.serviceStarts)
                nav.navigate(Screen.Home.route)
                // Synchronous destination change, before composition/disposal.
                assertTrue(composedEntries.contains(oldEntry))
                assertTrue(context.activeReceivers.isEmpty())
                assertEquals(1, context.receiverRemovals)
                assertEquals(1, context.serviceStops)
                assertFalse(wifi.uiState.value.isConnected)
            }
            composeRule.mainClock.advanceTimeBy(16)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(Screen.Home.route, nav.currentDestination?.route)
                assertTrue(composedEntries.contains(oldEntry))
                // Faithful Receive direction; files/role are owned by the
                // shared transfer and transport, not a replacement nav entry.
                transfer.resetTransfer()
                transfer.setMethod("wifi")
                nav.navigate(Screen.DiscoverWifi.route)
            }
            composeRule.mainClock.advanceTimeBy(16)
            composeRule.waitForIdle()
            var replacementLease = 0L
            composeRule.runOnIdle {
                val replacementEntry = checkNotNull(nav.currentBackStackEntry).id
                assertTrue(composeRule.mainClock.currentTime - overlapStarted < 300)
                assertTrue(replacementEntry != oldEntry)
                assertTrue(composedEntries.contains(oldEntry))
                assertTrue(composedEntries.contains(replacementEntry))
                assertSame(originalOwner, wifi)
                assertEquals(1, constructions)
                replacementLease = checkNotNull(leases[replacementEntry])
                assertTrue(replacementLease != oldLease)
                assertEquals(2, context.registeredReceivers.size)
                assertEquals(1, context.activeReceivers.size)
                assertNotSame(oldReceiver, context.activeReceivers.single())
                wifi.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply {
                    groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getLoopbackAddress()
                })
                assertEquals(2, context.serviceStarts)
                assertFalse(wifi.releaseLocalSession(oldLease))
                oldReceiver.onReceive(context, Intent(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION).apply {
                    putExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)
                })
                assertTrue(wifi.uiState.value.isConnected)
                assertEquals(1, context.serviceStops)
                assertTrue(transfer.state.value.urisToShare.isEmpty())
            }
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                // The old lifetime's real onDispose cannot close the new lease.
                assertFalse(composedEntries.contains(oldEntry))
                assertEquals(replacementLease, checkNotNull(wifi.beginLocalSession()))
                assertTrue(wifi.uiState.value.isConnected)
                assertEquals(1, context.activeReceivers.size)
                assertEquals(1, context.receiverRemovals)
                assertEquals(1, context.serviceStops)
                assertEquals(2, context.serviceStarts)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
            composeRule.runOnIdle { mounted.value = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                if (ownerCreated) owner.viewModelStore.clear()
                CommunicationService.clearTransferUpdate()
            }
        }
    }

    private fun exercise(sender: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val context = PermissionContext(application)
        val file = File.createTempFile("permission-owner-", ".txt", application.cacheDir)
        file.writeText("Fictional pending transfer")
        val uri = Uri.fromFile(file)
        val mounted = mutableStateOf(true)
        lateinit var owner: Owner
        var ownerCreated = false
        lateinit var transfer: TransferViewModel
        lateinit var discovery: WifiDirectViewModel
        lateinit var progress: WifiDirectViewModel
        lateinit var nav: NavHostController
        var constructions = 0
        var firstLease = 0L
        lateinit var firstReceiver: BroadcastReceiver
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == WifiDirectViewModel::class.java)
                constructions++
                @Suppress("UNCHECKED_CAST")
                return WifiDirectViewModel(context) as T
            }
        }
        try {
            composeRule.runOnIdle {
                owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
                ownerCreated = true
                CommunicationService.clearTransferUpdate()
                transfer = TransferViewModel(application, UserPreferencesDataStore(application))
                owner.viewModelStore.put("pending-transfer", transfer)
                if (sender) transfer.setUris(listOf(uri)) else transfer.resetTransfer()
            }
            composeRule.setContent {
                if (mounted.value) {
                    CompositionLocalProvider(LocalContext provides context, LocalViewModelStoreOwner provides owner) {
                        JediShareTheme {
                            // Exactly as production: capture the host before NavHost
                            // replaces LocalViewModelStoreOwner for each destination.
                            val transportOwner = checkNotNull(LocalViewModelStoreOwner.current)
                            nav = rememberNavController()
                            NavHost(nav, startDestination = Screen.DiscoverWifi.route) {
                                composable(Screen.Home.route) { Text("Browse home") }
                                composable(Screen.DiscoverWifi.route) {
                                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                                        LocalDiscoveryDestination(LocalTransferMethod.WIFI, nav, context) {
                                            discovery = sharedWifiTransferViewModel(transportOwner, factory)
                                            WifiLocalTransferLifetime(discovery, nav, transfer) {
                                                LaunchedEffect(discovery) { discovery.registerLocalReceiver() }
                                                Text("Discovery ready")
                                            }
                                        }
                                    }
                                }
                                composable(Screen.TransferProgress.route) {
                                    progress = sharedWifiTransferViewModel(transportOwner, factory)
                                    val state by transfer.state.collectAsState()
                                    val wifiState by progress.uiState.collectAsState()
                                    WifiLocalTransferLifetime(progress, nav, transfer, allowIdleContent = true) {
                                        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                                            TransferProgressPermissionRecovery("wifi", transfer, state.isTransferComplete) {
                                                progress.onPermissionRevoked()
                                            }
                                            TransferProgressSummary(
                                                isSender = state.urisToShare.isNotEmpty(), isConnected = wifiState.isConnected,
                                                hasTransferStarted = state.hasTransferStarted, isDone = state.isTransferComplete,
                                                transferFailed = state.progressPercent < 0f,
                                                permissionInterrupted = state.permissionInterrupted, deviceName = "Fictional device"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            composeRule.onNodeWithText("Allow Wi-Fi Direct sharing").assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(0, constructions) }
            composeRule.runOnIdle {
                owner.registry.currentState = Lifecycle.State.STARTED
                context.allowed = true
                owner.registry.currentState = Lifecycle.State.RESUMED
            }
            composeRule.onNodeWithText("Discovery ready").assertIsDisplayed()
            val selected = transfer.state.value.fileInfos
            composeRule.runOnIdle {
                assertEquals(1, constructions)
                discovery.setTransferRole(sender)
                firstLease = checkNotNull(discovery.beginLocalSession())
                firstReceiver = context.activeReceivers.single()
                discovery.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply {
                    groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getLoopbackAddress()
                })
                transfer.setMethod("wifi")
                transfer.markTransferStarted()
                nav.navigate(Screen.TransferProgress.route)
            }
            composeRule.onNodeWithText("Transferring files…").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Transfer active").assertExists()
            composeRule.runOnIdle {
                assertSame(discovery, progress)
                assertEquals(1, constructions)
                assertEquals(1, context.serviceStarts)
                assertEquals(1, context.activeReceivers.size)
                assertEquals(0, context.receiverRemovals) // Handoff retains the live receiver.
                owner.registry.currentState = Lifecycle.State.STARTED
                context.allowed = false
                owner.registry.currentState = Lifecycle.State.RESUMED
            }
            composeRule.onNodeWithText("Transfer interrupted").assertIsDisplayed()
            composeRule.onNodeWithText("Transferring files…").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Transfer active").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Transfer stopped").assertExists()
            composeRule.runOnIdle {
                assertSame(discovery, progress)
                assertFalse(discovery.uiState.value.isConnected)
                assertTrue(context.serviceStops > 0)
                assertEquals(1, context.serviceStarts)
                assertFalse(transfer.state.value.hasTransferStarted)
                assertTrue(transfer.state.value.permissionInterrupted)
                assertEquals(-1f, transfer.state.value.progressPercent, 0f)
                assertEquals(if (sender) listOf(uri) else emptyList<Uri>(), transfer.state.value.urisToShare)
                assertEquals(selected, transfer.state.value.fileInfos)
                assertTrue(context.activeReceivers.isEmpty())
                assertEquals(1, context.receiverRemovals)
                nav.navigate(Screen.Home.route)
            }
            composeRule.onNodeWithText("Browse home").assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                context.allowed = true
                nav.navigate(Screen.DiscoverWifi.route)
            }
            composeRule.onNodeWithText("Discovery ready").assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            var stopsBeforeExit = 0
            composeRule.runOnIdle {
                assertSame(discovery, progress)
                assertEquals(1, constructions)
                assertEquals(2, context.registeredReceivers.size)
                assertEquals(1, context.activeReceivers.size)
                val replacementLease = checkNotNull(discovery.beginLocalSession())
                assertTrue(replacementLease != firstLease)
                discovery.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply {
                    groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getLoopbackAddress()
                })
                assertEquals(2, context.serviceStarts)
                stopsBeforeExit = context.serviceStops
                assertFalse(discovery.releaseLocalSession(firstLease))
                assertEquals(stopsBeforeExit, context.serviceStops)
                assertTrue(discovery.uiState.value.isConnected)
                // A queued old registration must not change the replacement state.
                firstReceiver.onReceive(context, Intent(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION).apply {
                    putExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)
                })
                assertTrue(discovery.uiState.value.isConnected)
                assertEquals(stopsBeforeExit, context.serviceStops)
                // Check actual role survived cleanup, without reading private fields.
                discovery.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply { groupFormed = false })
                assertEquals(if (sender) "" else "hosting", discovery.uiState.value.connectionStatus)
                assertEquals(if (sender) listOf(uri) else emptyList<Uri>(), transfer.state.value.urisToShare)
                nav.navigate(Screen.Home.route)
            }
            composeRule.onNodeWithText("Browse home").assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue(context.activeReceivers.isEmpty())
                assertEquals(2, context.receiverRemovals)
                assertTrue(context.serviceStops > stopsBeforeExit)
                assertFalse(discovery.uiState.value.isConnected)
                // Grants remain allowed, but callbacks after browse exit cannot restart.
                discovery.connectionInfoListener.onConnectionInfoAvailable(WifiP2pInfo().apply {
                    groupFormed = true; isGroupOwner = false; groupOwnerAddress = InetAddress.getLoopbackAddress()
                })
                assertEquals(2, context.serviceStarts)
                assertFalse(discovery.uiState.value.isConnected)
                assertEquals(selected, transfer.state.value.fileInfos)
            }
        } finally {
            composeRule.runOnIdle { mounted.value = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                if (ownerCreated) owner.viewModelStore.clear()
                CommunicationService.clearTransferUpdate()
            }
            assertTrue(file.delete())
        }
    }
}
