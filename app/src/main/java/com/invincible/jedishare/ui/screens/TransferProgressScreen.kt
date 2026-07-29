package com.invincible.jedishare.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.invincible.jedishare.presentation.WifiDirectViewModel
import com.invincible.jedishare.presentation.TransferViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.theme.JediShareTheme

data class TransferFile(
    val name: String,
    val size: String,
    val icon: ImageVector,
    val mimeType: String? = null,
    val isDone: Boolean,
    val isActive: Boolean,
    val progress: Float,
    val isFailed: Boolean = false
)


@Composable
fun TransferProgressScreen(
    transferViewModel: TransferViewModel,
    btViewModel: BluetoothViewModel = hiltViewModel(),
    wifiViewModel: WifiDirectViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    BackHandler {
        // Do nothing to prevent system back button
    }
    val colors = JediShareTheme.colors
    val state by transferViewModel.state.collectAsState()
    val btProgress by btViewModel.transferProgress.collectAsState()
    val btConnectedDeviceName by btViewModel.connectedDeviceNameState.collectAsState()
    val btState by btViewModel.state.collectAsState()
    val wifiState by wifiViewModel.uiState.collectAsState()
    
    val isSender = state.urisToShare.isNotEmpty()
    val method = state.method
    var navigatingAway by remember { mutableStateOf(false) }
    
    val progress = if (method == "bt") {
        if (isSender) {
            if (btProgress.bytesSent < 0L) -1f else btProgress.sentPercent.toFloat()
        } else {
            if (btProgress.bytesReceived < 0L) -1f else btProgress.receivedPercent.toFloat()
        }
    } else {
        state.progressPercent
    }
    
    val btCurrFileCount by btViewModel.currFileCount.collectAsState()
    val btIncomingManifest by btViewModel.incomingManifestState.collectAsState()

    val isDone = navigatingAway || if (method == "bt") {
        if (isSender) {
            state.urisToShare.isNotEmpty() && btCurrFileCount >= state.urisToShare.size
        } else {
            if (btIncomingManifest != null) {
                btCurrFileCount >= btIncomingManifest!!.size
            } else {
                progress >= 100f
            }
        }
    } else {
        if (isSender) {
            state.isTransferComplete
        } else {
            state.isTransferComplete
        }
    }
    val btFileInfo by btViewModel.fileInfoState.collectAsState()
    val btIncomingFileName by btViewModel.incomingFileNameState.collectAsState()
    val btIncomingManifest by btViewModel.incomingManifestState.collectAsState()

    val manifest = if (method == "wifi") state.fileInfos.ifEmpty { null } else btIncomingManifest

    val isConnected = if (method == "bt") btState.isConnected else wifiState.isConnected
    val transferFailed = !isDone && (progress < 0f || (!isConnected && !state.hasTransferStarted))

    val receiverFilesBase = remember(manifest) {
        manifest?.map { fileInfo ->
            TransferFile(
                name = fileInfo.fileName ?: "Unknown",
                size = fileInfo.size?.toLongOrNull()?.let { formatSize(it) } ?: "Unknown",
                icon = getIconForMimeType(fileInfo.mimeType),
                mimeType = fileInfo.mimeType,
                isDone = false, isActive = false, progress = 0f
            )
        } ?: emptyList()
    }

    val btIncomingMimeType by btViewModel.incomingMimeTypeState.collectAsState()

    // If there is no active file being broadcasted yet, show an empty list or the mock list with updated state
    // We can map the urisToShare from the state to display all files that were selected.
    val displayFiles = if (isSender) {
        state.urisToShare.mapIndexed { index, uri ->
            val currentIndex = if (method == "bt") btCurrFileCount else state.currentFileIndex
            val fileIsDone = isDone || index < currentIndex
            val fileIsActive = !fileIsDone && index == currentIndex
            val fileIsFailed = (progress < 0f && fileIsActive) || (transferFailed && !fileIsDone)
            
            val fileInfo = state.fileInfos.getOrNull(index)
            val name = fileInfo?.fileName ?: uri.lastPathSegment ?: "File ${index + 1}"
            val sizeStr = fileInfo?.size?.toLongOrNull()?.let { formatSize(it) } ?: "Unknown"

            TransferFile(
                name = name,
                size = sizeStr,
                icon = getIconForMimeType(fileInfo?.mimeType),
                mimeType = fileInfo?.mimeType,
                isDone = fileIsDone,
                isActive = fileIsActive && !transferFailed,
                progress = if (fileIsActive && !transferFailed) progress else if (fileIsDone) 100f else 0f,
                isFailed = fileIsFailed
            )
        }
    } else {
        receiverFilesBase.mapIndexed { index, file -> 
            val currentIndex = if (method == "bt") btCurrFileCount else state.currentFileIndex
            val fileIsDone = isDone || index < currentIndex
            val fileIsActive = !fileIsDone && index == currentIndex
            val fileIsFailed = (progress < 0f && fileIsActive) || (transferFailed && !fileIsDone)
            
            file.copy(
                isDone = fileIsDone,
                isActive = fileIsActive && !transferFailed,
                progress = if (fileIsActive && !transferFailed) progress else if (fileIsDone) 100f else 0f,
                isFailed = fileIsFailed
            )
        }.ifEmpty {
            listOf(
                TransferFile(
                    name = "Waiting for files...",
                    size = "0 B",
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    mimeType = null,
                    isDone = false,
                    isActive = true,
                    progress = 0f
                )
            )
        }
    }

    com.invincible.jedishare.ui.components.RequireHardware(
        requireWifi = method == "wifi",
        requireBluetooth = method == "bt"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
        ) {
        BackBar(title = "Transfer Progress", onBack = null)

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
                    .background(if (transferFailed) colors.red.copy(alpha = 0.15f) else if (isDone) colors.green.copy(alpha = 0.15f) else colors.red.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (transferFailed) Icons.Default.Error 
                    else if (isDone) Icons.Default.Check 
                    else Icons.AutoMirrored.Filled.Send, 
                    contentDescription = null, 
                    tint = if (transferFailed) colors.red else if (isDone) colors.green else colors.red, 
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(if (transferFailed) "Transfer Failed" else if (isDone) "Transfer Complete" else "Transferring files…", style = MaterialTheme.typography.caption, color = colors.mutedFg)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    btConnectedDeviceName ?: state.connectedDeviceName ?: "Unknown Device",
                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                    color = colors.black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(8.dp).background(if (isConnected) colors.green else colors.red, CircleShape))
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
            itemsIndexed(displayFiles) { index, file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val baseTint = when {
                            file.mimeType?.startsWith("image/") == true || file.mimeType?.startsWith("audio/") == true -> colors.red
                            file.mimeType?.startsWith("video/") == true -> colors.green
                            else -> Color(0xFF1976D2) // Blue for docs/others
                        }
                        
                        val iconBg = if (file.isDone) baseTint.copy(alpha = 0.1f) else if (file.isActive) baseTint.copy(alpha = 0.2f) else Color(0xFFF0F0F0)
                        val iconTint = if (file.isDone || file.isActive) baseTint else colors.mutedFg

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
                        if (file.isFailed) {
                            Icon(Icons.Default.Cancel, contentDescription = "Failed", tint = colors.red, modifier = Modifier.size(18.dp))
                        } else if (file.isDone) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                        } else if (file.isActive) {
                            Text(text = "${file.progress.toInt()}%", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold), color = colors.red)
                        } else {
                            Box(modifier = Modifier.size(18.dp).border(2.dp, colors.mutedFg, CircleShape))
                        }
                    }

                    if (file.isActive && !file.isDone) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = file.progress / 100f,
                            color = colors.red,
                            backgroundColor = colors.red.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                    }
                }
                
                if (index < displayFiles.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = {
                    if (isDone) {
                        navigatingAway = true
                        btViewModel.disconnectFromDevice()
                        btViewModel.resetTransferState()
                        transferViewModel.resetTransfer()
                        onNavigateToScreen("home")
                    } else {
                        btViewModel.disconnectFromDevice()
                        onBack()
                    }
                },
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
                val showHome = isDone || navigatingAway
                Icon(if (showHome) Icons.Default.Home else Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showHome) "Go Home" else "Disconnect", fontWeight = FontWeight.SemiBold)
            }
            }
        }
    }
}
