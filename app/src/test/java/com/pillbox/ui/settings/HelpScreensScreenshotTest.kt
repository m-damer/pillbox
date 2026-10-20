package com.pillbox.ui.settings

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
import com.pillbox.domain.DisplaySettings
import com.pillbox.reminder.PermissionState
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Reminders not ringing?, Simple mode and High contrast preview in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class HelpScreensScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val broken = PermissionState(notifications = false, exactAlarms = false, battery = false, doNotDisturb = true)
    private val good = PermissionState(notifications = true, exactAlarms = true, battery = true)

    @Test fun ringing_en() = shoot("ringing_en", "en") { NotRingingContent(broken, {}, {}, {}) }
    @Test fun ringing_ok_en() = shoot("ringing_ok_en", "en") { NotRingingContent(good, {}, {}, {}) }
    @Test fun ringing_en_dark_2x() = shoot("ringing_en_dark_2x", "en", ThemeMode.Dark, 2f) { NotRingingContent(broken, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ringing_ar() = shoot("ringing_ar", "ar") { NotRingingContent(broken, {}, {}, {}) }

    @Test fun simple_en() = shoot("simple_en", "en") { SimpleModeContent(DisplaySettings(simpleMode = true), {}, {}, {}, {}) }
    @Test fun simple_en_dark_2x() = shoot("simple_en_dark_2x", "en", ThemeMode.Dark, 2f) { SimpleModeContent(DisplaySettings(simpleMode = true), {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun simple_ar() = shoot("simple_ar", "ar") { SimpleModeContent(DisplaySettings(simpleMode = true), {}, {}, {}, {}) }

    @Test fun preview_en() = shoot("preview_en", "en") { HighContrastPreviewContent(fmt(false), false, {}, {}) }
    @Test fun preview_en_dark_2x() = shoot("preview_en_dark_2x", "en", ThemeMode.Dark, 2f) { HighContrastPreviewContent(fmt(false), true, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun preview_ar() = shoot("preview_ar", "ar") { HighContrastPreviewContent(fmt(true), false, {}, {}) }

    @Test fun accessibility_new_en() = shoot("accessibility_new_en", "en") { AccessibilityContent(DisplaySettings(simpleMode = true), {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun accessibility_new_ar_2x() = shoot("accessibility_new_ar_2x", "ar", ThemeMode.Light, 2f) { AccessibilityContent(DisplaySettings(simpleMode = true), {}, {}, {}, {}) }

    private fun fmt(ar: Boolean) = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/help_$name.png")
    }
}
