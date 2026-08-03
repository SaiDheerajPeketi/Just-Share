package com.invincible.jedishare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invincible.jedishare.domain.altersend.AlterSendConnectionPhase
import com.invincible.jedishare.domain.altersend.AlterSendRole
import com.invincible.jedishare.presentation.AlterSendViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.components.PillButtonVariant
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

@Composable
fun RemoteTransferProgressScreen(
    viewModel: AlterSendViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit
) {
    BackHandler {
        // Keep transfer screen behavior aligned with Bluetooth/Wi-Fi progress.
    }

    val colors = JediShareTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var navigatingAway by remember { mutableStateOf(false) }

    val isSender = state.role == AlterSendRole.Sender
    val isDone = navigatingAway || state.phase == AlterSendConnectionPhase.Complete
    val isFailed = state.phase == AlterSendConnectionPhase.Failed || state.phase == AlterSendConnectionPhase.Cancelled
    val isTransferring = state.phase == AlterSendConnectionPhase.Hosting ||
        state.phase == AlterSendConnectionPhase.Joining ||
        state.phase == AlterSendConnectionPhase.Connecting ||
        state.phase == AlterSendConnectionPhase.Connected ||
        state.phase == AlterSendConnectionPhase.IncomingOffer ||
        state.phase == AlterSendConnectionPhase.Transferring

    LaunchedEffect(isDone) {
        if (isDone) {
            delay(1500)
            navigatingAway = true
            viewModel.reset()
            onNavigateHome()
        }
    }

    val activeIndex = state.progress?.fileId?.let { id ->
        state.offers.indexOfFirst { it.id == id }
    } ?: 0

    val files = state.offers.mapIndexed { index, offer ->
        val isActive = !isDone && !isFailed && index == activeIndex &&
            (state.phase == AlterSendConnectionPhase.Transferring || state.phase == AlterSendConnectionPhase.Connected)
        val isFileDone = isDone || index < activeIndex || (isActive && (state.progress?.percent ?: 0f) >= 100f)
        TransferFile(
            name = offer.name,
            size = formatSize(offer.sizeBytes),
            mimeType = offer.mimeType,
            isDone = isFileDone,
            isActive = isActive,
            progress = if (isActive) state.progress?.percent ?: 0f else if (isFileDone) 100f else 0f,
            isFailed = isFailed && !isFileDone
        )
    }.ifEmpty {
        listOf(
            TransferFile(
                name = if (state.role == AlterSendRole.Receiver) "Waiting for files..." else "Preparing transfer...",
                size = "0 B",
                mimeType = null,
                isDone = false,
                isActive = !isFailed,
                progress = 0f,
                isFailed = isFailed
            )
        )
    }

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
                isTransferring = isTransferring,
                isDone = isDone,
                isFailed = isFailed,
                isSender = isSender
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                when {
                    isFailed -> state.errorMessage ?: "Transfer Failed"
                    isDone -> "Transfer Complete"
                    state.phase == AlterSendConnectionPhase.Hosting -> "Waiting for receiver..."
                    state.phase == AlterSendConnectionPhase.IncomingOffer -> "Incoming encrypted files"
                    else -> "Transferring files..."
                },
                style = MaterialTheme.typography.caption,
                color = colors.mutedFg
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.remoteDeviceName ?: "Remote Transfer peer",
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(8.dp).background(if (!isFailed) colors.green else colors.red, CircleShape))
            }
        }

        state.topicHex?.takeIf { isSender && it.startsWith("JSAS") }?.let { code ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text("Connection Code", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(code, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), color = colors.red)
                }
                Spacer(modifier = Modifier.height(12.dp))
                PillButton(
                    label = "Copy Code",
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    variant = PillButtonVariant.OUTLINE,
                    size = PillButtonSize.MD,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                RemoteTransferQrCode(content = code, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.cardBg)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
            contentPadding = PaddingValues(vertical = 0.dp)
        ) {
            itemsIndexed(files) { index, file ->
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
                                if (file.isActive || file.isDone) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = colors.green, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(file.size, style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), color = colors.mutedFg)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (file.isFailed) {
                            Icon(Icons.Default.Cancel, contentDescription = "Failed", tint = colors.red, modifier = Modifier.size(18.dp))
                        } else if (file.isDone) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                        } else if (file.isActive) {
                            Text("${file.progress.toInt()}%", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold), color = colors.red)
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
                if (index < files.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                }
            }
        }

        if (state.phase == AlterSendConnectionPhase.IncomingOffer && state.role == AlterSendRole.Receiver) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                PillButton(
                    label = "Cancel",
                    onClick = viewModel::rejectIncomingTransfer,
                    variant = PillButtonVariant.OUTLINE,
                    size = PillButtonSize.MD,
                    modifier = Modifier.weight(1f)
                )
                PillButton(
                    label = "Accept",
                    onClick = viewModel::acceptIncomingTransfer,
                    size = PillButtonSize.MD,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Button(
                onClick = {
                    navigatingAway = true
                    viewModel.reset()
                    onNavigateHome()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.red,
                    contentColor = colors.white
                ),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Icon(if (isDone || isFailed) Icons.Default.Home else Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDone || isFailed) "Go Home" else "Cancel Transfer", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
