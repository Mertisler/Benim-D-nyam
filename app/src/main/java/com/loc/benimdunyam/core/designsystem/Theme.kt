package com.loc.benimdunyam.core.designsystem

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Material 3 ve uygulama içi kartlar için tek tema kaynağı. */
@Composable
fun BenimDunyamTheme(content: @Composable () -> Unit) {
    val palette = AppThemeController.palette

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.softPrimary,
            onPrimaryContainer = palette.ink,
            secondary = palette.secondary,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.tape,
            onSecondaryContainer = palette.ink,
            tertiary = palette.secondary,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.paper,
            surfaceBright = palette.paper,
            surfaceDim = palette.tape,
            surfaceContainerLowest = palette.paper,
            surfaceContainerLow = palette.paper,
            surfaceContainer = palette.paper,
            surfaceContainerHigh = palette.softPrimary,
            surfaceContainerHighest = palette.tape,
            onSurface = palette.ink,
            surfaceVariant = palette.tape,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.border,
            surfaceTint = palette.primary
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.softPrimary,
            onPrimaryContainer = palette.ink,
            secondary = palette.secondary,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.tape,
            onSecondaryContainer = palette.ink,
            tertiary = palette.secondary,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.paper,
            surfaceBright = palette.paper,
            surfaceDim = palette.tape,
            surfaceContainerLowest = palette.paper,
            surfaceContainerLow = palette.paper,
            surfaceContainer = palette.paper,
            surfaceContainerHigh = palette.softPrimary,
            surfaceContainerHighest = palette.tape,
            onSurface = palette.ink,
            surfaceVariant = palette.tape,
            onSurfaceVariant = palette.muted,
            outline = palette.border,
            outlineVariant = palette.border,
            surfaceTint = palette.primary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.paper.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = BenimDunyamTypography,
        content = content
    )
}
