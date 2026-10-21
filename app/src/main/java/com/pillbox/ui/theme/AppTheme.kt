package com.pillbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.pillbox.domain.DisplaySettings
import com.pillbox.domain.ThemeChoice
import com.pillbox.ui.LocalDisplaySettings

/** The theme with what the user chose in Settings: light, dark or the phone's, high contrast, and text size. */
@Composable
fun PillBoxAppTheme(settings: DisplaySettings, content: @Composable () -> Unit) {
    val mode = when (settings.theme) { ThemeChoice.SYSTEM -> ThemeMode.System; ThemeChoice.LIGHT -> ThemeMode.Light; ThemeChoice.DARK -> ThemeMode.Dark }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDisplaySettings provides settings,
        LocalDensity provides Density(density.density, settings.fontScale(density.fontScale)),
    ) {
        PillBoxTheme(mode = mode, highContrast = settings.highContrast) {
            MatchWindowToTheme()
            content()
        }
    }
}

/**
 * The app's own theme can differ from the phone's (dark app on a light phone). The window behind the screens and the
 * status and navigation bar icons must follow the app, or white shows at the rounded corners and the clock is unreadable.
 */
@Composable
private fun MatchWindowToTheme() {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    val background = PillBox.colors.background
    val dark = background.luminance() < 0.5f
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background.toArgb()))
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}
