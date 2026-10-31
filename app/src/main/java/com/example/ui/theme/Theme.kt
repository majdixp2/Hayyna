package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.ui.i18n.I18n
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

// 🌙 المظهر الداكن (نفس ألوان الويب الداكنة)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7D9BF0),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF17213F),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = SoftTerracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A2A1E),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFF8FB8D6),
    onTertiary = Color(0xFF0B1020),
    tertiaryContainer = Color(0xFF1D3557),
    onTertiaryContainer = Color(0xFFD8E9F5),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE7EAF3),
    surface = Color(0xFF141B2E),
    onSurface = Color(0xFFE7EAF3),
    surfaceVariant = Color(0xFF1C2540),
    onSurfaceVariant = Color(0xFF9AA3BA),
    outline = Color(0xFF33405F),
    outlineVariant = Color(0xFF26304A)
)

/** المظهر: فاتح (الافتراضي) / داكن / حسب الجوال — من إعدادات التطبيق */
@Composable
fun isAppDark(): Boolean = when (I18n.theme) { "dark" -> true; "auto" -> isSystemInDarkTheme(); else -> false }

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isAppDark()) DarkColorScheme else LightCalmColorScheme,
        typography = Typography,
        content = content
    )
}
