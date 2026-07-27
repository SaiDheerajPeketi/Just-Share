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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun HomeScreen(
    transferViewModel: TransferViewModel,
    onNavigateToNavRoute: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    var transferMethod by remember { mutableStateOf("bt") }
    
    val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (currentHour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = greeting, style = MaterialTheme.typography.body2, color = colors.mutedFg)
                    Text(text = "Just Share", style = MaterialTheme.typography.h1, color = colors.black)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(colors.lightRed, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigateToScreen("scan-qr") }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = "Scan QR", tint = colors.red, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Toggle
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
                            ) { transferMethod = "bt" }
                            .background(if (transferMethod == "bt") colors.red else Color.Transparent, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Bluetooth", color = if (transferMethod == "bt") Color.White else colors.black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { transferMethod = "wifi" }
                            .background(if (transferMethod == "wifi") colors.red else Color.Transparent, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Wi-Fi Direct", color = if (transferMethod == "wifi") Color.White else colors.black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // SEND Card
            CardButton(
                onClick = { 
                    transferViewModel.setMethod(transferMethod)
                    onNavigateToScreen("select-files/$transferMethod") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = colors.red.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(32.dp))
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
                        tint = Color.White, 
                        modifier = Modifier.size(32.dp).offset(y = upArrowOffset.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "SEND", style = MaterialTheme.typography.h2.copy(fontWeight = FontWeight.Bold), color = Color.White, letterSpacing = 1.sp)
                    Text(text = "Share files with nearby devices", style = MaterialTheme.typography.body2, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RECEIVE Card
            CardButton(
                onClick = { 
                    transferViewModel.setMethod(transferMethod)
                    onNavigateToScreen("discover-$transferMethod") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(32.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.cardBg)
                        .border(2.dp, colors.red, RoundedCornerShape(32.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ArrowDownward, 
                        contentDescription = null, 
                        tint = colors.red, 
                        modifier = Modifier.size(32.dp).offset(y = downArrowOffset.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "RECEIVE", style = MaterialTheme.typography.h2.copy(fontWeight = FontWeight.Bold), color = colors.red, letterSpacing = 1.sp)
                    Text(text = "Accept files from nearby devices", style = MaterialTheme.typography.body2, color = colors.mutedFg, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigateToScreen("scan-qr") }
                        )
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("QR Connect", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black, maxLines = 1)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .clickable { }
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Mode", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black, maxLines = 1)
                }
            }
        }

        BottomNav(activeRoute = "home", onNavigate = onNavigateToNavRoute)
    }
}
