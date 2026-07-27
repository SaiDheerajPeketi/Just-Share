package com.invincible.jedishare.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.StatusDot
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

@Composable
fun RadarAnim(scanning: Boolean) {
    val colors = JediShareTheme.colors
    val infiniteTransition = rememberInfiniteTransition()
    
    val size1 by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        if (scanning) {
            Box(
                modifier = Modifier
                    .size(size1.dp)
                    .alpha(alpha1)
                    .border(2.dp, colors.red, CircleShape)
            )
        }
        
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.red, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun DiscoverDevicesScreen(
    title: String,
    onBack: () -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(scanning) {
        if (scanning) {
            delay(3000)
            scanning = false
        }
    }

    val discovered = listOf(
        Pair("Marcus's Galaxy S24", "AC:DE:48:00:11:22"),
        Pair("Sarah's Pixel 8", "B4:F6:1C:33:44:55")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(
            title = title,
            onBack = onBack,
            rightEl = {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { scanning = true }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (scanning) colors.red else colors.mutedFg,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadarAnim(scanning = scanning)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (scanning) "Scanning for nearby devices…" else "Scan complete",
                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                    color = if (scanning) colors.red else colors.mutedFg
                )
            }

            Text(
                text = "AVAILABLE DEVICES (${discovered.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedFg,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                discovered.forEachIndexed { index, device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToScreen("transfer-progress") }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(colors.lightRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = device.first, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                            Text(text = device.second, style = MaterialTheme.typography.caption, color = colors.mutedFg)
                        }
                        StatusDot(status = "online")
                    }
                    if (index < discovered.lastIndex) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    }
                }
            }
        }
    }
}
