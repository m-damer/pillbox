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
import com.pillbox.domain.TextSize
import com.pillbox.domain.ThemeChoice
import com.pillbox.domain.TimeFormat
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.DayOfWeek
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings home, Accessibility, Dark theme and Language and numbers in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val state = SettingsUiState(loaded = true, refillDays = 5, people = 2, reminder = com.pillbox.domain.ReminderSettings(quiet = com.pillbox.domain.QuietHours(enabled = true)))

    @Test fun home_en() = shoot("home_en", "en") { SettingsContent(state, fmt(false), {}) }
    @Test fun home_en_dark_2x() = shoot("home_en_dark_2x", "en", ThemeMode.Dark, 2f) { SettingsContent(state, fmt(false), {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun home_ar() = shoot("home_ar", "ar") { SettingsContent(state, fmt(true), {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun home_ar_dark_2x() = shoot("home_ar_dark_2x", "ar", ThemeMode.Dark, 2f) { SettingsContent(state, fmt(true), {}) }

    @Test fun accessibility_en() = shoot("accessibility_en", "en") { AccessibilityContent(DisplaySettings(), {}, {}, {}, {}) }
    @Test fun accessibility_en_dark_2x() = shoot("accessibility_en_dark_2x", "en", ThemeMode.Dark, 2f) {
        AccessibilityContent(DisplaySettings(textSize = TextSize.LARGE, theme = ThemeChoice.DARK, highContrast = true), {}, {}, {}, {})
    }
    @Config(qualifiers = "+ar-ldrtl") @Test fun accessibility_ar() = shoot("accessibility_ar", "ar") { AccessibilityContent(DisplaySettings(), {}, {}, {}, {}) }
    @Test fun theme_en() = shoot("theme_en", "en") { ThemeContent(ThemeChoice.SYSTEM, {}, {}) }

    @Test fun language_en() = shoot("language_en", "en") { language("en") }
    @Test fun language_en_dark_2x() = shoot("language_en_dark_2x", "en", ThemeMode.Dark, 2f) { language("en") }
    @Config(qualifiers = "+ar-ldrtl") @Test fun language_ar() = shoot("language_ar", "ar") { language("ar") }

    private val rem = com.pillbox.domain.ReminderSettings(quiet = com.pillbox.domain.QuietHours(enabled = true))

    @Test fun sound_en() = shoot("sound_en", "en") { SoundContent(rem, fmt(false), {}, {}, {}, {}, {}) }
    @Test fun sound_en_dark_2x() = shoot("sound_en_dark_2x", "en", ThemeMode.Dark, 2f) { SoundContent(rem, fmt(false), {}, {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun sound_ar() = shoot("sound_ar", "ar") { SoundContent(rem, fmt(true), {}, {}, {}, {}, {}) }

    @Test fun quiet_en() = shoot("quiet_en", "en") { QuietHoursContent(rem.quiet, fmt(false), {}, {}, {}, {}, {}, {}, {}) }
    @Test fun quiet_en_dark_2x() = shoot("quiet_en_dark_2x", "en", ThemeMode.Dark, 2f) { QuietHoursContent(rem.quiet, fmt(false), {}, {}, {}, {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun quiet_ar() = shoot("quiet_ar", "ar") { QuietHoursContent(rem.quiet, fmt(true), {}, {}, {}, {}, {}, {}, {}) }
    @Test fun quiet_off_en() = shoot("quiet_off_en", "en") { QuietHoursContent(com.pillbox.domain.QuietHours(), fmt(false), {}, {}, {}, {}, {}, {}, {}) }

    @Test fun meals_en() = shoot("meals_en", "en") { MealTimesContent(com.pillbox.domain.MealTimes(), fmt(false), {}, { _, _ -> }, {}) }
    @Test fun meals_en_dark_2x() = shoot("meals_en_dark_2x", "en", ThemeMode.Dark, 2f) { MealTimesContent(com.pillbox.domain.MealTimes(), fmt(false), {}, { _, _ -> }, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun meals_ar() = shoot("meals_ar", "ar") { MealTimesContent(com.pillbox.domain.MealTimes(), fmt(true), {}, { _, _ -> }, {}) }

    @Test fun late_en() = shoot("late_en", "en") { LateSettingContent(com.pillbox.domain.LateSetting.ASK, com.pillbox.domain.LateThreshold.MIN_30, fmt(false), {}, {}, {}, {}) }
    @Test fun late_en_dark_2x() = shoot("late_en_dark_2x", "en", ThemeMode.Dark, 2f) { LateSettingContent(com.pillbox.domain.LateSetting.MOVE_NEXT, com.pillbox.domain.LateThreshold.HOUR_1, fmt(false), {}, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun late_ar() = shoot("late_ar", "ar") { LateSettingContent(com.pillbox.domain.LateSetting.ASK, com.pillbox.domain.LateThreshold.MIN_30, fmt(true), {}, {}, {}, {}) }

    @Composable
    private fun language(code: String) = LanguageContent(code, code == "ar", TimeFormat.PHONE, DayOfWeek.SUNDAY, {}, {}, {}, {}, {}, {})

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
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_$name.png")
    }
}
