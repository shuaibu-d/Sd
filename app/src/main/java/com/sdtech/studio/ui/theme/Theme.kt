package com.sdtech.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = InnovationTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2EF),
    onPrimaryContainer = MidnightNavy,
    secondary = MidnightNavy,
    onSecondary = Color.White,
    tertiary = PremiumGold,
    onTertiary = MidnightNavy,
    background = CloudWhite,
    onBackground = MidnightNavy,
    surface = Color.White,
    onSurface = MidnightNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateGrey,
    outline = Color(0xFFCBD3DD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55C8C1),
    onPrimary = MidnightNavy,
    primaryContainer = Color(0xFF174D4B),
    onPrimaryContainer = Color(0xFFD5F2EF),
    secondary = Color(0xFFB9C8D8),
    onSecondary = MidnightNavy,
    tertiary = PremiumGold,
    onTertiary = MidnightNavy,
    background = MidnightNavy,
    onBackground = CloudWhite,
    surface = DarkSurface,
    onSurface = CloudWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedTextDark,
    outline = Color(0xFF43566B)
)

@Composable
fun SDStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SDStudioTypography,
        content = content
    )
}
