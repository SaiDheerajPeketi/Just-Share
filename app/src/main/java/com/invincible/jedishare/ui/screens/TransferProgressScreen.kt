package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.TransferViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.theme.JediShareTheme

data class TransferFile(
    val name: String,
    val size: String,
    val icon: ImageVector,
    val isDone: Boolean,
    val isActive: Boolean
)

@Composable
fun TransferProgressScreen(
    transferViewModel: TransferViewModel,
    btViewModel: BluetoothViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    val state by transferViewModel.state.collectAsState()
    val btProgress by btViewModel.transferProgress.collectAsState()
    val btState by btViewModel.statee.collectAsState()
    
    val isSender = state.urisToShare.isNotEmpty()
    val method = state.method
    
    val progress = if (method == "bt") {
        if (isSender) btProgress.sentPercent.toFloat() else btProgress.receivedPercent.toFloat()
    } else {
        state.progressPercent
    }
    
    val isDone = if (method == "bt") {
        progress >= 100f
    } else {
        state.isTransferComplete
    }
    val btFileInfo by btViewModel.fileInfoState.collectAsState()
    val btIncomingFileName by btViewModel.incomingFileNameState.collectAsState()

    // If there is no active file being broadcasted yet, show an empty list or the mock list with updated state
    // We can map the urisToShare from the state to display all files that were selected.
    val files = if (state.urisToShare.isNotEmpty()) {
        state.urisToShare.mapIndexed { index, uri ->
            val isActive = state.currentFileName.isNotEmpty() && uri.toString().contains(state.currentFileName) 
                           || (state.currentFileName.isEmpty() && index == 0 && !isDone)
            TransferFile(
                name = btIncomingFileName ?: uri.lastPathSegment ?: "File ${index + 1}",
                size = if (btFileInfo > 0) "${btFileInfo / 1024} KB" else "Unknown",
                icon = Icons.Default.InsertDriveFile,
                isDone = isDone || (index < state.currentFileIndex),
                isActive = isActive
            )
        }
    } else {
        // Fallback for receiving side (we might not know all incoming files beforehand)
        listOf(
            TransferFile(
                name = btIncomingFileName ?: "Incoming File...",
                size = if (btFileInfo > 0) "${btFileInfo / 1024} KB" else "Unknown",
                icon = Icons.Default.InsertDriveFile,
                isDone = isDone,
                isActive = !isDone
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(title = "Transfer Progress", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lottie placeholder
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(colors.red.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isDone) Icons.Default.Check else Icons.Default.Send, contentDescription = null, tint = colors.red, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(if (isDone) "Transfer Complete" else "Transferring files…", style = MaterialTheme.typography.caption, color = colors.mutedFg)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.connectedDeviceName ?: "Unknown Device", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), color = colors.black)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(8.dp).background(colors.green, CircleShape))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
        ) {
            itemsIndexed(files) { index, file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconBg = if (file.isDone) Color(0xFFE8F5E9) else if (file.isActive) colors.lightRed else Color(0xFFF0F0F0)
                        val iconTint = if (file.isDone) colors.green else if (file.isActive) colors.red else colors.mutedFg

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(iconBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(file.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.isActive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = colors.green, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(text = file.size, style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), color = colors.mutedFg)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (file.isDone) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                        } else if (file.isActive) {
                            Text(text = "${progress.toInt()}%", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold), color = colors.red)
                        } else {
                            Box(modifier = Modifier.size(18.dp).border(2.dp, colors.mutedFg, CircleShape))
                        }
                    }

                    if (file.isActive && !file.isDone) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            color = colors.red,
                            backgroundColor = colors.red.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                    }
                }
                
                if (index < files.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = { onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.red,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Icon(if (isDone) Icons.Default.Home else Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDone) "Go Home" else "Disconnect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
