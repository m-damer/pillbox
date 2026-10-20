package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillPlannerTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val now = today.atTime(8, 0)
    private fun twice() = Schedule(today.minusDays(30), null, Frequency.FixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), DayPattern.EveryDay))
    private fun med(id: Long, stock: Double, person: Long = 1, status: MedicineStatus = MedicineStatus.ACTIVE) =
        Medicine(id = id, personId = person, name = "M$id", stock = stock, packSize = 60, status = status)
    private fun item(m: Medicine, pauses: List<Pause> = emptyList()) = Triple(m, twice() as Schedule?, pauses)
    private val five = RefillSettings(daysBefore = 5)

    @Test fun nextFireTodayWhenStillAhead() =
        assertEquals(today.atTime(9, 0), RefillPlanner.nextFire(five, now))

    @Test fun nextFireTomorrowWhenPassedOrNow() {
        assertEquals(today.plusDays(1).atTime(9, 0), RefillPlanner.nextFire(five, today.atTime(9, 0)))
        assertEquals(today.plusDays(1).atTime(9, 0), RefillPlanner.nextFire(five, today.atTime(15, 30)))
    }

    @Test fun lowStockIsDueWithRunOutDate() {
        // 8 tablets, 2 a day = 4 days left: within 5 days.
        val due = RefillPlanner.due(listOf(item(med(1, 8.0))), five, now, 1)
        assertEquals(1, due.size)
        assertEquals(4, due[0].daysLeft)
        assertEquals(today.plusDays(4), due[0].runsOutOn)
        assertEquals(false, due[0].out)
    }

    @Test fun enoughStockIsNotDue() =
        assertTrue(RefillPlanner.due(listOf(item(med(1, 40.0))), five, now, 1).isEmpty())

    @Test fun changingDaysChangesWhoIsDue() {
        val items = listOf(item(med(1, 14.0))) // 7 days left
        assertTrue(RefillPlanner.due(items, RefillSettings(daysBefore = 5), now, 1).isEmpty())
        assertEquals(1, RefillPlanner.due(items, RefillSettings(daysBefore = 7), now, 1).size)
    }

    @Test fun outOfStockIsDueAndFirst() {
        val due = RefillPlanner.due(listOf(item(med(1, 6.0)), item(med(2, 0.0))), five, now, 1)
        assertEquals(listOf(2L, 1L), due.sortedByDescending { it.out }.map { it.medicine.id })
        assertTrue(due.first { it.medicine.id == 2L }.out)
    }

    @Test fun sortedByDaysLeft() {
        val due = RefillPlanner.due(listOf(item(med(1, 8.0)), item(med(2, 4.0))), five, now, 1)
        assertEquals(listOf(2L, 1L), due.map { it.medicine.id })
    }

    @Test fun pausedMedicineGetsNoAlert() {
        val pause = Pause(from = now.minusDays(1), until = now.plusDays(3))
        assertTrue(RefillPlanner.due(listOf(item(med(1, 8.0), listOf(pause))), five, now, 1).isEmpty())
    }

    @Test fun finishedMedicineGetsNoAlert() =
        assertTrue(RefillPlanner.due(listOf(item(med(1, 8.0, status = MedicineStatus.FINISHED))), five, now, 1).isEmpty())

    @Test fun asNeededMedicineHasNoDaysSoNoAlertUnlessOut() {
        val asNeeded = Schedule(today, null, Frequency.AsNeeded())
        assertTrue(RefillPlanner.due(listOf(Triple(med(1, 3.0), asNeeded as Schedule?, emptyList())), five, now, 1).isEmpty())
        assertEquals(1, RefillPlanner.due(listOf(Triple(med(1, 0.0), asNeeded as Schedule?, emptyList())), five, now, 1).size)
    }

    @Test fun includePeopleOffKeepsOnlyMe() {
        val items = listOf(item(med(1, 8.0, person = 1)), item(med(2, 8.0, person = 2)))
        assertEquals(2, RefillPlanner.due(items, RefillSettings(includePeople = true), now, 1).size)
        assertEquals(listOf(1L), RefillPlanner.due(items, RefillSettings(includePeople = false), now, 1).map { it.medicine.id })
    }

    @Test fun firstAlertDate() {
        assertEquals(today, RefillPlanner.firstAlertDate(today, 4, 5))
        assertEquals(today, RefillPlanner.firstAlertDate(today, 5, 5))
        assertEquals(today.plusDays(3), RefillPlanner.firstAlertDate(today, 10, 7))
    }

    @Test fun clampDays() {
        assertEquals(1, RefillSettings.clampDays(0))
        assertEquals(30, RefillSettings.clampDays(99))
        assertEquals(12, RefillSettings.clampDays(12))
    }
}
