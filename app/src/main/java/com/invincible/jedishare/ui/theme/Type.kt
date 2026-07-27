package com.invincible.jedishare.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// We will use the system sans-serif (which defaults to Roboto on Android)
val RobotoSansSerif = FontFamily.SansSerif

// Define typography to match Figma / Tailwind styles
val Typography = Typography(
    defaultFontFamily = RobotoSansSerif,
    
    // h1: text-4xl (36sp), font-black
    h1 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 40.sp
    ),
    // h2: text-2xl (24sp), font-black
    h2 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    // h3: text-xl (20sp), font-bold
    h3 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    // body1: text-base (16sp), font-medium
    body1 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // body2: text-sm (14sp), font-medium
    body2 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // button: text-base (16sp), font-semibold
    button = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // caption: text-xs (12sp)
    caption = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)