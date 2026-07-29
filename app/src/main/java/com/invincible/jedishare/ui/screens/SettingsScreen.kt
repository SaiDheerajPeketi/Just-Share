package com.invincible.jedishare.ui.screens

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invincible.jedishare.ui.components.BottomNav
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToNavRoute: (String) -> Unit,
    transferViewModel: com.invincible.jedishare.presentation.TransferViewModel
) {
    val colors = JediShareTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val dataStore = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.invincible.jedishare.di.DataStoreEntryPoint::class.java
        ).userPreferencesDataStore()
    }
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode = !JediShareTheme.colors.isLight
    

    val savedTransferMethod by dataStore.defaultTransferMethod.collectAsStateWithLifecycle(initialValue = "wifi")
    var encRequired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.h3, color = colors.black)
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Appearance Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dark Mode", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomToggle(
                        on = isDarkMode,
                        onChange = {
                            coroutineScope.launch {
                                dataStore.setDarkMode(!isDarkMode)
                            }
                        },
                        trackOnColor = colors.red,
                        trackOffColor = colors.border,
                        thumbOnColor = Color.White,
                        thumbOffColor = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Transfer Defaults Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "TRANSFER DEFAULTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                
                // Bluetooth Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferViewModel.setMethod("bt", save = true) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Bluetooth", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = savedTransferMethod == "bt", color = colors.red)
                }
                
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
                
                // Wi-Fi Direct Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferViewModel.setMethod("wifi", save = true) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Wi-Fi Direct", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = savedTransferMethod == "wifi", color = colors.red)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Security Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = "SECURITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.mutedFg,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Always require encryption\nverification", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp), color = colors.black, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(16.dp))
                    CustomToggle(
                        on = encRequired,
                        onChange = { encRequired = !encRequired },
                        trackOnColor = colors.red, // App theme red track
                        trackOffColor = colors.border,
                        thumbOnColor = Color.White, // White thumb
                        thumbOffColor = Color.White,
                        thumbIcon = Icons.Default.Check,
                        thumbIconTint = colors.red
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Handle Trusted Devices */ }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Trusted Devices", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.mutedFg, modifier = Modifier.size(20.dp))
                }
            }
        }

        BottomNav(activeRoute = "settings", onNavigate = onNavigateToNavRoute)
    }
}

@Composable
fun CustomRadioButton(selected: Boolean, color: Color) {
    val colors = JediShareTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) color else colors.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun CustomToggle(
    on: Boolean, 
    onChange: () -> Unit,
    trackOnColor: Color,
    trackOffColor: Color,
    thumbOnColor: Color,
    thumbOffColor: Color,
    thumbIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    thumbIconTint: Color = Color.White
) {
    val animatedTrackColor by androidx.compose.animation.animateColorAsState(targetValue = if (on) trackOnColor else trackOffColor)
    val animatedThumbColor by androidx.compose.animation.animateColorAsState(targetValue = if (on) thumbOnColor else thumbOffColor)
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(targetValue = if (on) 24.dp else 2.dp)

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(animatedTrackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onChange
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .align(Alignment.CenterStart)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(animatedThumbColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (on && thumbIcon != null) {
                Icon(thumbIcon, contentDescription = null, tint = thumbIconTint, modifier = Modifier.size(14.dp))
            }
        }
    }
}
