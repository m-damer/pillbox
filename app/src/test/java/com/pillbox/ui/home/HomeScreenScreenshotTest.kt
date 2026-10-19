package com.pillbox.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class HomeScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun en_light_1x() = shoot("en_light_1x", ThemeMode.Light, 1f)
    @Test fun en_dark_1x() = shoot("en_dark_1x", ThemeMode.Dark, 1f)
    @Test fun en_light_2x() = shoot("en_light_2x", ThemeMode.Light, 2f)
    @Test fun en_dark_2x() = shoot("en_dark_2x", ThemeMode.Dark, 2f)

    @Config(qualifiers = "+ar-ldrtl")
    @Test fun ar_light_1x() = shoot("ar_light_1x", ThemeMode.Light, 1f)

    @Config(qualifiers = "+ar-ldrtl")
    @Test fun ar_dark_1x() = shoot("ar_dark_1x", ThemeMode.Dark, 1f)

    @Config(qualifiers = "+ar-ldrtl")
    @Test fun ar_light_2x() = shoot("ar_light_2x", ThemeMode.Light, 2f)

    @Config(qualifiers = "+ar-ldrtl")
    @Test fun ar_dark_2x() = shoot("ar_dark_2x", ThemeMode.Dark, 2f)

    private fun shoot(name: String, mode: ThemeMode, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { HomeScreen() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_$name.png")
    }
}
