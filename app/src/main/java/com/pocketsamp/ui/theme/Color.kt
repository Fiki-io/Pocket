package com.pocketsamp.ui.theme

import androidx.compose.ui.graphics.Color

object PocketSampColors {
    val BackgroundBase = Color(0xFF121212)
    val BackgroundTop = Color(0xFF282828)
    val SurfaceCard = Color(0xFF1E1E1E)
    val SurfaceElevated = Color(0xFF262626)
    val SurfaceInteractive = Color(0xFF2E2E2E)
    val SurfaceOverlay = Color(0xFF1C1C1C)

    val CardOverlay = Color.White.copy(alpha = 0.06f)
    val InteractiveOverlay = Color.White.copy(alpha = 0.10f)
    val BorderSubtle = Color.White.copy(alpha = 0.10f)
    val BorderStrong = Color.White.copy(alpha = 0.16f)
    val ScrimDark = Color.Black.copy(alpha = 0.68f)
    val GlowRadialNeutral = Color.White.copy(alpha = 0.08f)
    val GlowRadialAccent = Color(0xFF0A78D2).copy(alpha = 0.12f)

    val AccentBlue = Color(0xFF0A78D2)
    val AccentBlueMuted = Color(0xFF0A78D2).copy(alpha = 0.20f)
    val AccentBorder = Color(0xFF3E9BE6)

    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF9A9A9A)
    val TextTertiary = Color(0xFF707070)

    val StatusActive = Color(0xFF3FA36C)
    val StatusWarning = Color(0xFFD19A3E)
    val StatusNeutral = Color(0xFF9A9A9A)
    val ConsoleBackground = Color(0xFF0E0E0E)
}
