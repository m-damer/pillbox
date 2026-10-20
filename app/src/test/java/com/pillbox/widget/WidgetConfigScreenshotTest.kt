package com.pillbox.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.Person
import com.pillbox.domain.WidgetSettings
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Widget settings in en/ar, light/dark, font scale 1.0/2.0. (The widget itself is drawn by Glance and is checked on a phone.) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class WidgetConfigScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val people = listOf(Person(id = 1, name = "Me", isMe = true), Person(id = 2, name = "Mom"), Person(id = 3, name = "Dad"))

    @Composable private fun content() = WidgetSettingsContent(WidgetSettings(), people, {}, {}, {})

    @Test fun config_en() = shoot("config_en", ThemeMode.Light, 1f)
    @Test fun config_en_dark_2x() = shoot("config_en_dark_2x", ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun config_ar() = shoot("config_ar", ThemeMode.Light, 1f)

    private fun shoot(name: String, mode: ThemeMode, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/widget_$name.png")
    }
}
