package com.pocketsamp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PocketSampDarkColorScheme = darkColorScheme(
    primary = PocketSampColors.AccentBlue,
    onPrimary = PocketSampColors.TextPrimary,
    primaryContainer = PocketSampColors.AccentBlueMuted,
    onPrimaryContainer = PocketSampColors.TextPrimary,
    secondary = PocketSampColors.SurfaceInteractive,
    onSecondary = PocketSampColors.TextPrimary,
    background = PocketSampColors.BackgroundBase,
    onBackground = PocketSampColors.TextPrimary,
    surface = PocketSampColors.SurfaceCard,
    onSurface = PocketSampColors.TextPrimary,
    surfaceVariant = PocketSampColors.SurfaceElevated,
    onSurfaceVariant = PocketSampColors.TextSecondary,
    outline = PocketSampColors.BorderSubtle,
    outlineVariant = PocketSampColors.BorderStrong,
    error = PocketSampColors.StatusWarning,
    onError = PocketSampColors.BackgroundBase
)

@Composable
fun PocketSampTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketSampDarkColorScheme,
        typography = PocketSampTypography,
        shapes = PocketSampShapes,
        content = content
    )
}
