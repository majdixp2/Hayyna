package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm, light, soft color scheme
private val LightCalmColorScheme = lightColorScheme(
    primary = CalmSagePrimary,
    onPrimary = Color.White,
    primaryContainer = CalmSageContainer,
    onPrimaryContainer = OnCalmSageContainer,
    secondary = SoftTerracotta,
    onSecondary = Color.White,
    secondaryContainer = SoftTerracottaContainer,
    onSecondaryContainer = OnSoftTerracottaContainer,
    tertiary = CalmSlateBlue,
    onTertiary = Color.White,
    tertiaryContainer = CalmSlateBlueContainer,
    onTertiaryContainer = OnCalmSlateBlueContainer,
    background = SoftCreamBackground,
    onBackground = DarkTextPrimary,
    surface = PureWhiteSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = SoftLinenVariant,
    onSurfaceVariant = MutedTextSecondary,
    outline = SoftBorderColor,
    outlineVariant = SoftBorderColor.copy(alpha = 0.6f)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightCalmColorScheme,
        typography = Typography,
        content = content
    )
}
