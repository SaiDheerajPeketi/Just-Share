package com.invincible.jedishare.ui.theme

import timber.log.Timber

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

private val DarkColorPalette = darkColors(
    primary = DarkModeButtons,
    primaryVariant = Purple700,
    secondary = Teal200,
    background = DarkModeBackground,
    onSurface = Color.White,
)

private val LightColorPalette = lightColors(
//    primary = Purple500,
//    secondary = Teal200,
    primary = MyRed,
    primaryVariant = Purple700,
    secondary = MyRedSecondary,
    background = White,
    onSurface = Color.Black,

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */)

@Composable
fun JediShareTheme(content: @Composable () -> Unit) {
    Timber.d("Global - JediShareTheme called")
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val dataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.invincible.jedishare.di.DataStoreEntryPoint::class.java
    ).userPreferencesDataStore()
    
    val isDarkMode by dataStore.isDarkModeEnabled.collectAsStateWithLifecycle(initialValue = isSystemInDarkTheme())
    
    val colors = if (isDarkMode) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}