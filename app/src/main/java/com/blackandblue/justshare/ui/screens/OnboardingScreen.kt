package com.blackandblue.justshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackandblue.justshare.R
import com.blackandblue.justshare.ui.components.PillButton
import com.blackandblue.justshare.ui.components.PillButtonSize
import com.blackandblue.justshare.ui.theme.JediShareTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val colors = JediShareTheme.colors
    
    val gradientColors = if (colors.isLight) {
        listOf(colors.surface, colors.cardBg)
    } else {
        listOf(colors.surface, colors.cardBg)
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
            Image(
                painter = painterResource(R.drawable.app_logo_full),
                contentDescription = "Just Share logo",
                modifier = Modifier.size(160.dp)
            )
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
                Box(modifier = Modifier.width(8.dp).height(6.dp).background(colors.red.copy(alpha = 0.5f), CircleShape))
                Box(modifier = Modifier.width(8.dp).height(6.dp).background(colors.mutedFg, CircleShape))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val dataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.blackandblue.justshare.di.DataStoreEntryPoint::class.java
            ).userPreferencesDataStore()

            PillButton(
                label = "Continue",
                onClick = {
                    scope.launch {
                        dataStore.markFirstLaunchComplete()
                    }
                    onContinue()
                },
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
