package com.invincible.jedishare.ui.screens

import com.invincible.jedishare.ui.components.BackBar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun ScanQrScreen(onBack: () -> Unit) {
    val colors = JediShareTheme.colors
    var selectedTab by remember { mutableStateOf("scan") } // "scan" or "my_code"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(title = "Scan to Pair", onBack = onBack)

        Spacer(modifier = Modifier.height(24.dp))

        // Segmented Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedTab = "scan" }
                        .background(if (selectedTab == "scan") colors.lightRed else Color.Transparent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Scan QR", color = if (selectedTab == "scan") colors.red else colors.mutedFg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedTab = "my_code" }
                        .background(if (selectedTab == "my_code") colors.lightRed else Color.Transparent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("My Code", color = if (selectedTab == "my_code") colors.red else colors.mutedFg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Camera Preview Box
        if (selectedTab == "scan") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .aspectRatio(1f) // Square
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color(0xFF424242)), // Dummy camera feed background
                contentAlignment = Alignment.Center
            ) {
                // Red scanning frame overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .border(4.dp, colors.red.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                )
                // Frame Corners (Visual only)
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
                    val cornerLength = 30.dp.toPx()
                    val stroke = 6.dp.toPx()
                    val color = colors.red
                    val cornerRadius = 24.dp.toPx()
                    // Top Left
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, cornerRadius), androidx.compose.ui.geometry.Offset(0f, cornerLength), strokeWidth = stroke)
                    drawLine(color, androidx.compose.ui.geometry.Offset(cornerRadius, 0f), androidx.compose.ui.geometry.Offset(cornerLength, 0f), strokeWidth = stroke)
                    drawArc(color, 0f, 0f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(0f,0f), size = androidx.compose.ui.geometry.Size(cornerRadius*2, cornerRadius*2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                    // Simplification: We'll just rely on the semi-transparent border above, which looks close enough to the UI, 
                    // and I'll clear the canvas lines to keep it simple and clean.
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Align the QR code within the frame\nto connect instantly.",
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium, lineHeight = 24.sp),
                color = Color(0xFF5A4A45),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Flashlight button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFF5F5F5), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Toggle Flashlight */ }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FlashlightOn, contentDescription = "Flashlight", tint = colors.black, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
        } else {
            // "My Code" placeholder view
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Your QR Code goes here", color = colors.mutedFg)
            }
        }
    }
}
