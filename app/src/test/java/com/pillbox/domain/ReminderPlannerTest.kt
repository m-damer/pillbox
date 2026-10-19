package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val sat = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun med(id: Long, name: String) = Medicine(id = id, personId = 1, name = name)
    private fun fixed(vararg times: LocalTime) = Schedule(sat.minusDays(10), null, Frequency.FixedTimes(times.toList()))
    private val amox = Triple(med(1, "Amoxicillin"), fixed(t(6), t(14), t(22)), emptyList<Pause>())
    private val metf = Triple(med(2, "Metformin"), fixed(t(14), t(20, 30)), emptyList<Pause>())

    @Test fun nextDueIsTheEarliestAcrossMedicines() {
        assertEquals(sat.atTime(14, 0), ReminderPlanner.nextDue(listOf(amox, metf), sat.atTime(9, 0)))
        assertEquals(sat.atTime(20, 30), ReminderPlanner.nextDue(listOf(amox, metf), sat.atTime(14, 0)))
        assertEquals(sat.plusDays(1).atTime(6, 0), ReminderPlanner.nextDue(listOf(amox, metf), sat.atTime(22, 0)))
    }

    @Test fun nextDueIsNullWithNothingScheduled() {
        assertNull(ReminderPlanner.nextDue(emptyList(), sat.atTime(9, 0)))
        val prn = Triple(med(3, "Painkiller"), Schedule(sat, null, Frequency.AsNeeded()), emptyList<Pause>())
        assertNull(ReminderPlanner.nextDue(listOf(prn), sat.atTime(9, 0)))
    }

    @Test fun pausedMedicineDoesNotRing() {
        val paused = Triple(med(1, "A"), fixed(t(9)), listOf(Pause.untilResumed(sat.atStartOfDay())))
        assertNull(ReminderPlanner.nextDue(listOf(paused), sat.atTime(7, 0)))
    }

    @Test fun dueRowsGroupMedicinesAtTheSameTime() {
        val rows = ReminderPlanner.dueRows(listOf(amox, metf), emptyList(), sat.atTime(14, 0))
        assertEquals(listOf("Amoxicillin", "Metformin"), rows.map { it.medicine.name })
    }

    @Test fun dueRowsSkipDosesAlreadyHandled() {
        val taken = DoseEvent(medicineId = 1, planned = sat.atTime(14, 0), taken = sat.atTime(14, 1), status = DoseStatus.TAKEN)
        val rows = ReminderPlanner.dueRows(listOf(amox, metf), listOf(taken), sat.atTime(14, 0))
        assertEquals(listOf("Metformin"), rows.map { it.medicine.name })
        val skipped = DoseEvent(medicineId = 2, planned = sat.atTime(14, 0), status = DoseStatus.SKIPPED)
        assertTrue(ReminderPlanner.dueRows(listOf(amox, metf), listOf(taken, skipped), sat.atTime(14, 0)).isEmpty())
    }

    @Test fun dueRowsEmptyWhenNothingIsPlannedAtThatTime() {
        assertTrue(ReminderPlanner.dueRows(listOf(amox, metf), emptyList(), sat.atTime(15, 0)).isEmpty())
    }

    @Test fun pastMidnightDoseRingsOnItsOwnDay() {
        val night = Triple(med(5, "Syrup"), Schedule(sat.minusDays(1), null, Frequency.EveryNHours(t(20), 8)), emptyList<Pause>())
        assertEquals(sat.plusDays(1).atTime(4, 0), ReminderPlanner.nextDue(listOf(night), sat.atTime(21, 0)))
        assertEquals(1, ReminderPlanner.dueRows(listOf(night), emptyList(), sat.plusDays(1).atTime(4, 0)).size)
    }
}
