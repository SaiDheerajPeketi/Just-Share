package com.blackandblue.justshare.ui.components

import android.view.KeyEvent
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.blackandblue.justshare.ui.theme.JediShareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransferExitGuardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun ExitButton(active: Boolean, onLeave: () -> Unit) {
        JediShareTheme {
            val requestExit = rememberTransferExitRequest(active, onLeave)
            Button(onClick = requestExit) { Text("Exit") }
        }
    }

    @Test
    fun keepTransferringDoesNotDisconnect() {
        var leaves = 0
        composeRule.setContent { ExitButton(true) { leaves++ } }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.onNodeWithText("Stop this transfer?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, leaves) }
        composeRule.onNodeWithText("Keep transferring").performClick()
        composeRule.onNodeWithText("Stop this transfer?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, leaves) }
    }

    @Test
    fun stopTransferCallsTheExitActionOnce() {
        var leaves = 0
        composeRule.setContent { ExitButton(true) { leaves++ } }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.onNodeWithText("Stop transfer").performClick()
        composeRule.onNodeWithText("Stop this transfer?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, leaves) }
    }

    @Test
    fun systemBackOffersAChoiceAndSecondBackKeepsTransfer() {
        var leaves = 0
        composeRule.setContent { ExitButton(true) { leaves++ } }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithText("Stop this transfer?").assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithText("Stop this transfer?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, leaves) }
    }

    @Test
    fun finishedOrFailedTransferExitsWithoutStopPrompt() {
        var leaves = 0
        composeRule.setContent { ExitButton(false) { leaves++ } }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.onNodeWithText("Stop this transfer?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, leaves) }
    }

    @Test
    fun finishingWhilePromptIsOpenDoesNotCancelOrLeave() {
        val active = mutableStateOf(true)
        var leaves = 0
        composeRule.setContent { ExitButton(active.value) { leaves++ } }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.runOnIdle { active.value = false }
        composeRule.onNodeWithText("Stop this transfer?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, leaves) }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.runOnIdle { assertEquals(1, leaves) }
    }

    @Test
    fun pendingChoiceSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(composeRule)
        var leaves = 0
        restoration.setContent { ExitButton(true) { leaves++ } }
        composeRule.onNodeWithText("Exit").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Stop this transfer?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep transferring").performClick()
        composeRule.runOnIdle { assertEquals(0, leaves) }
    }

    @Test
    fun confirmationUsesTheCurrentExitAction() {
        val currentAction = mutableStateOf(false)
        var oldLeaves = 0
        var currentLeaves = 0
        composeRule.setContent {
            val action: () -> Unit = if (currentAction.value) {
                { currentLeaves++ }
            } else {
                { oldLeaves++ }
            }
            ExitButton(true, action)
        }
        composeRule.onNodeWithText("Exit").performClick()
        composeRule.runOnIdle { currentAction.value = true }
        composeRule.onNodeWithText("Stop transfer").performClick()
        composeRule.runOnIdle {
            assertEquals(0, oldLeaves)
            assertEquals(1, currentLeaves)
        }
    }
}
