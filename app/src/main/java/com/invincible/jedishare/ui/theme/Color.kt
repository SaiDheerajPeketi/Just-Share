package com.invincible.jedishare.ui.theme

import androidx.compose.ui.graphics.Color

// Brand red — same hue in both themes, tuned to sit at a mid-lightness so
// white text stays >4.5:1 contrast whether it's on a light or dark surface.
// Slightly deeper/less orange than a typical "urgent" red to avoid a
// gambling/phishing feel.
val MyRed = Color(0xFFE1414F)
val LightRed = Color(0xFFFF6B76)   // lighter tint, e.g. hover/gradient stop
val DarkRed = Color(0xFFA81F2D)    // pressed / shadow state

val Gold = Color(0xFFC9A227)       // muted, use sparingly (badges only)
val Green = Color(0xFF2E9E5B)      // success — keep separate from brand red

// --- Light theme neutrals ---
val LightBlack = Color(0xFF1C1B1F)     // primary text (onBackground/onSurface)
val LightWhite = Color(0xFFFFFFFF)     // text on primary/secondary fills
val LightSurface = Color(0xFFF7F5F3)   // page background — warm off-white,
                                        // not stark #FFFFFF
val LightCardBg = Color(0xFFFFFFFF)    // elevated card, distinct from surface
val LightMutedFg = Color(0xFF6F6C68)   // secondary text, captions
val LightBorder = Color(0xFFEAE6E1)    // hairline dividers

// --- Dark theme neutrals ---
// Deliberately cool and neutral (no red/brown undertone) — this is what
// fixes the "sewage" look. Three distinct steps of lightness so surface,
// card, and page background never collapse into each other.
val DarkBlack = Color(0xFFF4F2F5)      // primary text — near-white, NOT dark
val DarkWhite = Color(0xFFF7F5F3)      // text on primary/secondary fills —
                                        // must stay near-white here too, since
                                        // MyRed is the same brightness in both
                                        // themes. This is the value most likely
                                        // to have been wrong before.
val DarkSurface = Color(0xFF131215)    // page background — near-black, cool
val DarkCardBg = Color(0xFF221F24)     // elevated card, one step lighter
val DarkMutedFg = Color(0xFFA6A2AA)    // secondary text — cool grey, not
                                        // brown/red-tinted
val DarkBorder = Color(0xFF332F36)     // hairline, visible but subtle
