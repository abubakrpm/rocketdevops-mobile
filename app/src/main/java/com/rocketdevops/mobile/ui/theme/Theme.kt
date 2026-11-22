package com.rocketdevops.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** The three choices in Settings. "System" follows the phone's dark-mode switch. */
enum class ThemeMode { System, Light, Dark }

/**
 * Root theme. Wrap the whole app (and every @Preview) in this.
 * It provides the Material 3 colour scheme, our typography and shapes,
 * plus the extra brand tokens through LocalAppColors.
 */
@Composable
fun RocketDevOpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val reduceMotion = rememberReduceMotion()

    // Status-bar and navigation-bar icons: dark icons on the light theme, light icons on the dark theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = appColors.toColorScheme(),
            typography = AppTypography,
            shapes = AppMaterialShapes,
            content = content,
        )
    }
}

/** Short way to read our tokens inside any composable: AppTheme.colors.surface */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}

/**
 * Maps our tokens into Material 3's slots so built-in components
 * (dialogs, switches, snackbars, text fields) match the brand automatically.
 * Dynamic colour (Material You) is deliberately off: the brand stays violet on every phone.
 */
private fun AppColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = surfaceStrong,
        onPrimaryContainer = primaryText,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onHero,
        secondaryContainer = surfaceStrong,
        onSecondaryContainer = ink,
        tertiary = accent,
        onTertiary = onHero,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkSecondary,
        surfaceTint = background,          // flat design: no violet tint on elevated surfaces
        inverseSurface = heroDark,         // snackbars use the hero-dark colour
        inverseOnSurface = onHero,
        error = error.base,
        onError = Color.White,
        errorContainer = error.container,
        onErrorContainer = error.onContainer,
        outline = line,
        outlineVariant = line,
        surfaceBright = background,
        surfaceDim = surface,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,    // dialogs
        surfaceContainerHighest = line,    // switch track when off (matches the mockup)
    )
}
