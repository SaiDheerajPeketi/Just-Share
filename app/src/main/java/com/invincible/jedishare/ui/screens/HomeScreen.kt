package com.invincible.jedishare.ui.screens

import com.invincible.jedishare.presentation.TransferViewModel
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.HistoryViewModel
import com.invincible.jedishare.ui.screens.formatSize
import com.invincible.jedishare.ui.screens.formatDateRelative
import com.invincible.jedishare.ui.screens.openFile
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.components.BottomNav
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun CardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "card_scale")

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun RecentFileItem(
    mimeType: String?,
    title: String,
    subtitle: String,
    isSender: Boolean,
    onClick: () -> Unit = {}
) {
    val colors = JediShareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.invincible.jedishare.ui.components.MimeTypeIcon(
            mimeType = mimeType,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp), color = colors.black)
            Text(subtitle, style = MaterialTheme.typography.body2, color = colors.mutedFg, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (isSender) colors.red.copy(alpha = 0.1f) else colors.cardBg, CircleShape)
                .border(if (isSender) 0.dp else 1.dp, if (isSender) Color.Transparent else colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSender) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, 
                contentDescription = if (isSender) "Sent" else "Received", 
                tint = if (isSender) colors.red else colors.mutedFg, 
                modifier = Modifier.size(14.dp)
            )
        }
    }
}


@Composable
fun HomeScreen(
    transferViewModel: TransferViewModel,
    historyViewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToNavRoute: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    val transferMethod = transferState.method
    val historyItems by historyViewModel.history.collectAsState()
    val recentItems = historyItems.take(3)

    val infiniteTransition = rememberInfiniteTransition(label = "arrow_anim")
    val upArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "up_arrow"
    )
    val downArrowOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "down_arrow"
    )

    com.invincible.jedishare.ui.components.RequireHardware(
        requireWifi = true,
        requireBluetooth = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 40.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Just Share", 
                        style = MaterialTheme.typography.h1.copy(fontWeight = FontWeight.Bold), 
                        color = colors.black
                    )
                    Icon(
                        Icons.Outlined.Settings, 
                        contentDescription = "Settings", 
                        tint = colors.mutedFg, 
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigateToScreen("settings") }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // SEND Card
                CardButton(
                    onClick = { 
                        onNavigateToScreen("select-files/$transferMethod") 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.red)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, 
                            contentDescription = null, 
                            tint = colors.white, 
                            modifier = Modifier.size(32.dp).offset(y = upArrowOffset.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Send", style = MaterialTheme.typography.h2.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp), color = colors.white)
                        Text(text = "Share files with nearby devices", style = MaterialTheme.typography.body2, color = colors.white.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // RECEIVE Card
                CardButton(
                    onClick = { 
                        transferViewModel.resetTransfer()
                        transferViewModel.setMethod(transferMethod)
                        onNavigateToScreen("discover-$transferMethod") 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.cardBg)
                            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward, 
                            contentDescription = null, 
                            tint = colors.black, 
                            modifier = Modifier.size(32.dp).offset(y = downArrowOffset.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Receive", style = MaterialTheme.typography.h2.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp), color = colors.black)
                        Text(text = "Accept files from nearby devices", style = MaterialTheme.typography.body2, color = colors.mutedFg, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                CardButton(
                    onClick = { onNavigateToScreen("select-files/altersend") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.cardBg)
                            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(colors.red.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = colors.red, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remote Transfer",
                                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                color = colors.black
                            )
                            Text(
                                text = "Encrypted internet transfer",
                                style = MaterialTheme.typography.body2,
                                color = colors.mutedFg,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        com.invincible.jedishare.ui.components.EncryptedBadge()
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp), color = colors.mutedFg)
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigateToScreen("history") }
                            )
                            .padding(8.dp)
                    ) {
                        Text("See all", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), color = colors.red)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (recentItems.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(colors.cardBg, RoundedCornerShape(24.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Transfers Yet", style = MaterialTheme.typography.h3.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = colors.black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Your file transfer history will appear here.",
                            style = MaterialTheme.typography.body2,
                            color = colors.mutedFg
                        )
                    }
                } else {
                    recentItems.forEach { item ->
                        RecentFileItem(
                            mimeType = item.mimeType,
                            title = item.fileName,
                            subtitle = "${formatSize(item.fileSizeBytes)} · ${formatDateRelative(item.timestampMs)}",
                            isSender = item.isSender,
                            onClick = { openFile(context, item) }
                        )
                    }
                }
            }
        }
    }
}
