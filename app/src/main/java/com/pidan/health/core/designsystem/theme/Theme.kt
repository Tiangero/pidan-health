package com.pidan.health.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BrandOrangeContainer,
    onPrimaryContainer = OnBrandOrangeContainer,
    secondary = BrandOrangeLight,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = BrandOrangeContainer,
    onSecondaryContainer = OnBrandOrangeContainer,
    background = WarmWhite,
    onBackground = androidx.compose.ui.graphics.Color(0xFF292524),
    surface = WarmWhite,
    onSurface = androidx.compose.ui.graphics.Color(0xFF292524),
    surfaceVariant = WarmWhiteVariant,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF57534E)
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangeLight,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF431407),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF7C2D12),
    onPrimaryContainer = BrandOrangeContainer,
    background = WarmDarkBackground,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE7E5E4),
    surface = WarmDarkSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFFE7E5E4),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3F3B38),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD6D3D1)
)

@Composable
fun PidanHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PidanTypography,
        shapes = PidanShapes,
        content = content
    )
}
