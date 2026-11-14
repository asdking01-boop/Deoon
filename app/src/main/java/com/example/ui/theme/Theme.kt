package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class DebtBookExtendedColors(
    val isDark: Boolean,
    val emerald: Color,
    val emeraldContainer: Color,
    val crimson: Color,
    val crimsonContainer: Color,
    val gold: Color,
    val goldContainer: Color,
    val cardBorder: Color,
    val glassSurface: Color,
    val subtleSurface: Color,
    val mutedText: Color
)

val LocalDebtBookColors = staticCompositionLocalOf {
    DebtBookExtendedColors(
        isDark = true,
        emerald = EmeraldPrimary,
        emeraldContainer = EmeraldSoft,
        crimson = CrimsonRed,
        crimsonContainer = CrimsonSoft,
        gold = GoldAccent,
        goldContainer = GoldSoft,
        cardBorder = NavyBorder,
        glassSurface = NavyCard.copy(alpha = 0.78f),
        subtleSurface = NavyElevated,
        mutedText = TextSoftGray
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = DeepNavy,
    primaryContainer = NavyElevated,
    onPrimaryContainer = TextWhite,
    secondary = EmeraldPrimary,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF0A3A2E),
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = AmberHighlight,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSoftGray,
    outline = NavyBorder,
    outlineVariant = NavyBorder.copy(alpha = 0.5f),
    error = CrimsonRed,
    onError = Color.White,
    errorContainer = Color(0xFF4C1419),
    onErrorContainer = Color(0xFFFCA5A5)
)

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4F5),
    onPrimaryContainer = DeepNavy,
    secondary = EmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = GoldAccent,
    onTertiary = DeepNavy,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightCardAlt,
    onSurfaceVariant = LightInkSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.7f),
    error = CrimsonDark,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartDebtBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isArabic: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        DebtBookExtendedColors(
            isDark = true,
            emerald = EmeraldPrimary,
            emeraldContainer = Color(0xFF0B3B2E),
            crimson = CrimsonRed,
            crimsonContainer = Color(0xFF4B161B),
            gold = GoldAccent,
            goldContainer = Color(0xFF3E3012),
            cardBorder = NavyBorder,
            glassSurface = NavyCard.copy(alpha = 0.86f),
            subtleSurface = NavyElevated,
            mutedText = TextSoftGray
        )
    } else {
        DebtBookExtendedColors(
            isDark = false,
            emerald = EmeraldDark,
            emeraldContainer = Color(0xFFD1FAE5),
            crimson = CrimsonDark,
            crimsonContainer = Color(0xFFFEE2E2),
            gold = Color(0xFFB48914),
            goldContainer = Color(0xFFFEF3C7),
            cardBorder = LightBorder,
            glassSurface = LightSurface.copy(alpha = 0.94f),
            subtleSurface = LightCardAlt,
            mutedText = LightInkSecondary
        )
    }

    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalDebtBookColors provides extendedColors,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
