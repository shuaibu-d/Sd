package com.sdtech.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TealOnLight,
    onPrimary = White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD3F1EF),
    onPrimaryContainer = MidnightNavy,
    secondary = MidnightNavy,
    onSecondary = White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE3EC),
    onSecondaryContainer = MidnightNavy,
    tertiary = PremiumGold,
    onTertiary = MidnightNavy,
    background = CloudWhite,
    onBackground = MidnightNavy,
    surface = White,
    onSurface = MidnightNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = MutedTextLight,
    outline = androidx.compose.ui.graphics.Color(0xFFB9C3D0),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD5DCE5),
    surfaceContainerLowest = White,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF7F9FB),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF0F3F7),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE9EDF3),
    surfaceContainerHighest = LightSurfaceVariant,
    error = androidx.compose.ui.graphics.Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = InnovationTeal,
    onPrimary = MidnightNavy,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF0F4B4D),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFC9F2EF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB9C8D8),
    onSecondary = MidnightNavy,
    secondaryContainer = DarkSurfaceHighest,
    onSecondaryContainer = CloudWhite,
    tertiary = PremiumGold,
    onTertiary = MidnightNavy,
    background = MidnightNavy,
    onBackground = CloudWhite,
    surface = DarkSurface,
    onSurface = CloudWhite,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = MutedTextDark,
    outline = androidx.compose.ui.graphics.Color(0xFF4A5E78),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF30445E),
    surfaceContainerLowest = DeepNavy,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF142236),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    error = androidx.compose.ui.graphics.Color(0xFFFF8A80)
)

private val SDShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SDStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SDStudioTypography,
        shapes = SDShapes,
        content = content
    )
}
