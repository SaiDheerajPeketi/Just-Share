package com.blackandblue.justshare.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.blackandblue.justshare.ui.theme.JediShareTheme

@Composable
internal fun rememberTransferExitRequest(
    isTransferActive: Boolean,
    onLeave: () -> Unit
): () -> Unit {
    var showConfirmation by rememberSaveable(isTransferActive) { mutableStateOf(false) }
    val currentOnLeave by rememberUpdatedState(onLeave)
    val requestExit: () -> Unit = {
        if (isTransferActive) showConfirmation = true else currentOnLeave()
    }

    BackHandler(onBack = requestExit)

    if (showConfirmation && isTransferActive) {
        val colors = JediShareTheme.colors
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            backgroundColor = colors.cardBg,
            contentColor = colors.black,
            title = { Text("Stop this transfer?") },
            text = { Text("This disconnects from the other device and stops the transfer.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    currentOnLeave()
                }) {
                    Text("Stop transfer", color = colors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Keep transferring", color = colors.black)
                }
            }
        )
    }
    return requestExit
}
