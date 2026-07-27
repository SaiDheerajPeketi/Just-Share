package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val colors = JediShareTheme.colors
    val permissions = listOf(
        Triple("Bluetooth", "Find & connect nearby devices", Icons.Default.Bluetooth),
        Triple("Wi-Fi Direct", "High-speed peer-to-peer transfers", Icons.Default.Wifi),
        Triple("Storage", "Read & save transferred files", Icons.Default.Image),
        Triple("Notifications", "Transfer progress updates", Icons.Default.Notifications)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(colors.red.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colors.red,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "A Few Permissions",
                color = colors.black,
                style = MaterialTheme.typography.h1.copy(fontSize = 30.sp, fontWeight = FontWeight.Black),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Just Share needs these to discover devices and transfer your files securely — all locally, never to the cloud.",
                color = colors.mutedFg,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            permissions.forEach { perm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(colors.lightRed, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(perm.third, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = perm.first, style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = colors.black)
                        Text(text = perm.second, style = MaterialTheme.typography.caption.copy(fontSize = 12.sp), color = colors.mutedFg)
                    }
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp)) {
            PillButton(
                label = "Grant Permissions",
                onClick = onContinue,
                size = PillButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
