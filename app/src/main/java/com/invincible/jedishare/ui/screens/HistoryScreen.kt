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
import androidx.compose.material.icons.filled.ChevronLeft
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
                HistoryItem(1, "Project_Assets_v2.zip", "MacBook Pro", "Sent", "1.2 GB", "TODAY", "Wi-Fi", Icons.Default.InsertDriveFile),
                HistoryItem(2, "IMG_9823.heic", "Galaxy S23", "Received", "4.5 MB", "TODAY", "BT", Icons.Default.Image),
                HistoryItem(3, "Client_Presentation.mp4", "iPad Pro", "Sent", "850 MB", "YESTERDAY", "Wi-Fi", Icons.Default.Videocam),
                HistoryItem(4, "Q3_Reports", "Desktop-X82", "Received", "120 MB", "YESTERDAY", "Wi-Fi", Icons.Default.InsertDriveFile)
            )
        )
    }

    val groupedItems = items.groupBy { it.date }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onNavigateToNavRoute("home") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = colors.black)
            }
            Text("Transfer History", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold), color = colors.black)
            Text(
                text = "Clear All", 
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), 
                color = colors.red,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { items = emptyList() }
                )
            )
        }

        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    groupedItems.forEach { (dateHeader, dateItems) ->
                        item {
                            Column {
                                Text(
                                    text = dateHeader,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.mutedFg,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    dateItems.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(colors.cardBg, RoundedCornerShape(16.dp))
                                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(if (item.dir == "Sent") colors.lightRed else Color(0xFFF5F5F5), RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon, 
                                                    contentDescription = null, 
                                                    tint = if (item.dir == "Sent") colors.red else colors.mutedFg, 
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name, 
                                                    style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.SemiBold), 
                                                    color = colors.black,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (item.dir == "Sent") "Sent to ${item.peer}" else "Received from ${item.peer}", 
                                                    fontSize = 13.sp, 
                                                    color = colors.mutedFg,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(horizontalAlignment = Alignment.End) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFF5F5F5), RoundedCornerShape(50))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = item.method, 
                                                        fontSize = 10.sp, 
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF555555)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = item.size, 
                                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold), 
                                                    color = colors.black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BottomNav(activeRoute = "history", onNavigate = onNavigateToNavRoute)
    }
}
