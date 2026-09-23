package com.blackandblue.justshare.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.blackandblue.justshare.ui.theme.JediShareTheme
import org.junit.Rule
import org.junit.Test

class PermissionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun finalPermissionAndActionRemainAccessible() {
        composeRule.setContent {
            JediShareTheme {
                PermissionsScreen(onContinue = {})
            }
        }

        composeRule.onNodeWithText("Notifications")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("Grant Permissions") or hasText("Continue"))
            .assertIsDisplayed()
    }
}
