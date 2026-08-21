package com.blackandblue.justshare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.blackandblue.justshare.R
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.ui.theme.JediShareTheme

@Composable
fun SplashScreen(
    onNavigateNext: (Boolean) -> Unit
) {
    val colors = JediShareTheme.colors
    val context = LocalContext.current
    val dataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.blackandblue.justshare.di.DataStoreEntryPoint::class.java
    ).userPreferencesDataStore()
    
    val isFirstLaunch by dataStore.isFirstLaunch.collectAsState(initial = null)

    LaunchedEffect(isFirstLaunch) {
        if (isFirstLaunch != null) {
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
            contentDescription = "Just Share",
            modifier = Modifier
                .fillMaxSize(0.72f)
                .aspectRatio(1f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}
