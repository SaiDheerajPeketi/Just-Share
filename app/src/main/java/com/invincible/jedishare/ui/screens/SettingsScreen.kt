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
            Text(
                text = "APPEARANCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedFg,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Dark Mode", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomToggle(
                        on = isDarkMode,
                        onChange = {
                            coroutineScope.launch {
                                dataStore.setDarkMode(!isDarkMode)
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Transfer Section
            Text(
                text = "TRANSFER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedFg,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text(text = "Default Transfer Method", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black, modifier = Modifier.padding(bottom = 12.dp))
                
                // Bluetooth Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (transferMethod == "bluetooth") colors.lightRed else colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, if (transferMethod == "bluetooth") colors.red else colors.border, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferMethod = "bluetooth" }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = if (transferMethod == "bluetooth") colors.red else colors.mutedFg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Bluetooth", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium), color = if (transferMethod == "bluetooth") colors.red else colors.black, modifier = Modifier.weight(1f))
                    if (transferMethod == "bluetooth") {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.red, modifier = Modifier.size(16.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Wi-Fi Direct Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (transferMethod == "wifi") colors.lightRed else colors.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, if (transferMethod == "wifi") colors.red else colors.border, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { transferMethod = "wifi" }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = if (transferMethod == "wifi") colors.red else colors.mutedFg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Wi-Fi Direct", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium), color = if (transferMethod == "wifi") colors.red else colors.black, modifier = Modifier.weight(1f))
                    if (transferMethod == "wifi") {
                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.red, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Privacy & Security Section
            Text(
                text = "PRIVACY & SECURITY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedFg,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Always require encryption", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium), color = colors.black, modifier = Modifier.weight(1f))
                    CustomToggle(
                        on = encRequired,
                        onChange = { encRequired = !encRequired }
                    )
                }
            }
        }

        BottomNav(activeRoute = "settings", onNavigate = onNavigateToNavRoute)
    }
}

@Composable
fun CustomToggle(on: Boolean, onChange: () -> Unit) {
    val colors = JediShareTheme.colors
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) colors.red else Color(0xFFCCCCCC))
            .clickable(onClick = onChange),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
