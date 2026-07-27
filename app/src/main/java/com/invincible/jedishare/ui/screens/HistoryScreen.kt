package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.InsertDriveFile
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
import com.invincible.jedishare.ui.components.BottomNav
import com.invincible.jedishare.ui.theme.JediShareTheme

data class HistoryItem(
    val id: Int,
    val name: String,
    val peer: String,
    val dir: String,
    val size: String,
    val date: String,
    val method: String,
    val icon: ImageVector
)

@Composable
fun HistoryScreen(
    onNavigateToNavRoute: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var items by remember { 
        mutableStateOf(
            listOf(
                HistoryItem(1, "vacation_beach.jpg", "Marcus's Galaxy S24", "Sent", "4.2 MB", "Today 14:32", "Wi-Fi", Icons.Default.Image),
                HistoryItem(2, "family_video.mp4", "Sarah's Pixel 8", "Received", "128 MB", "Today 11:05", "Bluetooth", Icons.Default.Videocam),
                HistoryItem(3, "report_q4.pdf", "Work MacBook Pro", "Sent", "2.8 MB", "Yesterday", "Wi-Fi", Icons.Default.InsertDriveFile),
                HistoryItem(4, "podcast_ep12.mp3", "Lena's iPhone 15", "Received", "34 MB", "Mon, Jul 21", "Bluetooth", Icons.Default.MusicNote)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Transfer History", style = MaterialTheme.typography.h3, color = colors.black)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { items = emptyList() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History", tint = colors.red, modifier = Modifier.size(24.dp))
            }
        }

        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(colors.lightRed, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = colors.red, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No transfer history yet", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Your transfers will appear here", fontSize = 14.sp, color = colors.mutedFg)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.cardBg, RoundedCornerShape(24.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                ) {
                    items(items.size) { index ->
                        val item = items[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (item.dir == "Sent") colors.lightRed else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = null, tint = if (item.dir == "Sent") colors.red else Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name, 
                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), 
                                    color = colors.black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.dir == "Sent") "↑ Sent to ${item.peer}" else "↓ Received from ${item.peer}", 
                                    fontSize = 12.sp, 
                                    color = colors.mutedFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = item.size, fontSize = 12.sp, color = colors.mutedFg)
                                    Text(text = "·", fontSize = 12.sp, color = colors.mutedFg)
                                    Text(text = item.date, fontSize = 12.sp, color = colors.mutedFg)
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                if (item.method == "Wi-Fi") Color(0xFFE3F2FD) else colors.lightRed, 
                                                RoundedCornerShape(percent = 50)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (item.method == "Wi-Fi") Icons.Default.Wifi else Icons.Default.Bluetooth, 
                                            contentDescription = null, 
                                            tint = if (item.method == "Wi-Fi") Color(0xFF1565C0) else colors.darkRed, 
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = item.method, 
                                            fontSize = 10.sp, 
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.method == "Wi-Fi") Color(0xFF1565C0) else colors.darkRed
                                        )
                                    }
                                }
                            }
                        }
                        if (index < items.lastIndex) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                        }
                    }
                }
            }
        }

        BottomNav(activeRoute = "history", onNavigate = onNavigateToNavRoute)
    }
}
