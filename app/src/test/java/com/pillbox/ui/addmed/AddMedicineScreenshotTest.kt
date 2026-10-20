package com.pillbox.ui.addmed

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.AddMedicineForm
import com.pillbox.domain.DaysChoice
import com.pillbox.domain.FreqChoice
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Add medicine steps in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class AddMedicineScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val base = AddMedicineForm(name = "Metformin", strength = "500")
    private val hours = base.withFreq(FreqChoice.HOURS).copy(hours = 2)
    private val days = base.copy(days = DaysChoice.SPECIFIC, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

    @Test fun en_step1() = shoot("en_step1", AddMedUiState(base, 1), "en")
    @Test fun en_step1_error() = shoot("en_step1_error", AddMedUiState(AddMedicineForm(boxCount = null), 1, showErrors = true), "en")
    @Test fun en_step2() = shoot("en_step2", AddMedUiState(base, 2), "en")
    @Test fun en_step2_hours() = shoot("en_step2_hours", AddMedUiState(hours, 2), "en")
    @Test fun en_step2_prn() = shoot("en_step2_prn", AddMedUiState(base.withFreq(FreqChoice.AS_NEEDED), 2), "en")
    @Test fun en_step3_days() = shoot("en_step3_days", AddMedUiState(days, 3), "en")
    @Test fun en_dark_2x_step1() = shoot("en_dark_2x_step1", AddMedUiState(base, 1), "en", ThemeMode.Dark, 2f)
    @Test fun en_light_2x_step2() = shoot("en_light_2x_step2", AddMedUiState(base.withFreq(FreqChoice.THREE), 2), "en", ThemeMode.Light, 2f)

    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_step1() = shoot("ar_step1", AddMedUiState(base, 1), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_step2() = shoot("ar_step2", AddMedUiState(base, 2), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_step2_hours() = shoot("ar_step2_hours", AddMedUiState(hours, 2), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_step3_days() = shoot("ar_step3_days", AddMedUiState(days, 3), "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x_step3() = shoot("ar_dark_2x_step3", AddMedUiState(days, 3), "ar", ThemeMode.Dark, 2f)

    private val amox = com.pillbox.domain.Medicine(id = 1, personId = 1, name = "Amoxicillin", unit = com.pillbox.domain.DoseUnit.CAPSULE, stock = 20.0) to
        com.pillbox.domain.Schedule(today.minusDays(3), today.plusDays(6), com.pillbox.domain.Frequency.FixedTimes(listOf(java.time.LocalTime.of(6, 0), java.time.LocalTime.of(14, 0), java.time.LocalTime.of(22, 0))), com.pillbox.domain.WithFood.WITH_MEAL)
    private val review = AddMedUiState(base.copy(name = "Metformin").let { it.copy(customTimes = listOf(java.time.LocalTime.of(14, 0), java.time.LocalTime.of(20, 30))) }, 4, others = listOf(amox))

    @Config(qualifiers = "w360dp-h844dp-xxhdpi") @Test fun en_review() = shoot("en_review", review, "en")
    @Config(qualifiers = "+ar-ldrtl-h844dp") @Test fun ar_review() = shoot("ar_review", review, "ar")
    @Config(qualifiers = "w360dp-h844dp-xxhdpi") @Test fun en_dark_2x_review() = shoot("en_dark_2x_review", review, "en", ThemeMode.Dark, 2f)

    private fun shoot(name: String, state: AddMedUiState, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    AddMedicineContent(state, fmt, today, edit = {}, onBack = {}, onNext = {}, onSave = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/addmed_$name.png")
    }
}
