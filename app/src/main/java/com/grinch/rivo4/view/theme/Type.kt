package com.grinch.rivo4.view.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object VobizFonts {
    // System fallback font families - using platform fonts that match the design spec
    // Display: Cormorant Garamond (serif) -> fallback to system Serif (Georgia/Times New Roman)
    val DisplayFontFamily = FontFamily.Serif

    // UI: Inter (sans-serif) -> fallback to system SansSerif (Roboto/Inter)
    val UiFontFamily = FontFamily.SansSerif

    // Mono: JetBrains Mono -> fallback to system Monospace
    val MonoFontFamily = FontFamily.Monospace
}

private fun vobizTextStyle(
    fontFamily: FontFamily,
    fontSize: Float,
    lineHeight: Float,
    letterSpacing: Float,
    fontWeight: FontWeight
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val VobizTypography = Typography(
    // Display styles - Cormorant Garamond (serif)
    displayLarge = vobizTextStyle(VobizFonts.DisplayFontFamily, 42f, 52f, -0.2f, FontWeight.Medium),      // h1 - 42px
    displayMedium = vobizTextStyle(VobizFonts.DisplayFontFamily, 32f, 40f, 0f, FontWeight.Medium),      // h2 - 32px
    displaySmall = vobizTextStyle(VobizFonts.DisplayFontFamily, 24f, 32f, 0f, FontWeight.Medium),       // h3 - 24px

    // Headline styles - Cormorant Garamond
    headlineLarge = vobizTextStyle(VobizFonts.DisplayFontFamily, 24f, 32f, 0f, FontWeight.Medium),       // 24px
    headlineMedium = vobizTextStyle(VobizFonts.DisplayFontFamily, 20f, 28f, 0f, FontWeight.Medium),      // 20px
    headlineSmall = vobizTextStyle(VobizFonts.DisplayFontFamily, 18f, 26f, 0f, FontWeight.Medium),       // 18px

    // Title styles - Inter (UI font)
    titleLarge = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.15f, FontWeight.Medium),            // 16px
    titleMedium = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Medium),            // 14px
    titleSmall = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Medium),             // 12px

    // Body styles - Inter
    bodyLarge = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.5f, FontWeight.Normal),              // 16px - base
    bodyMedium = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.2f, FontWeight.Normal),             // 14px
    bodySmall = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.4f, FontWeight.Normal),              // 12px

    // Label styles - Inter
    labelLarge = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Medium),             // 14px
    labelMedium = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Medium),            // 12px
    labelSmall = vobizTextStyle(VobizFonts.UiFontFamily, 11f, 16f, 0.5f, FontWeight.Medium),             // 11px - micro/badges

    // Emphasized variants
    displayLargeEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 42f, 52f, 0f, FontWeight.Bold),
    displayMediumEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 32f, 40f, 0f, FontWeight.Bold),
    displaySmallEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 24f, 32f, 0f, FontWeight.Bold),
    headlineLargeEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 24f, 32f, 0f, FontWeight.Bold),
    headlineMediumEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 20f, 28f, 0f, FontWeight.Bold),
    headlineSmallEmphasized = vobizTextStyle(VobizFonts.DisplayFontFamily, 18f, 26f, 0f, FontWeight.Bold),
    titleLargeEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.15f, FontWeight.Bold),
    titleMediumEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Bold),
    titleSmallEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Bold),
    bodyLargeEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.15f, FontWeight.Medium),
    bodyMediumEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.25f, FontWeight.Medium),
    bodySmallEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.4f, FontWeight.Medium),
    labelLargeEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Bold),
    labelMediumEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Bold),
    labelSmallEmphasized = vobizTextStyle(VobizFonts.UiFontFamily, 11f, 16f, 0.5f, FontWeight.Bold),
)

// Semantic text styles for easy access
object VobizTextStyles {
    // Display/Headline (Cormorant Garamond / Serif)
    val h1 = vobizTextStyle(VobizFonts.DisplayFontFamily, 42f, 52f, -0.2f, FontWeight.Medium)
    val h2 = vobizTextStyle(VobizFonts.DisplayFontFamily, 32f, 40f, 0f, FontWeight.Medium)
    val h3 = vobizTextStyle(VobizFonts.DisplayFontFamily, 24f, 32f, 0f, FontWeight.Medium)
    val h4 = vobizTextStyle(VobizFonts.DisplayFontFamily, 20f, 28f, 0f, FontWeight.Medium)
    val h5 = vobizTextStyle(VobizFonts.DisplayFontFamily, 18f, 26f, 0f, FontWeight.Medium)

    // UI Text (Inter / SansSerif)
    val titleLarge = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.15f, FontWeight.Medium)
    val titleMedium = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Medium)
    val titleSmall = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Medium)

    val bodyLarge = vobizTextStyle(VobizFonts.UiFontFamily, 16f, 24f, 0.5f, FontWeight.Normal)
    val bodyMedium = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.2f, FontWeight.Normal)
    val bodySmall = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.4f, FontWeight.Normal)

    val labelLarge = vobizTextStyle(VobizFonts.UiFontFamily, 14f, 20f, 0.1f, FontWeight.Medium)
    val labelMedium = vobizTextStyle(VobizFonts.UiFontFamily, 12f, 16f, 0.5f, FontWeight.Medium)
    val labelSmall = vobizTextStyle(VobizFonts.UiFontFamily, 11f, 16f, 0.5f, FontWeight.Medium)

    // Mono (JetBrains Mono / Monospace)
    val monoLarge = vobizTextStyle(VobizFonts.MonoFontFamily, 16f, 24f, 0f, FontWeight.Normal)
    val monoMedium = vobizTextStyle(VobizFonts.MonoFontFamily, 14f, 20f, 0f, FontWeight.Normal)
    val monoSmall = vobizTextStyle(VobizFonts.MonoFontFamily, 12f, 16f, 0f, FontWeight.Medium)
    val monoXs = vobizTextStyle(VobizFonts.MonoFontFamily, 11f, 16f, 0f, FontWeight.Medium)

    // Special
    val score = vobizTextStyle(VobizFonts.DisplayFontFamily, 48f, 56f, 0f, FontWeight.Bold) // threat scores
    val micro = vobizTextStyle(VobizFonts.UiFontFamily, 10f, 14f, 0.8f, FontWeight.Medium)  // badges/tags
}