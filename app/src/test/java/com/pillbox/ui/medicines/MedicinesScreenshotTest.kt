package com.pillbox.ui.medicines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Schedule
import com.pillbox.domain.StockMath
import com.pillbox.domain.WeekSummary
import com.pillbox.domain.WithFood
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

/** Medicines list and Medicine detail in en/ar, light/dark, font scale 1.0/2.0. Saved to app/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class MedicinesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 19)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val metformin = Medicine(
        id = 1, personId = 1, name = "Metformin", strength = "850 mg", unit = DoseUnit.TABLET, stock = 8.0, packSize = 60,
    )
    private val metformSchedule = Schedule(today.minusDays(60), null, Frequency.FixedTimes(listOf(t(14), t(20, 30))), WithFood.AFTER_MEAL)
    private val amox = Medicine(
        id = 2, personId = 1, name = "Amoxicillin", strength = "500 mg", unit = DoseUnit.CAPSULE, stock = 18.0, packSize = 21,
    )
    private val amoxSchedule = Schedule(today.minusDays(1), today.plusDays(4), Frequency.FixedTimes(listOf(t(6), t(14), t(22))), WithFood.WITH_MEAL)
    private val metformItem = MedicineWithSchedule(metformin, metformSchedule, emptyList())
    private val amoxItem = MedicineWithSchedule(amox, amoxSchedule, emptyList())
    private val ended = amox.copy(status = MedicineStatus.FINISHED, endedOn = today.minusDays(16))
    private val stopped = Medicine(
        id = 3, personId = 1, name = "Ibuprofen", strength = "400 mg", unit = DoseUnit.TABLET, status = MedicineStatus.STOPPED, endedOn = today.minusDays(21),
    )

    private val listState = MedicinesUiState(
        loaded = true,
        active = listOf(metformItem, amoxItem).map { MedRow(it, StockMath.info(it.medicine, it.schedule, today)) },
        finished = listOf(
            MedicineWithSchedule(ended, amoxSchedule, emptyList()),
            MedicineWithSchedule(stopped, metformSchedule, emptyList()),
        ),
    )

    private fun events() = (1..6).flatMap { d ->
        listOf(t(14), t(20, 30)).filter { !(d == 5 && it == t(20, 30)) }.map {
            DoseEvent(planned = today.minusDays(d.toLong()).atTime(it), taken = today.minusDays(d.toLong()).atTime(it).plusMinutes(5), status = DoseStatus.TAKEN)
        }
    }

    private val detail = DetailUiState(
        loaded = true, item = metformItem, info = StockMath.info(metformin, metformSchedule, today),
        week = WeekSummary.build(metformSchedule, emptyList(), events(), today),
    )
    private val finishedDetail = DetailUiState(
        loaded = true, item = MedicineWithSchedule(ended, amoxSchedule, emptyList()), courseTaken = 19, courseTotal = 21,
    )

    private val pause = com.pillbox.domain.Pause.forDays(today.atTime(10, 0), 8)
    private val pausedItem = metformItem.copy(pauses = listOf(pause))
    private val pausedDetail = detail.copy(item = pausedItem, pause = pause)
    private val pausedList = listState.copy(
        active = listOf(pausedItem, amoxItem).mapIndexed { i, it ->
            MedRow(it, StockMath.info(it.medicine, it.schedule, today), if (i == 0) pause else null)
        },
        pausedCount = 1,
    )

    @Test fun en_detail_paused() = shoot("en_detail_paused", "en") { f -> MedicineDetailContent(pausedDetail, f) }
    @Test fun en_list_paused() = shoot("en_list_paused", "en") { f -> MedicinesContent(pausedList, f, false, {}, {}, {}) }
    @Test fun en_dark_2x_detail_paused() = shoot("en_dark_2x_detail_paused", "en", ThemeMode.Dark, 2f) { f -> MedicineDetailContent(pausedDetail, f) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_detail_paused() = shoot("ar_detail_paused", "ar") { f -> MedicineDetailContent(pausedDetail, f) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_list_paused() = shoot("ar_list_paused", "ar") { f -> MedicinesContent(pausedList, f, false, {}, {}, {}) }

    // Expiry: dates relative to the real month, because the cards compare with the device date.
    private val soonMetformin = metformItem.copy(medicine = metformin.copy(expiry = java.time.YearMonth.now()))
    private val expiredAmox = amoxItem.copy(medicine = amox.copy(expiry = java.time.YearMonth.now().minusMonths(2)))
    private val expiryList = listState.copy(
        active = listOf(soonMetformin, expiredAmox).map { MedRow(it, StockMath.info(it.medicine, it.schedule, today)) },
    )
    private val expiryDetailSoon = detail.copy(item = soonMetformin)
    private val expiryDetailExpired = detail.copy(item = expiredAmox, info = StockMath.info(expiredAmox.medicine, expiredAmox.schedule, today))
    @Test fun en_list_expiry() = shoot("en_list_expiry", "en") { f -> MedicinesContent(expiryList, f, false, {}, {}, {}) }
    @Test fun en_detail_expiry_soon() = shoot("en_detail_expiry_soon", "en") { f -> MedicineDetailContent(expiryDetailSoon, f) }
    @Test fun en_dark_2x_detail_expired() = shoot("en_dark_2x_detail_expired", "en", ThemeMode.Dark, 2f) { f -> MedicineDetailContent(expiryDetailExpired, f) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_list_expiry() = shoot("ar_list_expiry", "ar") { f -> MedicinesContent(expiryList, f, false, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_detail_expired() = shoot("ar_detail_expired", "ar") { f -> MedicineDetailContent(expiryDetailExpired, f) }

    // Boxes with their own expiry: box 2 is in use by the user's choice, box 1 waits.
    private val packsMetformin = metformin.copy(
        stock = 40.0,
        packs = listOf(
            com.pillbox.domain.Pack(2, 10.0, java.time.YearMonth.now().plusMonths(14)),
            com.pillbox.domain.Pack(1, 30.0, java.time.YearMonth.now().plusMonths(2)),
        ),
        expiry = java.time.YearMonth.now().plusMonths(14),
    )
    private val packsDetail = detail.copy(item = metformItem.copy(medicine = packsMetformin), info = StockMath.info(packsMetformin, metformSchedule, today))
    @Test fun en_detail_packs() = shoot("en_detail_packs", "en") { f -> MedicineDetailContent(packsDetail, f) }
    @Test fun en_dark_2x_detail_packs() = shoot("en_dark_2x_detail_packs", "en", ThemeMode.Dark, 2f) { f -> MedicineDetailContent(packsDetail, f) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_detail_packs() = shoot("ar_detail_packs", "ar") { f -> MedicineDetailContent(packsDetail, f) }

    @Test fun en_list() = shoot("en_list", "en") { f -> MedicinesContent(listState, f, false, {}, {}, {}) }
    @Test fun en_list_finished() = shoot("en_list_finished", "en") { f -> MedicinesContent(listState, f, true, {}, {}, {}) }
    @Test fun en_detail() = shoot("en_detail", "en") { f -> MedicineDetailContent(detail, f) }
    @Test fun en_detail_finished() = shoot("en_detail_finished", "en") { f -> MedicineDetailContent(finishedDetail, f) }
    @Test fun en_dark_2x_detail() = shoot("en_dark_2x_detail", "en", ThemeMode.Dark, 2f) { f -> MedicineDetailContent(detail, f) }
    @Test fun en_dark_2x_list() = shoot("en_dark_2x_list", "en", ThemeMode.Dark, 2f) { f -> MedicinesContent(listState, f, false, {}, {}, {}) }

    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_list() = shoot("ar_list", "ar") { f -> MedicinesContent(listState, f, false, {}, {}, {}) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_detail() = shoot("ar_detail", "ar") { f -> MedicineDetailContent(detail, f) }
    @Config(qualifiers = "+ar-ldrtl") @Test fun ar_dark_2x_detail() = shoot("ar_dark_2x_detail", "ar", ThemeMode.Dark, 2f) { f -> MedicineDetailContent(detail, f) }

    private fun shoot(name: String, lang: String, mode: ThemeMode = ThemeMode.Light, fontScale: Float = 1f, content: @Composable (Formatter) -> Unit) {
        val ar = lang == "ar"
        val fmt = Formatter(arabicDigits = ar, is24Hour = false, am = if (ar) "ص" else "AM", pm = if (ar) "م" else "PM", rtl = ar)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                PillBoxTheme(mode = mode) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content(fmt) }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/medicines_$name.png")
    }
}
