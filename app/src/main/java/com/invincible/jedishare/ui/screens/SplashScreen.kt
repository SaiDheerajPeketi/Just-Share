package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.invincible.jedishare.R
import com.invincible.jedishare.data.UserPreferencesDataStore
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: (Boolean) -> Unit
) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current
    val dataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.invincible.jedishare.di.DataStoreEntryPoint::class.java
    ).userPreferencesDataStore()
    
    val isFirstLaunch by dataStore.isFirstLaunch.collectAsState(initial = null)

    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch != null) {
            kotlinx.coroutines.delay(1500)
            onNavigateNext(isFirstLaunch!!)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo_full),
            contentDescription = "App Logo",
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1f) // Actually, the image is 1024x1024 so it's a square
        )
    }
}
