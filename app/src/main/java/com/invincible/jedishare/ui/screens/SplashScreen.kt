package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.theme.JediShareTheme

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    val colors = JediShareTheme.colors
    
    val gradientColors = if (colors.isLight) {
        listOf(colors.surface, Color(0xFFFFF0F0))
    } else {
        listOf(colors.surface, Color(0xFF1A0505))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(gradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.red, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = buildAnnotatedString {
                    append("Welcome to\n")
                    withStyle(style = SpanStyle(color = colors.red)) {
                        append("Just Share")
                    }
                },
                color = colors.black,
                style = MaterialTheme.typography.h1.copy(fontSize = 36.sp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            // Placeholder for Lottie
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(colors.red.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = colors.red,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Transfer files instantly — no internet needed",
                color = colors.black,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Share photos, videos, documents, and audio files via Bluetooth & Wi-Fi Direct at blazing speeds.",
                color = colors.mutedFg,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Indicator bar
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.width(24.dp).height(6.dp).background(colors.red, CircleShape))
                Box(modifier = Modifier.width(8.dp).height(6.dp).background(colors.lightRed, CircleShape))
                Box(modifier = Modifier.width(8.dp).height(6.dp).background(Color(0xFF666666), CircleShape))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            PillButton(
                label = "Continue",
                onClick = onContinue,
                size = PillButtonSize.LG,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "By continuing you agree to our Terms & Privacy Policy",
                color = colors.mutedFg,
                style = MaterialTheme.typography.caption,
                textAlign = TextAlign.Center
            )
        }
    }
}
