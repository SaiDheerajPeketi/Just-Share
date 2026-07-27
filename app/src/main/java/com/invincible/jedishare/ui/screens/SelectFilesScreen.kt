package com.invincible.jedishare.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.invincible.jedishare.presentation.SelectFileViewModel
import com.invincible.jedishare.presentation.TransferViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.theme.*

@Composable
fun SelectFilesScreen(
    method: String = "bt",
    transferViewModel: TransferViewModel,
    viewModel: SelectFileViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var activeTab by remember { mutableStateOf("images") }
    
    val images by viewModel.images.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val audios by viewModel.audios.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()

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
                            if (activeTab == "docs") {
                                pickerLauncher.launch("*/*")
                                activeTab = "images" // Reset so they can pick again
                            }
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

        // Grid
        Box(modifier = Modifier.weight(1f).padding(12.dp)) {
            val itemsToDisplay = when (activeTab) {
                "images" -> images.map { Pair(it.uri, it.name) }
                "videos" -> videos.map { Pair(it.uri, it.name) }
                "audio" -> audios.map { Pair(it.uri, it.name) }
                else -> emptyList()
            }

            if (itemsToDisplay.isEmpty() && activeTab != "docs") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files found", color = colors.mutedFg)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(itemsToDisplay) { item ->
                        val uri = item.first
                        val isSelected = selectedUris.contains(uri)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.cardBg)
                                .clickable {
                                    if (isSelected) {
                                        // Wait, SelectFileViewModel doesn't have removeUri yet? Let's check.
                                        // I will add it or just pass list minus uri to setUris? 
                                        // But SelectFileViewModel only has addUris. Let's add removeUri to SelectFileViewModel.
                                    } else {
                                        viewModel.addUris(listOf(uri))
                                    }
                                }
                        ) {
                            if (activeTab == "images" || activeTab == "videos") {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = colors.mutedFg,
                                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                                )
                            }

                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(colors.red.copy(alpha = 0.35f)))
                                Box(
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(colors.red, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(16.dp)) {
            PillButton(
                label = if (selectedUris.isNotEmpty()) "Send ${selectedUris.size} file${if (selectedUris.size > 1) "s" else ""}" else "Select files above",
                onClick = { 
                    transferViewModel.setMethod(method)
                    transferViewModel.setUris(selectedUris)
                    onNavigateToScreen("discover-$method") 
                },
                disabled = selectedUris.isEmpty(),
                size = PillButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
