package com.invincible.jedishare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

enum class NavRoute(val label: String, val icon: ImageVector, val route: String) {
    HOME("Transfer", Icons.Outlined.Send, "home"),
    HISTORY("History", Icons.Outlined.History, "history"),
    SETTINGS("Settings", Icons.Outlined.Settings, "settings")
}

@Composable
fun BottomNav(
    activeRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = JediShareTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.cardBg)
            .drawBehind {
                drawLine(
                    color = colors.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavRoute.values().forEach { navRoute ->
            val isActive = activeRoute == navRoute.route
            val color = if (isActive) colors.red else colors.mutedFg
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(navRoute.route) }
                    .padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = navRoute.icon,
                    contentDescription = navRoute.label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = navRoute.label,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
