package com.pillbox.ui.prn

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Medicine
import com.pillbox.domain.PrnRules
import com.pillbox.ui.Formatter
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** As-needed log (after a dose, and with the daily limit reached) in en/ar, light/dark, font scale 1.0/2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h844dp-xxhdpi")
class PrnScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val d = LocalDate.now()
    private val med = Medicine(id = 1, personId = 1, name = "Paracetamol", strength = "500 mg", unit = DoseUnit.TABLET, stock = 20.0)
    private fun ev(h: Int, m: Int, early: Boolean = false) = DoseEvent(medicineId = 1, planned = null, taken = d.atTime(h, m), status = DoseStatus.TAKEN, early = early)

    private val two = listOf(ev(14, 5), ev(8, 5))
    private val four = listOf(ev(21, 5), ev(15, 5), ev(9, 5), ev(3, 0, early = true))
    private val nowTwo = d.atTime(14, 10)
    private val nowFour = d.atTime(21, 30)

    @Test fun en_log() = shoot("en_log", two, nowTwo, "en")
    @Test fun en_limit() = shoot("en_limit", four, nowFour, "en")
    @Test fun en_dark_2x_log() = shoot("en_dark_2x_log", two, nowTwo, "en", ThemeMode.Dark, 2f)
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_log() = shoot("ar_log", two, nowTwo, "ar")
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_limit() = shoot("ar_limit", four, nowFour, "ar")

    private fun shoot(name: String, doses: List<DoseEvent>, now: LocalDateTime, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        val day = PrnRules.day(now, doses.mapNotNull { it.taken }, 360, 4)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    PrnContent(med, day, doses, fmt, justLogged = !day.limitReached, onBack = {}, onTakeNow = {}, onUndo = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/prn_$name.png")
    }
}
