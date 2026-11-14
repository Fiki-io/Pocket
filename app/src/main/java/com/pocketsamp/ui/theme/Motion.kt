package com.pocketsamp.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object PocketSampMotion {
    const val DurationFastMs = 135
    const val DurationStandardMs = 220
    const val DurationSheetMs = 310
    const val PressScale = 0.97f

    val StandardEasing: Easing = FastOutSlowInEasing

    fun <T> fastTween() = tween<T>(durationMillis = DurationFastMs, easing = StandardEasing)
    fun <T> standardTween() = tween<T>(durationMillis = DurationStandardMs, easing = StandardEasing)
    fun <T> sheetTween() = tween<T>(durationMillis = DurationSheetMs, easing = StandardEasing)
    fun <T> standardSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
