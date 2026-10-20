package com.pillbox.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.HistoryBuilder
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** History in en/ar, light/dark, font scale 1.0/2.0, plus the empty state. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(21, 0)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val metformin = Medicine(id = 1, personId = 1, name = "Metformin", stock = 40.0)
    private val amox = Medicine(id = 2, personId = 1, name = "Amoxicillin", stock = 18.0)
    private val sMet = Schedule(today.minusDays(60), null, Frequency.FixedTimes(listOf(t(14), t(20, 30))))
    private val sAmox = Schedule(today.minusDays(60), null, Frequency.FixedTimes(listOf(t(6), t(22))))

    private fun takenEvents(): List<DoseEvent> {
        val all = (0..29).flatMap { d ->
            val day = today.minusDays(d.toLong())
            listOf(1L to listOf(t(14), t(20, 30)), 2L to listOf(t(6), t(22))).flatMap { (id, times) ->
                times.map { DoseEvent(medicineId = id, planned = day.atTime(it), taken = day.atTime(it).plusMinutes(3), status = DoseStatus.TAKEN) }
            }
        }
        val gone = setOf(1L to today.minusDays(1).atTime(20, 30), 1L to today.minusDays(9).atTime(14, 0), 1L to today.minusDays(9).atTime(20, 30), 2L to today.minusDays(5).atTime(6, 0))
        val late = DoseEvent(medicineId = 2, planned = today.minusDays(5).atTime(6, 0), taken = today.minusDays(5).atTime(8, 0), status = DoseStatus.TAKEN, lateMinutes = 120)
        return all.filterNot { (it.medicineId to it.planned) in gone } + late +
            DoseEvent(medicineId = 1, planned = today.minusDays(1).atTime(20, 30), status = DoseStatus.SKIPPED)
    }

    private val items = listOf(Triple(metformin, sMet, emptyList<Pause>()), Triple(amox, sAmox, emptyList<Pause>()))
    private val state = HistoryUiState(true, "Me", today, HistoryBuilder.build(items, takenEvents(), now))
    private val stateAr = state.copy(personName = "أنا")
    private val empty = HistoryUiState(true, "Me", today, HistoryBuilder.build(emptyList(), emptyList(), now))

    @Test fun en() = shoot("en", "en", state)
    @Test fun en_empty() = shoot("en_empty", "en", empty)
    @Test fun en_dark_2x() = shoot("en_dark_2x", "en", state, ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar() = shoot("ar", "ar", stateAr)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_empty() = shoot("ar_empty", "ar", empty)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x() = shoot("ar_dark_2x", "ar", stateAr, ThemeMode.Dark, 2f)

    private fun shoot(name: String, lang: String, s: HistoryUiState, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        HistoryContent(s, fmt, if (ar) DayOfWeek.SATURDAY else DayOfWeek.SUNDAY, {}, {}, {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/history_$name.png")
    }
}
