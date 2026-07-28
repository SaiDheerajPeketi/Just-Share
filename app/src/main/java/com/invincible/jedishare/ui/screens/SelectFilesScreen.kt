package com.invincible.jedishare.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.bytesToHumanReadableSize
import com.invincible.jedishare.presentation.SelectFileViewModel
import com.invincible.jedishare.presentation.TransferViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun SelectFilesScreen(
    method: String = "bt",
    transferViewModel: TransferViewModel,
    viewModel: SelectFileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var activeTab by rememberSaveable { mutableStateOf("docs") }
    
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    val tabs = listOf(
        Triple("images", "Images", Icons.Default.Image),
        Triple("videos", "Videos", Icons.Default.VideoLibrary),
        Triple("audio", "Audio", Icons.Default.LibraryMusic),
        Triple("docs", "Docs", Icons.Default.InsertDriveFile)
    )

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addUris(uris)
        }
    }

    fun launchPickerFor(tab: String) {
        val mimeType = when (tab) {
            "images" -> "image/*"
            "videos" -> "video/*"
            "audio" -> "audio/*"
            else -> "*/*"
        }
        pickerLauncher.launch(mimeType)
    }

    var hasLaunchedPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasLaunchedPicker) {
            hasLaunchedPicker = true
            launchPickerFor(activeTab)
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
        BackBar(title = "Select Files", onBack = onBack)

        // Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
                .drawBehind {
                    drawLine(
                        color = colors.border,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val isActive = activeTab == tab.first
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { 
                            activeTab = tab.first
                            launchPickerFor(activeTab)
                        }
                        .drawBehind {
                            if (isActive) {
                                drawLine(
                                    color = colors.red,
                                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(tab.third, contentDescription = null, tint = if (isActive) colors.red else colors.mutedFg, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = tab.second, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = if (isActive) colors.red else colors.mutedFg)
                    }
                }
            }
        }

        // List Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected Files (${selectedFiles.size})",
                style = MaterialTheme.typography.h2.copy(fontSize = 18.sp),
                color = colors.black
            )
            Button(
                onClick = { launchPickerFor(activeTab) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.cardBg,
                    contentColor = colors.red
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Files", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        // Selected Files List
        if (selectedFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = colors.mutedFg.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No files selected", color = colors.mutedFg, style = MaterialTheme.typography.body1)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(selectedFiles, key = { it.first.toString() }) { (uri, fileInfo) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBg)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            fileInfo.mimeType?.startsWith("image/") == true -> Icons.Default.Image
                            fileInfo.mimeType?.startsWith("video/") == true -> Icons.Default.VideoLibrary
                            fileInfo.mimeType?.startsWith("audio/") == true -> Icons.Default.LibraryMusic
                            else -> Icons.Default.InsertDriveFile
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(colors.red.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileInfo.fileName ?: "Unknown File",
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sizeStr = fileInfo.size?.toDoubleOrNull()?.let { bytesToHumanReadableSize(it) } ?: "Unknown Size"
                            Text(
                                text = sizeStr,
                                style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                                color = colors.mutedFg
                            )
                        }
                        IconButton(onClick = { viewModel.removeUri(uri) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = colors.mutedFg)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(16.dp)) {
            PillButton(
                label = if (selectedFiles.isNotEmpty()) "Send ${selectedFiles.size} file${if (selectedFiles.size > 1) "s" else ""}" else "Select files above",
                onClick = { 
                    transferViewModel.setMethod(method)
                    transferViewModel.setUris(selectedFiles.map { it.first })
                    onNavigateToScreen("discover-$method") 
                },
                disabled = selectedFiles.isEmpty(),
                size = PillButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
}
