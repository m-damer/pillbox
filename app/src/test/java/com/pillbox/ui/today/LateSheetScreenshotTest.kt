package com.pillbox.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Schedule
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBox
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

/** The Taken late sheet content, en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class LateSheetScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 20)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val amox = Medicine(id = 1, personId = 1, name = "Amoxicillin", unit = DoseUnit.CAPSULE, stock = 20.0)
    private val met = Medicine(id = 2, personId = 1, name = "Metformin", unit = DoseUnit.TABLET, stock = 20.0)
    private val sched = Schedule(day.minusDays(3), null, Frequency.EveryNHours(t(6), 8))

    private fun rows() = listOf(amox, met).map { com.pillbox.domain.DoseRow(it, sched, day.atTime(14, 0), null, false) }

    private fun hours() = TodaySheet.Late(
        rows = rows(), early = emptySet(), askRows = rows(), takenAt = day.atTime(14, 40), minutesLate = 40, duePlanned = day.atTime(14, 0),
        gapHours = 8, moveTimes = listOf(day.atTime(22, 40), day.plusDays(1).atTime(6, 40)), delayMinutes = 40,
        keepNext = day.atTime(22, 0), keepGapMinutes = 440,
    )

    private fun fixed() = TodaySheet.Late(
        rows = rows().take(1), early = emptySet(), askRows = rows().take(1), takenAt = day.atTime(14, 40), minutesLate = 40, duePlanned = day.atTime(14, 0),
        gapHours = null, moveTimes = listOf(day.atTime(22, 40)), delayMinutes = 40, keepNext = day.atTime(22, 0), keepGapMinutes = 440,
    )

    @Test fun hours_en() = shoot("hours_en", "en") { hours() }
    @Test fun hours_en_dark_2x() = shoot("hours_en_dark_2x", "en", ThemeMode.Dark, 2f) { hours() }
    @Config(qualifiers = "+ar-ldrtl") @Test fun hours_ar() = shoot("hours_ar", "ar") { hours() }
    @Test fun fixed_en() = shoot("fixed_en", "en") { fixed() }

    private fun fmt(ar: Boolean) = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, sheet: () -> TodaySheet.Late) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Column(Modifier.fillMaxSize().background(PillBox.colors.card).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        LateSheet(sheet(), fmt(lang == "ar")) { _, _ -> }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/late_sheet_$name.png")
    }
}
