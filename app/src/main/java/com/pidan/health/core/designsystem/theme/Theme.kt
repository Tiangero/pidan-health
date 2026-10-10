package com.pidan.health.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TextInk = Color(0xFF292524)
private val TextSub = Color(0xFF57534E)

/**
 * 关键点：background（页面底）与 surface（卡片/导航栏）必须分离——
 * 设计稿页面底是暖灰 #F6F4F1，卡片/顶栏/导航栏是纯白。
 * surfaceContainer 系列全部压成暖中性色，避免 M3 默认的紫灰色系漏出来。
 */
private val LightColorScheme = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeContainer,
    onPrimaryContainer = OnBrandOrangeContainer,
    secondary = BrandOrangeLight,
    onSecondary = Color.White,
    secondaryContainer = BrandOrangeContainer,
    onSecondaryContainer = OnBrandOrangeContainer,
    background = PageBackground,
    onBackground = TextInk,
    surface = CardWhite,
    onSurface = TextInk,
    surfaceVariant = RingTrack,
    onSurfaceVariant = TextSub,
    outline = Color(0xFFD6D3D1),
    outlineVariant = CardBorder,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Color(0xFFFBFAF8),
    surfaceContainer = PageBackground,
    surfaceContainerHigh = Color(0xFFF1EFEC),
    surfaceContainerHighest = Color(0xFFEDEAE6)
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandOrangeLight,
    onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF7C2D12),
    onPrimaryContainer = BrandOrangeContainer,
    background = WarmDarkBackground,
    onBackground = Color(0xFFE7E5E4),
    surface = WarmDarkSurface,
    onSurface = Color(0xFFE7E5E4),
    surfaceVariant = Color(0xFF3F3B38),
    onSurfaceVariant = Color(0xFFD6D3D1),
    outline = Color(0xFF57534E),
    outlineVariant = Color(0xFF3F3B38)
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
