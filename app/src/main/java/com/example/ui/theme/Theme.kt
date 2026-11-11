package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AmatatuColorScheme = darkColorScheme(
    primary = KampalaGold,
    onPrimary = EmeraldAbyss,
    primaryContainer = GoldDeep,
    onPrimaryContainer = GoldShimmer,
    secondary = JadeSuccess,
    onSecondary = EmeraldAbyss,
    secondaryContainer = EmeraldFeltLight,
    onSecondaryContainer = WarmOffWhite,
    tertiary = AmberWarning,
    onTertiary = EmeraldAbyss,
    background = EmeraldAbyss,
    onBackground = WarmOffWhite,
    surface = EmeraldFelt,
    onSurface = WarmOffWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = MutedSage,
    surfaceContainerLowest = ObsidianMahogany,
    surfaceContainerLow = EmeraldFelt,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    outline = SubtleBorder,
    outlineVariant = EmeraldFeltLight,
    error = CrimsonAlert,
    onError = WarmOffWhite
)

private val AmatatuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AmatatuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmatatuColorScheme,
        typography = AmatatuTypography,
        shapes = AmatatuShapes,
        content = content
    )
}
