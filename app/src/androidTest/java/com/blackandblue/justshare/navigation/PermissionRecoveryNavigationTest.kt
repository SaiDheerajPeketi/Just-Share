package com.blackandblue.justshare.navigation

import android.app.Application
import android.net.Uri
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.localTransferPermissions
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.presentation.TransferViewModel
import com.blackandblue.justshare.ui.screens.NearbyPermissionAccess
import com.blackandblue.justshare.ui.theme.JediShareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Navigation Compose destinations and the production gate; discovery is
 * deliberately a harmless fixture. No Bluetooth/Wi-Fi grant or service is used. */
class PermissionRecoveryNavigationTest {
    @get:Rule val composeRule = createComposeRule()
    private class Access : NearbyPermissionAccess {
        override val sdkInt = 33
        val grants = mutableSetOf<String>()
        var settingsOpens = 0
        override fun isGranted(permission: String) = permission in grants
        override fun openAppSettings(): Boolean { settingsOpens++; return true }
    }
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var owner: Owner
    private lateinit var nav: NavHostController
    private var starts = 0
    private var stops = 0

    private fun graph(access: Access, method: LocalTransferMethod, direct: Boolean = true, vm: TransferViewModel? = null) {
        val route = if (method == LocalTransferMethod.BLUETOOTH) Screen.DiscoverBT.route else Screen.DiscoverWifi.route
        composeRule.runOnIdle { owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
        composeRule.setContent {
            JediShareTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = if (direct) route else Screen.SelectFiles.createRoute("wifi")) {
                    composable(Screen.Home.route) { Text("Browse home") }
                    composable(Screen.SelectFiles.createRoute("wifi")) { Text("Selected files") }
                    composable(Screen.History.route) { Text("Browse history") }
                    composable(Screen.Settings.route) { Text("Browse settings") }
                    composable(Screen.AlterSend.route) { Text("Remote page") }
                    composable(route) {
                        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                            LocalDiscoveryDestination(method, nav, access) {
                                DisposableEffect(Unit) { starts++; onDispose { stops++ } }
                                val state = vm?.state?.collectAsState()?.value
                                Text(if (state == null) "Sharing ready" else
                                    "Sharing ${if (state.urisToShare.isNotEmpty()) "sender" else "receiver"}")
                            }
                        }
                    }
                }
            }
        }
    }
    private fun resume(change: () -> Unit) {
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            change()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
    }

    @Test
    fun otherTransportGrantCannotConstructBluetoothDiscovery() {
        val access = Access().also { it.grants.addAll(localTransferPermissions(LocalTransferMethod.WIFI, it.sdkInt)) }
        graph(access, LocalTransferMethod.BLUETOOTH)
        composeRule.onNodeWithText("Allow Bluetooth sharing").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, starts) }
        resume { access.grants.add(localTransferPermissions(LocalTransferMethod.BLUETOOTH, access.sdkInt).first()) }
        composeRule.onNodeWithText("Sharing ready").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, starts) }
        resume { access.grants.addAll(localTransferPermissions(LocalTransferMethod.BLUETOOTH, access.sdkInt)) }
        composeRule.onNodeWithText("Sharing ready").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, starts); assertEquals(Screen.DiscoverBT.route, nav.currentDestination?.route) }
    }

    @Test
    fun directShareDenialCanBrowseAndResumeDoesNotBounceNonlocalPages() {
        val access = Access()
        graph(access, LocalTransferMethod.WIFI)
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Browse home").assertIsDisplayed()
        for ((route, label) in listOf(Screen.History.route to "Browse history",
            Screen.Settings.route to "Browse settings", Screen.AlterSend.route to "Remote page")) {
            composeRule.runOnIdle { nav.navigate(route) }
            resume {}
            composeRule.onNodeWithText(label).assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(route, nav.currentDestination?.route); assertEquals(0, starts) }
        }
    }

    @Test
    fun foregroundRevocationDisposesDiscoveryBeforeRecoveryAndCanResume() {
        val access = Access().also { it.grants.addAll(localTransferPermissions(LocalTransferMethod.WIFI, it.sdkInt)) }
        graph(access, LocalTransferMethod.WIFI)
        composeRule.onNodeWithText("Sharing ready").assertIsDisplayed()
        resume { access.grants.clear() }
        composeRule.onNodeWithText("Allow Wi-Fi Direct sharing").assertIsDisplayed()
        composeRule.onNodeWithText("Sharing ready").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, starts); assertEquals(1, stops) }
        composeRule.onNodeWithText("Open app settings").performClick()
        resume { access.grants.addAll(localTransferPermissions(LocalTransferMethod.WIFI, access.sdkInt)) }
        composeRule.onNodeWithText("Sharing ready").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, starts); assertEquals(1, access.settingsOpens) }
    }

    @Test
    fun selectedFilesAndSenderRoleSurviveDenialSettingsAndReturnToPicker() = withTransfer(true) { vm, uri ->
        val access = Access()
        graph(access, LocalTransferMethod.WIFI, direct = false, vm = vm)
        composeRule.runOnIdle { nav.navigate(Screen.DiscoverWifi.route) }
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.onNodeWithText("Selected files").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(uri), vm.state.value.urisToShare); nav.navigate(Screen.DiscoverWifi.route) }
        composeRule.onNodeWithText("Open app settings").performClick()
        resume { access.grants.addAll(localTransferPermissions(LocalTransferMethod.WIFI, access.sdkInt)) }
        composeRule.onNodeWithText("Sharing sender").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(uri), vm.state.value.urisToShare); assertEquals(Screen.DiscoverWifi.route, nav.currentDestination?.route) }
    }

    @Test
    fun receiverRoleSurvivesDirectEntryRecoveryWithoutInventingFiles() = withTransfer(false) { vm, _ ->
        val access = Access()
        graph(access, LocalTransferMethod.BLUETOOTH, vm = vm)
        composeRule.onNodeWithText("Open app settings").performClick()
        resume { access.grants.addAll(localTransferPermissions(LocalTransferMethod.BLUETOOTH, access.sdkInt)) }
        composeRule.onNodeWithText("Sharing receiver").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(vm.state.value.urisToShare.isEmpty()); assertEquals(Screen.DiscoverBT.route, nav.currentDestination?.route) }
    }

    private fun withTransfer(sender: Boolean, block: (TransferViewModel, Uri) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val store = ViewModelStore()
        val file = File.createTempFile("permission-selection-", ".txt", application.cacheDir)
        file.writeText("Fictional selected file")
        val uri = Uri.fromFile(file)
        lateinit var vm: TransferViewModel
        try {
            composeRule.runOnIdle {
                vm = TransferViewModel(application, UserPreferencesDataStore(application))
                store.put("permission-selection", vm)
                if (sender) vm.setUris(listOf(uri)) else vm.resetTransfer()
            }
            block(vm, uri)
        } finally {
            composeRule.runOnIdle { store.clear() }
            assertTrue(file.delete())
        }
    }
}
