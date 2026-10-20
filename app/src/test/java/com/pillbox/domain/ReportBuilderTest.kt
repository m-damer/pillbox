package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(22, 0)
    private fun t(h: Int) = LocalTime.of(h, 0)
    private val metformin = Medicine(id = 1, personId = 1, name = "Metformin", stock = 40.0)
    private val amox = Medicine(id = 2, personId = 1, name = "Amoxicillin", stock = 10.0)
    private val sched = Schedule(today.minusDays(120), null, Frequency.FixedTimes(listOf(t(8), t(20))))
    private val items = listOf(Triple(metformin, sched, emptyList<Pause>()), Triple(amox, sched, emptyList<Pause>()))

    private fun taken(id: Long, day: LocalDate, h: Int, late: Int? = null) =
        DoseEvent(medicineId = id, planned = day.atTime(h, 0), taken = day.atTime(h, 5), status = DoseStatus.TAKEN, lateMinutes = late)

    private fun everything(days: Int = 90) = (0 until days).flatMap { d ->
        listOf(1L, 2L).flatMap { id -> listOf(8, 20).map { taken(id, today.minusDays(d.toLong()), it) } }
    }

    @Test fun periodsChangeTheWindow() {
        val seven = ReportBuilder.build("Ali", items, everything(), now, ReportOptions(days = 7))
        assertEquals(today.minusDays(6), seven.start)
        assertEquals(7, seven.days.size)
        assertEquals(7 * 4, seven.history.total)
        val ninety = ReportBuilder.build("Ali", items, everything(), now, ReportOptions(days = 90))
        assertEquals(90, ninety.days.size)
        assertEquals(90 * 4, ninety.history.total)
    }

    @Test fun perMedicineCountsAddUp() {
        val gone = setOf(1L to today.minusDays(3).atTime(8, 0), 2L to today.minusDays(4).atTime(20, 0))
        val events = everything(30).filterNot { (it.medicineId to it.planned) in gone } +
            taken(2, today.minusDays(4), 20, late = 45) +
            DoseEvent(medicineId = 1, planned = today.minusDays(3).atTime(8, 0), status = DoseStatus.SKIPPED, skipReason = "side_effects")
        val r = ReportBuilder.build("Ali", items, events, now, ReportOptions(days = 30))
        val met = r.medicines.first { it.medicine.id == 1L }
        val amo = r.medicines.first { it.medicine.id == 2L }
        assertEquals(listOf("Amoxicillin", "Metformin"), r.medicines.map { it.medicine.name })
        assertEquals(1, met.missed); assertEquals(0, met.late); assertEquals(59, met.onTime)
        assertEquals(1, amo.late); assertEquals(0, amo.missed); assertEquals(59, amo.onTime)
        assertEquals(r.history.onTime, met.onTime + amo.onTime)
    }

    @Test fun skipReasonTravelsToTheList() {
        val events = everything(30).filterNot { it.medicineId == 1L && it.planned == today.minusDays(3).atTime(8, 0) } +
            DoseEvent(medicineId = 1, planned = today.minusDays(3).atTime(8, 0), status = DoseStatus.SKIPPED, skipReason = "side_effects")
        val r = ReportBuilder.build("Ali", items, events, now, ReportOptions())
        assertEquals("side_effects", r.history.attention.single().reason)
    }

    @Test fun dayMarksFollowWorstDose() {
        val events = everything(30).filterNot { it.planned!!.toLocalDate() == today.minusDays(2) && it.medicineId == 1L && it.planned!!.hour == 8 }
            .map { if (it.planned!!.toLocalDate() == today.minusDays(5) && it.medicineId == 2L && it.planned!!.hour == 20) it.copy(lateMinutes = 90) else it }
        val r = ReportBuilder.build("Ali", items, events, now, ReportOptions())
        assertEquals(ReportDay.MISSED, r.dayMark(today.minusDays(2)))
        assertEquals(ReportDay.LATE, r.dayMark(today.minusDays(5)))
        assertEquals(ReportDay.OK, r.dayMark(today.minusDays(7)))
    }

    @Test fun pendingTodayAndPausedDays() {
        val morning = today.atTime(7, 0)
        val pause = Pause(today.minusDays(4).atStartOfDay(), today.minusDays(2).atStartOfDay())
        val onlyMet = listOf(Triple(metformin, sched, listOf(pause)))
        val events = everything(30).filter { it.medicineId == 1L && it.planned!!.toLocalDate() !in today.minusDays(4)..today.minusDays(3) && it.planned!!.toLocalDate() != today }
        val r = ReportBuilder.build("Ali", onlyMet, events, morning, ReportOptions())
        assertEquals(ReportDay.PENDING, r.dayMark(today))
        assertEquals(ReportDay.PAUSED, r.dayMark(today.minusDays(3)))
    }

    @Test fun stoppedMedicineWithNoDosesInWindowIsLeftOut() {
        val old = metformin.copy(status = MedicineStatus.FINISHED, endedOn = today.minusDays(200))
        val oldSchedule = Schedule(today.minusDays(260), today.minusDays(200), Frequency.FixedTimes(listOf(t(8))))
        val r = ReportBuilder.build("Ali", listOf(Triple(old, oldSchedule, emptyList<Pause>())), emptyList(), now, ReportOptions())
        assertTrue(r.medicines.isEmpty())
    }

    @Test fun createdDateIsToday() =
        assertEquals(today, ReportBuilder.build("Ali", items, emptyList(), now, ReportOptions()).created)
}
