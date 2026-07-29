package com.invincible.jedishare.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RobotoSansSerif = FontFamily.SansSerif

// Three weights only: Regular for reading, Medium for emphasis, SemiBold/Bold
// reserved for the one or two headlines per screen that should actually stand
// out. Black weight is gone entirely — it renders as visually shouting no
// matter what it's paired with.
val Typography = Typography(
    defaultFontFamily = RobotoSansSerif,

    // h1: hero headline (onboarding, empty states) — strong but not Black
    h1 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    // h2: screen title ("Settings", "Transfer History")
    h2 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // h3: section header within a screen
    h3 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    // body1: primary reading text — regular weight, this is the fix
    body1 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // body2: secondary text, list metadata — regular weight
    body2 = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // button: medium is enough at this size, heavy letter-spacing reads as
    // aggressive when combined with a bold weight
    button = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    // caption: unchanged, this one was already calm
    caption = TextStyle(
        fontFamily = RobotoSansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)