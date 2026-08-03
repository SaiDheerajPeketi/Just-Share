package com.invincible.jedishare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.invincible.jedishare.R
import com.invincible.jedishare.data.UserPreferencesDataStore
import com.invincible.jedishare.ui.theme.JediShareTheme

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
            onNavigateNext(isFirstLaunch!!)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_mark),
                contentDescription = "Just Share",
                modifier = Modifier
                    .size(144.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Just Share",
                style = MaterialTheme.typography.h3,
                fontWeight = FontWeight.Bold,
                color = colors.black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fast, private file sharing",
                style = MaterialTheme.typography.body2,
                color = colors.mutedFg
            )
        }
    }
}
