package com.blackandblue.justshare.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackandblue.justshare.LocalTransferMethod
import com.blackandblue.justshare.localTransferPermissions
import com.blackandblue.justshare.ui.theme.JediShareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private class PermissionAccess : NearbyPermissionAccess {
        override val sdkInt = 33
        val grants = mutableSetOf<String>()
        var settingsOpened = 0
        var settingsAvailable = true
        override fun isGranted(permission: String) = permission in grants
        override fun openAppSettings(): Boolean { settingsOpened++; return settingsAvailable }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun finalPermissionAndRecoveryActionsRemainAccessible() {
        val access = PermissionAccess()
        composeRule.setContent { JediShareTheme { PermissionsScreen({}, permissionAccess = access) } }
        composeRule.onNodeWithText("Notifications").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Allow nearby sharing").assertIsDisplayed()
        composeRule.onNodeWithText("Open app settings").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test
    fun returningFromSettingsRefreshesActualGrantsWithoutAutoNavigation() {
        val access = PermissionAccess()
        lateinit var owner: Owner
        var continues = 0
        composeRule.runOnIdle { owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                JediShareTheme { PermissionsScreen({ continues++ }, permissionAccess = access) }
            }
        }
        composeRule.onNodeWithText("Open app settings").performClick()
        composeRule.runOnIdle {
            assertEquals(1, access.settingsOpened)
            owner.registry.currentState = Lifecycle.State.STARTED
            access.grants.addAll(localTransferPermissions(LocalTransferMethod.BLUETOOTH, access.sdkInt))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, continues) }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.runOnIdle { assertEquals(1, continues) }
    }

    @Test
    fun unavailableSettingsKeepsRecoveryAndBrowseExitUsable() {
        val access = PermissionAccess().also { it.settingsAvailable = false }
        var exits = 0
        composeRule.setContent { JediShareTheme { PermissionsScreen({}, { exits++ }, access) } }
        composeRule.onNodeWithText("Open app settings").performClick()
        composeRule.onNodeWithText("App settings could not open.", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not now").performClick()
        composeRule.runOnIdle { assertEquals(1, exits) }
    }
}
