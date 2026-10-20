package com.pillbox.ui.report

import android.content.Context
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.ReportBuilder
import com.pillbox.domain.ReportOptions
import com.pillbox.domain.Schedule
import com.pillbox.ui.Formatter
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The PDF is made without crashing, in English and Arabic, for every period, and long lists run onto more pages. */
@RunWith(RobolectricTestRunner::class)
// PdfDocument only works in the legacy graphics mode (in native mode it reports "document is closed").
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [35])
class ReportPdfTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(22, 0)
    private fun t(h: Int) = LocalTime.of(h, 0)
    private val med = Medicine(id = 1, personId = 1, name = "Metformin", strength = "850 mg", stock = 40.0)
    private val schedule = Schedule(today.minusDays(120), null, Frequency.FixedTimes(listOf(t(8), t(14), t(20))))
    private val items = listOf(Triple(med, schedule, emptyList<Pause>()))

    private fun model(days: Int, allMissed: Boolean) = ReportBuilder.build(
        "Ali Hassan", items,
        if (allMissed) emptyList() else (0 until days).flatMap { d -> listOf(8, 14, 20).map { h ->
            DoseEvent(medicineId = 1, planned = today.minusDays(d.toLong()).atTime(h, 0), taken = today.minusDays(d.toLong()).atTime(h, 5), status = DoseStatus.TAKEN)
        } },
        now, ReportOptions(days = days),
    )

    private fun pages(m: com.pillbox.domain.ReportModel): Int {
        val context = RuntimeEnvironment.getApplication() as Context
        val out = ByteArrayOutputStream()
        val n = try {
            ReportPdf(context, Formatter.from(context)).write(m, out)
        } catch (e: Throwable) {
            // Put the frames in the message so the CI summary shows where it happened.
            val frames = generateSequence(e) { it.cause }.flatMap { c -> listOf("Caused by: $c") + c.stackTrace.filter { it.className.contains("pillbox") }.take(4).map { "Caused by: at $it" } }
            throw AssertionError("DIAG\n" + frames.joinToString("\n"))
        }
        assertTrue(String(out.toByteArray(), 0, 5, Charsets.ISO_8859_1).startsWith("%PDF"))
        return n
    }

    @Test fun smallReportIsOnePage() = assertEquals(1, pages(model(7, allMissed = false)))

    @Test fun everyPeriodWorks() = ReportOptions.PERIODS.forEach { assertTrue(pages(model(it, allMissed = false)) >= 1) }

    @Test fun manyMissedDosesContinueOnMorePages() = assertTrue(pages(model(90, allMissed = true)) > 1)

    @Test fun optionsCanBeTurnedOff() {
        val m = model(30, false).let { it.copy(options = ReportOptions(30, medicines = false, dayByDay = false, missedLate = false)) }
        assertEquals(1, pages(m))
    }

    @Config(qualifiers = "ar-rSY") @Test fun arabicWorks() = assertTrue(pages(model(30, allMissed = true)) >= 1)
}
