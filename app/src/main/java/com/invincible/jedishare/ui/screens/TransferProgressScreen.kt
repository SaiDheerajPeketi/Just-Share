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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
    val mimeType: String?,
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
    

    val manifest = if (method == "wifi") state.fileInfos.ifEmpty { null } else btIncomingManifest

    val isConnected = if (method == "bt") btState.isConnected else wifiState.isConnected
    val transferFailed = !isDone && (progress < 0f || (!isConnected && !state.hasTransferStarted))

    LaunchedEffect(isDone, isConnected) {
        if (isDone && !isSender) {
            kotlinx.coroutines.delay(1500) // Small delay to let user see 100% completion
            navigatingAway = true
            if (method == "bt") {
                btViewModel.disconnectFromDevice()
                btViewModel.resetTransferState()
            } else if (method == "wifi") {
                wifiViewModel.disconnectP2P()
            }
            transferViewModel.resetTransfer()
            onNavigateToScreen("home")
        } else if (isDone && isSender && !isConnected) {
            // Sender navigates home when transfer is done and receiver disconnects
            kotlinx.coroutines.delay(1500)
            navigatingAway = true
            if (method == "bt") {
                btViewModel.disconnectFromDevice()
                btViewModel.resetTransferState()
            } else if (method == "wifi") {
                wifiViewModel.disconnectP2P()
            }
            transferViewModel.resetTransfer()
            onNavigateToScreen("home")
        }
    }

    val receiverFilesBase = remember(manifest) {
        manifest?.map { fileInfo ->
            TransferFile(
                name = fileInfo.fileName ?: "Unknown",
                size = fileInfo.size?.toLongOrNull()?.let { formatSize(it) } ?: "Unknown",
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
            AnimatedTransferProgressIndicator(
                isTransferring = isConnected || state.hasTransferStarted,
                isDone = isDone,
                isFailed = transferFailed,
                isSender = isSender
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(if (transferFailed) "Transfer Failed" else if (isDone) "Transfer Complete" else "Transferring files…", style = MaterialTheme.typography.caption, color = colors.mutedFg)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = btConnectedDeviceName ?: state.connectedDeviceName ?: "Unknown Device",
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(8.dp).background(if (isConnected || state.hasTransferStarted) colors.green else colors.red, CircleShape))
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
                        com.invincible.jedishare.ui.components.MimeTypeIcon(
                            mimeType = file.mimeType,
                            modifier = Modifier.size(36.dp),
                            isActive = file.isActive,
                            isDone = file.isDone
                        )
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
                            backgroundColor = colors.border,
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
                        if (method == "bt") {
                            btViewModel.disconnectFromDevice()
                            btViewModel.resetTransferState()
                        } else if (method == "wifi") {
                            wifiViewModel.disconnectP2P()
                        }
                        transferViewModel.resetTransfer()
                        onNavigateToScreen("home")
                    } else {
                        if (method == "bt") {
                            btViewModel.disconnectFromDevice()
                        } else if (method == "wifi") {
                            wifiViewModel.disconnectP2P()
                        }
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.red,
                    contentColor = colors.white
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

@Composable
fun AnimatedTransferProgressIndicator(
    isTransferring: Boolean,
    isDone: Boolean,
    isFailed: Boolean,
    isSender: Boolean
) {
    val colors = JediShareTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "ripple_transition")

    val rippleScale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_scale"
    )
    val rippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1_alpha"
    )

    val rippleScale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_scale"
    )
    val rippleAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_alpha"
    )
    
    val arrowOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSender) -8f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_offset_y"
    )

    val baseColor = if (isFailed) colors.red else if (isDone) colors.green else colors.red 
    val iconColor = colors.white

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isTransferring && !isDone && !isFailed) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(rippleScale1)
                    .background(baseColor.copy(alpha = rippleAlpha1), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(rippleScale2)
                    .background(baseColor.copy(alpha = rippleAlpha2), CircleShape)
            )
        }
        
        val animatedScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isDone || isFailed) 1.2f else 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        )
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(animatedScale)
                .shadow(16.dp, CircleShape, spotColor = baseColor.copy(alpha = 0.5f))
                .background(baseColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isFailed) Icons.Default.Error 
                else if (isDone) Icons.Default.Check 
                else if (isSender) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, 
                contentDescription = null, 
                tint = iconColor, 
                modifier = Modifier
                    .size(40.dp)
                    .offset(y = if (isTransferring && !isDone && !isFailed) arrowOffsetY.dp else 0.dp)
            )
        }
    }
}
