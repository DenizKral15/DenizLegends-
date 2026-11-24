package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DenizCraftColorScheme = darkColorScheme(
    primary = AquaPrimary,
    onPrimary = OceanDeep,
    primaryContainer = AquaDark,
    onPrimaryContainer = TextPrimary,
    secondary = GoldAccent,
    onSecondary = OceanDeep,
    secondaryContainer = AmberWarm,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldAction,
    onTertiary = OceanDeep,
    background = OceanDeep,
    onBackground = TextPrimary,
    surface = OceanSlate,
    onSurface = TextPrimary,
    surfaceVariant = OceanCard,
    onSurfaceVariant = TextSecondary,
    outline = OceanCardBorder,
    error = CrimsonDanger,
    onError = Color.White
)

private val DenizCraftShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun DenizCraftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DenizCraftColorScheme,
        typography = DenizCraftTypography,
        shapes = DenizCraftShapes,
        content = content
    )
}
