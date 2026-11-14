package com.pocketsamp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PocketSampRadii {
    val Pill = 12.dp
    val Field = 16.dp
    val Button = 18.dp
    val Card = 24.dp
    val Sheet = 28.dp
}

val PocketSampShapes = Shapes(
    extraSmall = RoundedCornerShape(PocketSampRadii.Pill),
    small = RoundedCornerShape(PocketSampRadii.Field),
    medium = RoundedCornerShape(PocketSampRadii.Button),
    large = RoundedCornerShape(PocketSampRadii.Card),
    extraLarge = RoundedCornerShape(PocketSampRadii.Sheet)
)
