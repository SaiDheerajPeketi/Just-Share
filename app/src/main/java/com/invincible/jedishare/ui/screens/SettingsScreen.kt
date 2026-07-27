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
    onNavigateToNavRoute: (String) -> Unit
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
    val isDarkMode by dataStore.isDarkModeEnabled.collectAsStateWithLifecycle(initialValue = androidx.compose.foundation.isSystemInDarkTheme())
    
    var transferMethod by remember { mutableStateOf("bluetooth") }
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
                    color = Color(0xFF5A4A45),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, bottom = 16.dp)
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
                        trackOffColor = Color(0xFFE0E0E0),
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
                    color = Color(0xFF5A4A45),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, bottom = 16.dp)
                )
                
                // Bluetooth Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferMethod = "bluetooth" }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF5A4A45), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Bluetooth", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = transferMethod == "bluetooth", color = Color(0xFFB71C1C))
                }
                
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0xFFF0F0F0)))
                
                // Wi-Fi Direct Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferMethod = "wifi" }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF5A4A45), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Wi-Fi Direct", style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomRadioButton(selected = transferMethod == "wifi", color = Color(0xFFB71C1C))
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
                    color = Color(0xFF5A4A45),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, bottom = 16.dp)
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
                        trackOnColor = Color(0xFFB71C1C), // Red track
                        trackOffColor = Color(0xFFE0E0E0),
                        thumbOnColor = Color(0xFF2962FF), // Blue thumb
                        thumbOffColor = Color.White,
                        thumbIcon = Icons.Default.Check
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Color(0xFFF0F0F0)))
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
                    Icon(androidx.compose.material.icons.filled.Devices, contentDescription = null, tint = Color(0xFF5A4A45), modifier = Modifier.size(20.dp))
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
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) color else Color(0xFFBDBDBD), CircleShape),
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
    thumbIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) trackOnColor else trackOffColor)
            .clickable(onClick = onChange),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(if (on) thumbOnColor else thumbOffColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (on && thumbIcon != null) {
                Icon(thumbIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
