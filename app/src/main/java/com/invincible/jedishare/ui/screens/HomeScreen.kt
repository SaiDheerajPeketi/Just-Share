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
fun HomeScreen(
    transferViewModel: TransferViewModel,
    onNavigateToNavRoute: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit
) {
    val colors = JediShareTheme.colors
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    val transferMethod = transferState.method

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
                        Icons.Default.Settings, 
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
            }

            BottomNav(activeRoute = "home", onNavigate = onNavigateToNavRoute)
        }
    }
}

