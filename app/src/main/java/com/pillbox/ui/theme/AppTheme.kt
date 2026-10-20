package com.pillbox.ui.theme

import androidx.compose.runtime.Composable
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
        PillBoxTheme(mode = mode, highContrast = settings.highContrast, content = content)
    }
}
