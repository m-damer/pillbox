package com.pillbox.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Schedule
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBox
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

/** The "When did you take it?" sheet: log a forgotten dose, change a logged time, a time that has not come yet. en/ar, light/dark, 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class LogSheetScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 16)
    private val now = LocalDateTime.of(2026, 10, 17, 13, 30)
    private val amox = Medicine(id = 1, personId = 1, name = "Amoxicillin", unit = DoseUnit.CAPSULE, stock = 20.0)
    private val sched = Schedule(day.minusDays(3), null, Frequency.FixedTimes(listOf(LocalTime.of(14, 0))))
    private fun row(event: DoseEvent? = null) = DoseRow(amox, sched, day.atTime(14, 0), event, event == null)
    private val taken = DoseEvent(medicineId = 1, planned = day.atTime(14, 0), taken = day.atTime(14, 35), status = DoseStatus.TAKEN, lateMinutes = 35, loggedLater = true)

    @Test fun log_en() = shoot("log_en", "en") { TodaySheet.LogTaken(row(), edit = false) }
    @Test fun log_en_dark_2x() = shoot("log_en_dark_2x", "en", ThemeMode.Dark, 2f) { TodaySheet.LogTaken(row(), edit = false) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun log_ar() = shoot("log_ar", "ar") { TodaySheet.LogTaken(row(), edit = false) }
    @Test fun change_en() = shoot("change_en", "en") { TodaySheet.LogTaken(row(taken), edit = true) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun change_ar() = shoot("change_ar", "ar") { TodaySheet.LogTaken(row(taken), edit = true) }
    // Today's 2 PM dose when it is only 1:30 PM: the planned time has not come, so the chosen time is in the future.
    @Test fun future_en() = shoot("future_en", "en", nowOverride = LocalDateTime.of(2026, 10, 16, 13, 30)) { TodaySheet.LogTaken(row(), edit = false) }

    private fun fmt(ar: Boolean) = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, nowOverride: LocalDateTime = now, sheet: () -> TodaySheet.LogTaken) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Column(Modifier.fillMaxSize().background(PillBox.colors.card).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LogTakenSheet(sheet(), fmt(lang == "ar"), onSave = {}, onCancel = {}, now = nowOverride)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/log_sheet_$name.png")
    }
}
