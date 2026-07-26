package com.invincible.jedishare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.presentation.SettingsViewModel
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.Roboto
import dagger.hilt.android.AndroidEntryPoint

/**
 * Settings screen backed by [SettingsViewModel] + DataStore.
 *
 * Was previously a stub showing only NavBar().
 * Now provides persisted preferences for:
 *  - Dark mode toggle
 *  - Default transfer method (Bluetooth / WiFi-Direct)
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<SettingsViewModel>()
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: SettingsViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val defaultMethod by viewModel.defaultTransferMethod.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Settings",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                fontFamily = Roboto
            )

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // ── Dark Mode ──────────────────────────────────────────────────────
            SettingRow(title = "Dark Mode", subtitle = "Switch to dark theme") {
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = viewModel::toggleDarkMode,
                    colors = SwitchDefaults.colors(checkedThumbColor = MyRed)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Default Transfer Method ────────────────────────────────────────
            Text(
                text = "Default Transfer Method",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = Roboto
            )
            Text(
                text = "Used when launching from share sheet",
                fontSize = 13.sp,
                color = Color.Gray,
                fontFamily = Roboto
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Bluetooth", "WiFi-Direct").forEach { method ->
                    val isSelected = defaultMethod == method
                    Button(
                        onClick = { viewModel.setDefaultTransferMethod(method) },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isSelected) MyRed else MyRedSecondaryLight
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = method,
                            color = if (isSelected) Color.White else Color.DarkGray,
                            fontSize = 14.sp,
                            fontFamily = Roboto
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── App Version info ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MyRedSecondaryLight)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Just Share",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = Roboto
                    )
                    Text(
                        text = "Version 1.0 · Built with ♥",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontFamily = Roboto
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            NavBar()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = Roboto)
            Text(text = subtitle, fontSize = 13.sp, color = Color.Gray, fontFamily = Roboto)
        }
        trailing()
    }
}
