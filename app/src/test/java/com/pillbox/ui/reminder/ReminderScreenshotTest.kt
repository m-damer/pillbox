package com.pillbox.ui.reminder

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Schedule
import com.pillbox.domain.WithFood
import com.pillbox.reminder.ReminderScreen
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The full-screen reminder (what opens over the lock screen) in en/ar. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h844dp-xxhdpi")
class ReminderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 17)
    private val at = day.atTime(14, 0)
    private val amox = Medicine(id = 1, personId = 1, name = "Amoxicillin", unit = DoseUnit.CAPSULE, stock = 20.0)
    private val metf = Medicine(id = 2, personId = 1, name = "Metformin", unit = DoseUnit.TABLET, stock = 20.0)
    private fun sched(food: WithFood) = Schedule(day.minusDays(10), null, Frequency.FixedTimes(listOf(LocalTime.of(14, 0))), food)
    private val rows = listOf(
        DoseRow(amox, sched(WithFood.WITH_MEAL), at, null, false),
        DoseRow(metf, sched(WithFood.AFTER_MEAL), at, null, false),
    )

    @Test fun landing_en() = shoot("landing_en", "en")
    @Config(qualifiers = "+ar-ldrtl") @Test fun landing_ar() = shoot("landing_ar", "ar")
    @Test fun landing_en_dark() = shoot("landing_en_dark", "en", ThemeMode.Dark)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                PillBoxTheme(mode = mode) {
                    ReminderScreen(at, rows, fmt, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/reminder_$name.png")
    }
}
