package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.presentation.HistoryViewModel
import com.invincible.jedishare.ui.components.BottomNav
import com.invincible.jedishare.ui.theme.JediShareTheme
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}

fun getIconForMimeType(mimeType: String?): ImageVector {
    return when {
        mimeType?.startsWith("image/") == true -> Icons.Default.Image
        mimeType?.startsWith("video/") == true -> Icons.Default.Videocam
        mimeType?.startsWith("audio/") == true -> Icons.Default.MusicNote
        else -> Icons.Default.InsertDriveFile
    }
}

fun formatDateRelative(ms: Long): String {
    val date = Date(ms)
    val now = Date()
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    // Very simple check for Today/Yesterday (for better logic, use Calendar)
    val diff = now.time - date.time
    val days = diff / (1000 * 60 * 60 * 24)
    
    return when (days) {
        0L -> "TODAY"
        1L -> "YESTERDAY"
        else -> format.format(date).uppercase(Locale.getDefault())
    }
}

private fun openFile(context: Context, item: TransferHistoryEntity) {
    if (item.contentUri == null) {
        Toast.makeText(context, "File location unknown", Toast.LENGTH_SHORT).show()
        return
    }
    
    val uri = Uri.parse(item.contentUri)
    
    // Check if it exists and is not in the trash
    var exists = false
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                exists = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val isTrashedIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.IS_TRASHED)
                    if (isTrashedIndex != -1) {
                        if (cursor.getInt(isTrashedIndex) == 1) {
                            exists = false
                        }
                    }
                }
            }
        }
        
        // Double check by trying to open it for reading
        if (exists) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {}
        }
    } catch (e: Exception) {
        exists = false
    }
    
    if (!exists) {
        Toast.makeText(context, "File no longer exists or was moved", Toast.LENGTH_SHORT).show()
        return
    }
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    try {
        context.startActivity(Intent.createChooser(intent, "Open File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToNavRoute: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current
    val historyItems by viewModel.history.collectAsState()

    val groupedItems = historyItems.groupBy { formatDateRelative(it.timestampMs) }

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
            Text("Transfer History", style = MaterialTheme.typography.h3, color = colors.black)
            Text(
                text = "Clear All", 
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), 
                color = colors.red,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.clearAll() }
                )
            )
        }

        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            if (historyItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(colors.cardBg, RoundedCornerShape(32.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("No Transfers Yet", style = MaterialTheme.typography.h2, color = colors.black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your file transfer history will appear here.",
                        style = MaterialTheme.typography.body2,
                        color = colors.mutedFg
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
                ) {
                    groupedItems.forEach { (date, itemsForDate) ->
                        item {
                            Text(
                                text = date,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.mutedFg,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp, top = 16.dp)
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(colors.cardBg)
                                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                            ) {
                                itemsForDate.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { openFile(context, item) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val baseTint = when {
                                            item.mimeType?.startsWith("image/") == true || item.mimeType?.startsWith("audio/") == true -> colors.red
                                            item.mimeType?.startsWith("video/") == true -> colors.green
                                            else -> Color(0xFF1976D2) // Blue for docs/others
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(baseTint.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                getIconForMimeType(item.mimeType), 
                                                contentDescription = null, 
                                                tint = baseTint, 
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.fileName,
                                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(text = if (item.isSender) "Sent to " else "From ", style = MaterialTheme.typography.caption, color = colors.mutedFg)
                                                Text(text = item.remoteDeviceName ?: "Unknown", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium), color = colors.black)
                                                Text(text = " • ${formatSize(item.fileSizeBytes)}", style = MaterialTheme.typography.caption, color = colors.mutedFg)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(
                                                modifier = Modifier.background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (item.transferMethod == "bt") Icons.Default.Bluetooth else Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = colors.mutedFg,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = if (item.transferMethod == "bt") "BT" else "Wi-Fi", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.mutedFg)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = colors.red.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable { viewModel.deleteEntry(item) }
                                            )
                                        }
                                    }
                                    if (index < itemsForDate.lastIndex) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BottomNav(
            activeRoute = "history",
            onNavigate = onNavigateToNavRoute
        )
    }
}
