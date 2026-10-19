package com.grinch.rivo4.view.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grinch.rivo4.controller.util.PreferenceManager
import org.koin.compose.koinInject

const val KEY_CUSTOM_PRIMARY_COLOR: String = "custom_primary_color"
const val CUSTOM_PRIMARY_COLOR_UNSET: Int = -1

val LocalNavBarStyle: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { PreferenceManager.NAV_BAR_STYLE_STANDARD }

@Composable
fun VobizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    prefs: PreferenceManager = koinInject(),
    content: @Composable () -> Unit
) {
    val settingsVersion by prefs.settingsChanged.collectAsState()
    val context = LocalContext.current

    val dynamicColor = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)
    }
    val amoledMode = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_AMOLED_MODE, false)
    }
    val customPrimaryInt = remember(settingsVersion) {
        prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, CUSTOM_PRIMARY_COLOR_UNSET)
    }
    val cardRoundness = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, 28).coerceAtLeast(5)
    }

    val colorScheme = remember(darkTheme, dynamicColor, amoledMode, customPrimaryInt) {
        val base = when {
            dynamicColor -> if (darkTheme) VobizDarkColorScheme else VobizLightColorScheme
            customPrimaryInt != CUSTOM_PRIMARY_COLOR_UNSET -> {
                if (darkTheme) VobizDarkColorScheme else VobizLightColorScheme
            }
            darkTheme -> VobizDarkColorScheme
            else -> VobizLightColorScheme
        }
        if (darkTheme && amoledMode) base.copy(
            background = Color.Black,
            surface = Color.Black,
        ) else base
    }

    val vobizColors = remember(darkTheme) { vobizColorsForTheme(darkTheme) }
    val shapes = VobizShapeScheme
    val navBarStyle = remember(settingsVersion) {
        prefs.getInt(PreferenceManager.KEY_NAV_BAR_STYLE, PreferenceManager.NAV_BAR_STYLE_STANDARD)
    }

    CompositionLocalProvider(
        LocalVobizColors provides vobizColors,
        LocalNavBarStyle provides navBarStyle
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            typography = VobizTypography,
            content = content
        )
    }
}

// Backward compatibility alias
@Composable
@Deprecated("Use VobizTheme instead", ReplaceWith("VobizTheme(darkTheme, prefs, content)"))
fun Rivo4Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    prefs: PreferenceManager = koinInject(),
    content: @Composable () -> Unit
) = VobizTheme(darkTheme, prefs, content)

// Spacing tokens - 8px grid system
object VobizSpacing {
    val sp1 = 8.dp
    val sp2 = 8.dp
    val sp3 = 16.dp
    val sp4 = 16.dp
    val sp5 = 24.dp
    val sp6 = 32.dp
    val sp8 = 32.dp
    val sp10 = 48.dp
    val sp12 = 64.dp

    // Legacy aliases
    val space1 = sp1
    val space2 = sp2
    val space3 = sp3
    val space4 = sp4
    val space5 = sp5
    val space6 = sp6
    val space8 = sp8
    val space10 = sp10
    val space12 = sp12
}

// Motion tokens
object VobizMotion {
    val fast = 100
    val normal = 200
    val slow = 300
    val slower = 500
}

// Layout tokens
object VobizLayout {
    val sidebarWidth = 260.dp
    val sidebarCollapsedWidth = 64.dp
    val detailWidth = 400.dp
    val headerHeight = 64.dp
    val containerMax = 1440.dp
}

// Top-level composable functions for theme colors (can be imported directly)
@Composable @ReadOnlyComposable
fun vobizPrimary(): Color = LocalVobizColors.current.primary

@Composable @ReadOnlyComposable
fun vobizOnPrimary(): Color = LocalVobizColors.current.onPrimary

@Composable @ReadOnlyComposable
fun vobizBackground(): Color = LocalVobizColors.current.background

@Composable @ReadOnlyComposable
fun vobizBackgroundSecondary(): Color = LocalVobizColors.current.backgroundSecondary

@Composable @ReadOnlyComposable
fun vobizBackgroundTertiary(): Color = LocalVobizColors.current.backgroundTertiary

@Composable @ReadOnlyComposable
fun vobizBackgroundElevated(): Color = LocalVobizColors.current.backgroundElevated

@Composable @ReadOnlyComposable
fun vobizBackgroundDeep(): Color = LocalVobizColors.current.backgroundDeep

@Composable @ReadOnlyComposable
fun vobizSurface(): Color = LocalVobizColors.current.surface

@Composable @ReadOnlyComposable
fun vobizSurfaceElevated(): Color = LocalVobizColors.current.surfaceElevated

@Composable @ReadOnlyComposable
fun vobizSurfaceHover(): Color = LocalVobizColors.current.surfaceHover

@Composable @ReadOnlyComposable
fun vobizSurfacePressed(): Color = LocalVobizColors.current.surfacePressed

@Composable @ReadOnlyComposable
fun vobizGlass(): Color = LocalVobizColors.current.glass

@Composable @ReadOnlyComposable
fun vobizGlassStrong(): Color = LocalVobizColors.current.glassStrong

@Composable @ReadOnlyComposable
fun vobizText(): Color = LocalVobizColors.current.text

@Composable @ReadOnlyComposable
fun vobizTextDim(): Color = LocalVobizColors.current.textDim

@Composable @ReadOnlyComposable
fun vobizTextMuted(): Color = LocalVobizColors.current.textMuted

@Composable @ReadOnlyComposable
fun vobizTextInverse(): Color = LocalVobizColors.current.textInverse

@Composable @ReadOnlyComposable
fun vobizBorder(): Color = LocalVobizColors.current.border

@Composable @ReadOnlyComposable
fun vobizBorderStrong(): Color = LocalVobizColors.current.borderStrong

@Composable @ReadOnlyComposable
fun vobizBorderDim(): Color = LocalVobizColors.current.borderDim

@Composable @ReadOnlyComposable
fun vobizHover(): Color = LocalVobizColors.current.hover

@Composable @ReadOnlyComposable
fun vobizHoverStrong(): Color = LocalVobizColors.current.hoverStrong

@Composable @ReadOnlyComposable
fun vobizDanger(): Color = LocalVobizColors.current.danger

@Composable @ReadOnlyComposable
fun vobizDangerGlow(): Color = LocalVobizColors.current.dangerGlow

@Composable @ReadOnlyComposable
fun vobizDangerBg(): Color = LocalVobizColors.current.dangerBg

@Composable @ReadOnlyComposable
fun vobizWarning(): Color = LocalVobizColors.current.warning

@Composable @ReadOnlyComposable
fun vobizWarningGlow(): Color = LocalVobizColors.current.warningGlow

@Composable @ReadOnlyComposable
fun vobizWarningBg(): Color = LocalVobizColors.current.warningBg

@Composable @ReadOnlyComposable
fun vobizSuccess(): Color = LocalVobizColors.current.success

@Composable @ReadOnlyComposable
fun vobizSuccessGlow(): Color = LocalVobizColors.current.successGlow

@Composable @ReadOnlyComposable
fun vobizSuccessBg(): Color = LocalVobizColors.current.successBg

@Composable @ReadOnlyComposable
fun vobizInfo(): Color = LocalVobizColors.current.info

@Composable @ReadOnlyComposable
fun vobizInfoGlow(): Color = LocalVobizColors.current.infoGlow

@Composable @ReadOnlyComposable
fun vobizInfoBg(): Color = LocalVobizColors.current.infoBg

@Composable @ReadOnlyComposable
fun vobizSecondary(): Color = LocalVobizColors.current.secondary

@Composable @ReadOnlyComposable
fun vobizSecondaryGlow(): Color = LocalVobizColors.current.secondaryGlow

@Composable @ReadOnlyComposable
fun vobizSecondaryBg(): Color = LocalVobizColors.current.secondaryBg

@Composable @ReadOnlyComposable
fun vobizAccent(): Color = LocalVobizColors.current.accent

@Composable @ReadOnlyComposable
fun vobizAccentHover(): Color = LocalVobizColors.current.accentHover

@Composable @ReadOnlyComposable
fun vobizOverlay(): Color = LocalVobizColors.current.overlay

@Composable @ReadOnlyComposable
fun vobizOverlayStrong(): Color = LocalVobizColors.current.overlayStrong

@Composable @ReadOnlyComposable
fun vobizScrollbar(): Color = LocalVobizColors.current.scrollbar

@Composable @ReadOnlyComposable
fun vobizSelection(): Color = LocalVobizColors.current.selection

@Composable @ReadOnlyComposable
fun vobizDdBg(): Color = LocalVobizColors.current.ddBg

@Composable @ReadOnlyComposable
fun vobizDdHover(): Color = LocalVobizColors.current.ddHover

// Button colors - grey in dark, black in light
@Composable @ReadOnlyComposable
fun vobizButtonPrimary(): Color = LocalVobizColors.current.primary

@Composable @ReadOnlyComposable
fun vobizButtonOnPrimary(): Color = LocalVobizColors.current.onPrimary

@Composable @ReadOnlyComposable
fun vobizButtonSecondary(): Color = LocalVobizColors.current.accent

@Composable @ReadOnlyComposable
fun vobizButtonOnSecondary(): Color = LocalVobizColors.current.onPrimary

@Composable @ReadOnlyComposable
fun vobizButtonGhost(): Color = LocalVobizColors.current.surface

@Composable @ReadOnlyComposable
fun vobizButtonGhostBorder(): Color = LocalVobizColors.current.borderStrong