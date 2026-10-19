package com.grinch.rivo4.view.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VobizColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryGlow: Color,
    val primaryGlowStrong: Color,
    val primaryBg: Color,

    val secondary: Color,
    val secondaryGlow: Color,
    val secondaryBg: Color,

    val accent: Color,
    val accentHover: Color,

    val background: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    val backgroundElevated: Color,
    val backgroundDeep: Color,

    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val surfacePressed: Color,

    val glass: Color,
    val glassStrong: Color,

    val text: Color,
    val textDim: Color,
    val textMuted: Color,
    val textInverse: Color,

    val border: Color,
    val borderStrong: Color,
    val borderDim: Color,

    val hover: Color,
    val hoverStrong: Color,

    val danger: Color,
    val dangerGlow: Color,
    val dangerBg: Color,

    val warning: Color,
    val warningGlow: Color,
    val warningBg: Color,

    val success: Color,
    val successGlow: Color,
    val successBg: Color,

    val info: Color,
    val infoGlow: Color,
    val infoBg: Color,

    val overlay: Color,
    val overlayStrong: Color,

    val shadowSm: Color,
    val shadowMd: Color,
    val shadowLg: Color,
    val shadowXl: Color,
    val shadowGlow: Color,
    val shadowGlowStrong: Color,

    val focusRing: Color,
    val scrollbar: Color,
    val selection: Color,

    val ddBg: Color,
    val ddHover: Color,
) {
    @Composable
    @ReadOnlyComposable
    fun ProvidableCompositionLocal<VobizColors>.provides(): ProvidableCompositionLocal<VobizColors> = this
}

val VobizDarkColors = VobizColors(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    primaryGlow = Color(0x38FFFFFF),
    primaryGlowStrong = Color(0x66FFFFFF),
    primaryBg = Color(0x12FFFFFF),

    secondary = Color(0xFFFFD700),
    secondaryGlow = Color(0x59FFD700),
    secondaryBg = Color(0x1EFFD700),

    accent = Color(0xFF1A1A1A),
    accentHover = Color(0xFF2A2A2A),

    background = Color(0xFF000000),
    backgroundSecondary = Color(0xFF0A0A0A),
    backgroundTertiary = Color(0xFF121212),
    backgroundElevated = Color(0xFF1A1A1A),
    backgroundDeep = Color(0xFF000000),

    surface = Color(0xFF000000),
    surfaceElevated = Color(0xFF1A1A1A),
    surfaceHover = Color(0xFF2A2A2A),
    surfacePressed = Color(0xFF333333),

    glass = Color(0x991A1A1A),
    glassStrong = Color(0xCC1A1A1A),

    text = Color(0xFFFFFFFF),
    textDim = Color(0xFFB0B0B0),
    textMuted = Color(0xFF888888),
    textInverse = Color(0xFF000000),

    border = Color(0xFF333333),
    borderStrong = Color(0xFF4A4A4A),
    borderDim = Color(0xFF1A1A1A),

    hover = Color(0xFF1A1A1A),
    hoverStrong = Color(0xFF2A2A2A),

    danger = Color(0xFFEB0029),
    dangerGlow = Color(0x66EB0029),
    dangerBg = Color(0x33EB0029),

    warning = Color(0xFFFFD700),
    warningGlow = Color(0x59FFD700),
    warningBg = Color(0x33FFD700),

    success = Color(0xFF00E676),
    successGlow = Color(0x5900E676),
    successBg = Color(0x3300E676),

    info = Color(0xFFFFFFFF),
    infoGlow = Color(0x38FFFFFF),
    infoBg = Color(0x33FFFFFF),

    overlay = Color(0xE0000000),
    overlayStrong = Color(0xF5000000),

    shadowSm = Color(0xFF000000),
    shadowMd = Color(0x80000000),
    shadowLg = Color(0xA0000000),
    shadowXl = Color(0xC0000000),
    shadowGlow = Color(0x38FFFFFF),
    shadowGlowStrong = Color(0x66FFFFFF),

    focusRing = Color(0xFFFFFFFF),
    scrollbar = Color(0xFF333333),
    selection = Color(0x4DFFFFFF),

    ddBg = Color(0xFF1C1C1C),
    ddHover = Color(0xFF2A2A2A),
)

val VobizLightColors = VobizColors(
    primary = Color(0xFF0D0D0D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDEDED),
    onPrimaryContainer = Color(0xFF0D0D0D),
    primaryGlow = Color(0x380D0D0D),
    primaryGlowStrong = Color(0x660D0D0D),
    primaryBg = Color(0x0F0D0D0D),

    secondary = Color(0xFFB8860B),
    secondaryGlow = Color(0x4DB8860B),
    secondaryBg = Color(0x1AB8860B),

    accent = Color(0xFFDADADA),
    accentHover = Color(0xFFCCCCCC),

    background = Color(0xFFF4F4F2),
    backgroundSecondary = Color(0xFFEDEDEB),
    backgroundTertiary = Color(0xFFE4E4E2),
    backgroundElevated = Color(0xFFDADADA),
    backgroundDeep = Color(0xFFFFFFFF),

    surface = Color(0x08000000),
    surfaceElevated = Color(0x0D000000),
    surfaceHover = Color(0x12000000),
    surfacePressed = Color(0x1A000000),

    glass = Color(0x8CFFFFFF),
    glassStrong = Color(0xC7FFFFFF),

    text = Color(0xFF0D0D0D),
    textDim = Color(0xFF6B6B6B),
    textMuted = Color(0xFF9C9C9C),
    textInverse = Color(0xFFFFFFFF),

    border = Color(0xFFD6D6D4),
    borderStrong = Color(0xFFC4C4C2),
    borderDim = Color(0xFFE2E2E0),

    hover = Color(0xFFEDEDEB),
    hoverStrong = Color(0xFFE0E0DE),

    danger = Color(0xFFC40023),
    dangerGlow = Color(0x4DC40023),
    dangerBg = Color(0x1AC40023),

    warning = Color(0xFFB8860B),
    warningGlow = Color(0x4DB8860B),
    warningBg = Color(0x1AB8860B),

    success = Color(0xFF00A86B),
    successGlow = Color(0x4D00A86B),
    successBg = Color(0x1A00A86B),

    info = Color(0xFF0D0D0D),
    infoGlow = Color(0x380D0D0D),
    infoBg = Color(0x0F0D0D0D),

    overlay = Color(0xCCF4F4F2),
    overlayStrong = Color(0xF2F4F4F2),

    shadowSm = Color(0x0A000000),
    shadowMd = Color(0x0F000000),
    shadowLg = Color(0x1A000000),
    shadowXl = Color(0x24000000),
    shadowGlow = Color(0x380D0D0D),
    shadowGlowStrong = Color(0x660D0D0D),

    focusRing = Color(0xFF0D0D0D),
    scrollbar = Color(0xFFC4C4C2),
    selection = Color(0x260D0D0D),

    ddBg = Color(0xFFF0F0EE),
    ddHover = Color(0x0F0D0D0D),
)

val LocalVobizColors: ProvidableCompositionLocal<VobizColors> =
    staticCompositionLocalOf { VobizDarkColors }

// Material3 ColorScheme mapping for compatibility
val VobizDarkColorScheme: ColorScheme = darkColorScheme(
    primary = VobizDarkColors.primary,
    onPrimary = VobizDarkColors.onPrimary,
    primaryContainer = VobizDarkColors.primaryContainer,
    onPrimaryContainer = VobizDarkColors.onPrimaryContainer,
    inversePrimary = VobizDarkColors.accent,
    secondary = VobizDarkColors.secondary,
    onSecondary = VobizDarkColors.onPrimary,
    secondaryContainer = VobizDarkColors.accent,
    onSecondaryContainer = VobizDarkColors.onPrimary,
    tertiary = VobizDarkColors.success,
    onTertiary = VobizDarkColors.onPrimary,
    tertiaryContainer = VobizDarkColors.successBg,
    onTertiaryContainer = VobizDarkColors.success,
    background = VobizDarkColors.background,
    onBackground = VobizDarkColors.text,
    surface = VobizDarkColors.background,
    onSurface = VobizDarkColors.text,
    surfaceVariant = VobizDarkColors.accent,
    onSurfaceVariant = VobizDarkColors.textDim,
    surfaceTint = VobizDarkColors.primary,
    inverseSurface = VobizLightColors.background,
    inverseOnSurface = VobizLightColors.text,
    error = VobizDarkColors.danger,
    onError = VobizDarkColors.onPrimary,
    errorContainer = VobizDarkColors.dangerBg,
    onErrorContainer = VobizDarkColors.danger,
    outline = VobizDarkColors.borderStrong,
    outlineVariant = VobizDarkColors.border,
    scrim = VobizDarkColors.overlay,
    surfaceBright = VobizDarkColors.backgroundElevated,
    surfaceContainer = VobizDarkColors.backgroundTertiary,
    surfaceContainerHigh = VobizDarkColors.backgroundElevated,
    surfaceContainerHighest = VobizDarkColors.accent,
    surfaceContainerLow = VobizDarkColors.backgroundSecondary,
    surfaceContainerLowest = VobizDarkColors.backgroundDeep,
    surfaceDim = VobizDarkColors.background,
)

val VobizLightColorScheme: ColorScheme = lightColorScheme(
    primary = VobizLightColors.primary,
    onPrimary = VobizLightColors.onPrimary,
    primaryContainer = VobizLightColors.primaryContainer,
    onPrimaryContainer = VobizLightColors.onPrimaryContainer,
    inversePrimary = VobizLightColors.accent,
    secondary = VobizLightColors.secondary,
    onSecondary = VobizLightColors.onPrimary,
    secondaryContainer = VobizLightColors.accent,
    onSecondaryContainer = VobizLightColors.onPrimary,
    tertiary = VobizLightColors.success,
    onTertiary = VobizLightColors.onPrimary,
    tertiaryContainer = VobizLightColors.successBg,
    onTertiaryContainer = VobizLightColors.success,
    background = VobizLightColors.background,
    onBackground = VobizLightColors.text,
    surface = VobizLightColors.background,
    onSurface = VobizLightColors.text,
    surfaceVariant = VobizLightColors.accent,
    onSurfaceVariant = VobizLightColors.textDim,
    surfaceTint = VobizLightColors.primary,
    inverseSurface = VobizDarkColors.background,
    inverseOnSurface = VobizDarkColors.text,
    error = VobizLightColors.danger,
    onError = VobizLightColors.onPrimary,
    errorContainer = VobizLightColors.dangerBg,
    onErrorContainer = VobizLightColors.danger,
    outline = VobizLightColors.borderStrong,
    outlineVariant = VobizLightColors.border,
    scrim = VobizLightColors.overlay,
    surfaceBright = VobizLightColors.backgroundElevated,
    surfaceContainer = VobizLightColors.backgroundTertiary,
    surfaceContainerHigh = VobizLightColors.backgroundElevated,
    surfaceContainerHighest = VobizLightColors.accent,
    surfaceContainerLow = VobizLightColors.backgroundSecondary,
    surfaceContainerLowest = VobizLightColors.backgroundDeep,
    surfaceDim = VobizLightColors.background,
)

fun vobizColorsForTheme(darkTheme: Boolean): VobizColors = if (darkTheme) VobizDarkColors else VobizLightColors

fun vobizColorSchemeForTheme(darkTheme: Boolean): ColorScheme = if (darkTheme) VobizDarkColorScheme else VobizLightColorScheme

// Backward compatibility for CallColors
@Immutable
data class CallColors(
    val answer: Color,
    val onAnswer: Color,
    val answerContainer: Color,
    val onAnswerContainer: Color,
    val decline: Color,
    val onDecline: Color,
    val declineContainer: Color,
    val onDeclineContainer: Color,
    val hold: Color,
    val onHold: Color,
    val holdContainer: Color,
    val onHoldContainer: Color
)

fun rivoCallColors(colorScheme: ColorScheme, darkTheme: Boolean): CallColors {
    return if (darkTheme) {
        CallColors(
            answer = Color(0xFF00E676),
            onAnswer = Color(0xFF0D0D0D),
            answerContainer = Color(0xFF00A86B),
            onAnswerContainer = Color(0xFFFFFFFF),
            decline = Color(0xFFEB0029),
            onDecline = Color(0xFFFFFFFF),
            declineContainer = Color(0xFF8C1D18),
            onDeclineContainer = Color(0xFFFFFFFF),
            hold = Color(0xFFD7BEE5),
            onHold = Color(0xFF3B2948),
            holdContainer = Color(0xFF523F5F),
            onHoldContainer = Color(0xFFF3D9FF)
        )
    } else {
        CallColors(
            answer = Color(0xFF00A86B),
            onAnswer = Color(0xFFFFFFFF),
            answerContainer = Color(0xFF00E676),
            onAnswerContainer = Color(0xFF0D0D0D),
            decline = Color(0xFFC40023),
            onDecline = Color(0xFFFFFFFF),
            declineContainer = Color(0xFFB8860B),
            onDeclineContainer = Color(0xFF0D0D0D),
            hold = Color(0xFF6B5778),
            onHold = Color(0xFFFFFFFF),
            holdContainer = Color(0xFFF3D9FF),
            onHoldContainer = Color(0xFF251432)
        )
    }
}

val LocalCallColors: ProvidableCompositionLocal<CallColors> = staticCompositionLocalOf {
    rivoCallColors(VobizLightColorScheme, darkTheme = false)
}

val MaterialTheme.callColors: CallColors
    @Composable @ReadOnlyComposable get() = LocalCallColors.current