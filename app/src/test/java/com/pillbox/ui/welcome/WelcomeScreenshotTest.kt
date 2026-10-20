package com.pillbox.ui.welcome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.TextSize
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Welcome screen in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h844dp-xxhdpi")
class WelcomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun en() = shoot("en", "en")
    @Test fun en_dark_2x() = shoot("en_dark_2x", "en", ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar() = shoot("ar", "ar")

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    WelcomeContent(lang, TextSize.STANDARD, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/welcome_$name.png")
    }
}
