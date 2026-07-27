package com.invincible.jedishare.ui.theme

import timber.log.Timber

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb

class CustomColors(
    val red: Color,
    val lightRed: Color,
    val darkRed: Color,
    val black: Color,
    val white: Color,
    val surface: Color,
    val cardBg: Color,
    val mutedFg: Color,
    val border: Color,
    val gold: Color,
    val green: Color,
    val isLight: Boolean
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        red = MyRed,
        lightRed = LightRed,
        darkRed = DarkRed,
        black = LightBlack,
        white = LightWhite,
        surface = LightSurface,
        cardBg = LightCardBg,
        mutedFg = LightMutedFg,
        border = LightBorder,
        gold = Gold,
        green = Green,
        isLight = true
    )
}

private val LightCustomColors = CustomColors(
    red = MyRed,
    lightRed = LightRed,
    darkRed = DarkRed,
    black = LightBlack,
    white = LightWhite,
    surface = LightSurface,
    cardBg = LightCardBg,
    mutedFg = LightMutedFg,
    border = LightBorder,
    gold = Gold,
    green = Green,
    isLight = true
)

private val DarkCustomColors = CustomColors(
    red = MyRed,
    lightRed = LightRed,
    darkRed = DarkRed,
    black = DarkBlack,
    white = DarkWhite,
    surface = DarkSurface,
    cardBg = DarkCardBg,
    mutedFg = DarkMutedFg,
    border = DarkBorder,
    gold = Gold,
    green = Green,
    isLight = false
)

private val DarkColorPalette = darkColors(
    primary = MyRed,
    primaryVariant = DarkRed,
    secondary = Gold,
    background = DarkSurface,
    surface = DarkCardBg,
    onPrimary = DarkWhite,
    onSecondary = DarkWhite,
    onBackground = DarkBlack,
    onSurface = DarkBlack,
)

private val LightColorPalette = lightColors(
    primary = MyRed,
    primaryVariant = DarkRed,
    secondary = Gold,
    background = LightSurface,
    surface = LightCardBg,
    onPrimary = LightWhite,
    onSecondary = LightWhite,
    onBackground = LightBlack,
    onSurface = LightBlack,
)

object JediShareTheme {
    val colors: CustomColors
        @Composable
        get() = LocalCustomColors.current
}

@Composable
fun JediShareTheme(content: @Composable () -> Unit) {
    Timber.d("Global - JediShareTheme called")
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val dataStore = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        com.invincible.jedishare.di.DataStoreEntryPoint::class.java
    ).userPreferencesDataStore()
    
    val isDarkMode by dataStore.isDarkModeEnabled.collectAsStateWithLifecycle(initialValue = isSystemInDarkTheme())
    
    val customColors = if (isDarkMode) DarkCustomColors else LightCustomColors
    val materialColors = if (isDarkMode) DarkColorPalette else LightColorPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = customColors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
        }
    }

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colors = materialColors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}