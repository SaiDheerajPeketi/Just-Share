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
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

data class TransferFile(
    val name: String,
    val size: String,
    val icon: ImageVector,
    val isDone: Boolean,
    val isActive: Boolean
)

@Composable
fun TransferProgressScreen(
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var progress by remember { mutableStateOf(42f) }

    LaunchedEffect(Unit) {
        while (progress < 100f) {
            delay(200)
            progress += 2f
        }
    }

    val files = listOf(
        TransferFile("vacation_beach.jpg", "4.2 MB", Icons.Default.Image, isDone = true, isActive = false),
        TransferFile("family_video.mp4", "128 MB", Icons.Default.Videocam, isDone = false, isActive = true),
        TransferFile("report_q4.pdf", "2.8 MB", Icons.Default.InsertDriveFile, isDone = false, isActive = false),
        TransferFile("podcast_ep12.mp3", "34 MB", Icons.Default.MusicNote, isDone = false, isActive = false)
    )

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
                Icon(Icons.Default.Send, contentDescription = null, tint = colors.red, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Transferring files…", style = MaterialTheme.typography.caption, color = colors.mutedFg)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Marcus's Galaxy S24", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), color = colors.black)
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

                    if (file.isActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            color = colors.red,
                            backgroundColor = colors.lightRed,
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
            OutlinedButton(
                onClick = { onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, colors.darkRed),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.darkRed,
                    backgroundColor = Color.Transparent
                )
            ) {
                Text("Disconnect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
