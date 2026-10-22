package com.pillbox.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.TodayBuilder
import com.pillbox.domain.WithFood
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today in en/ar, light/dark, font scale 1.0/2.0, with the design's sample day. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val sat = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val amox = Medicine(id = 1, personId = 1, name = "Amoxicillin", unit = DoseUnit.CAPSULE, stock = 20.0)
    private val metf = Medicine(id = 2, personId = 1, name = "Metformin", unit = DoseUnit.TABLET, stock = 20.0)
    private val items = listOf(
        Triple(amox, Schedule(sat.minusDays(10), null, Frequency.FixedTimes(listOf(t(6), t(14), t(22))), WithFood.WITH_MEAL), emptyList<Pause>()),
        Triple(metf, Schedule(sat.minusDays(10), null, Frequency.FixedTimes(listOf(t(14), t(20, 30))), WithFood.AFTER_MEAL), emptyList<Pause>()),
    )
    private val taken6 = DoseEvent(medicineId = 1, planned = sat.atTime(6, 0), taken = sat.atTime(6, 5), status = DoseStatus.TAKEN)
    private val skippedFri = DoseEvent(
        medicineId = 2, planned = sat.minusDays(1).atTime(20, 30), status = DoseStatus.SKIPPED, skipReason = "not_feeling_well",
    )

    private fun state(day: LocalDate, now: LocalDateTime, events: List<DoseEvent>) =
        TodayUiState(loaded = true, hasMedicines = true, model = TodayBuilder.build(items, events, day, now), selectedDate = day, now = now)

    private val nextNow = sat.atTime(13, 30)
    private val lateNow = sat.atTime(15, 0)

    @Test fun en_next() = shoot("en_next", state(sat, nextNow, listOf(taken6)), "en")
    @Test fun en_late() = shoot("en_late", state(sat, lateNow, listOf(taken6)), "en")
    @Test fun en_past_day() = shoot("en_past_day", state(sat.minusDays(1), nextNow, listOf(skippedFri)), "en")
    // Yesterday: both 2 PM doses and the evening one were never logged (Missed with "I took it"); one dose was logged later.
    private val loggedLater = DoseEvent(medicineId = 1, planned = sat.minusDays(1).atTime(6, 0), taken = sat.minusDays(1).atTime(6, 40), status = DoseStatus.TAKEN, lateMinutes = 40, loggedLater = true)
    @Test fun en_past_missed() = shoot("en_past_missed", state(sat.minusDays(1), nextNow, listOf(loggedLater)), "en")
    @Test fun en_past_missed_dark_2x() = shoot("en_past_missed_dark_2x", state(sat.minusDays(1), nextNow, listOf(loggedLater)), "en", ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_past_missed() = shoot("ar_past_missed", state(sat.minusDays(1), nextNow, listOf(loggedLater)), "ar")
    @Test fun en_future_day() = shoot("en_future_day", state(sat.plusDays(1), nextNow, emptyList()), "en")
    @Test fun en_empty() = shoot("en_empty", TodayUiState(loaded = true, hasMedicines = false, now = nextNow, selectedDate = sat), "en")
    @Test fun en_dark_2x_next() = shoot("en_dark_2x_next", state(sat, nextNow, listOf(taken6)), "en", ThemeMode.Dark, 2f)
    @Test fun en_light_2x_late() = shoot("en_light_2x_late", state(sat, lateNow, listOf(taken6)), "en", ThemeMode.Light, 2f)

    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_next() = shoot("ar_next", state(sat, nextNow, listOf(taken6)), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_late() = shoot("ar_late", state(sat, lateNow, listOf(taken6)), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x_next() = shoot("ar_dark_2x_next", state(sat, nextNow, listOf(taken6)), "ar", ThemeMode.Dark, 2f)

    private fun full(day: LocalDate, now: LocalDateTime) = state(day, now, listOf(taken6)).copy(
        asNeeded = listOf(AsNeededItem(3, "Paracetamol", DoseUnit.TABLET), AsNeededItem(4, "Ibuprofen", DoseUnit.TABLET)),
        lowStock = listOf(LowStock("Metformin", DoseUnit.TABLET, 4, com.pillbox.domain.StockLevel.BUY_SOON)),
    )

    /** Full phone height (844dp) so the timeline, the As needed card and the low stock strip are all in view. */
    @Config(qualifiers = "+h844dp") @Test fun en_full() = shoot("en_full", full(sat, nextNow), "en")
    @Config(qualifiers = "+ar-ldrtl-h844dp") @Test fun ar_full() = shoot("ar_full", full(sat, nextNow), "ar")
    @Config(qualifiers = "+h844dp") @Test fun en_dark_2x_full() = shoot("en_dark_2x_full", full(sat, lateNow), "en", ThemeMode.Dark, 2f)

    private fun shoot(name: String, state: TodayUiState, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    TodayContent(state, fmt, onSelectDate = {}, onBackToToday = {}, onTake = {}, onSkip = {}, onAddMedicine = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today_$name.png")
    }
}
