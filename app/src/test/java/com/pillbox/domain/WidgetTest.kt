package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTest {
    private val day = LocalDate.of(2026, 10, 17)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(day, LocalTime.of(h, m))

    private fun item(id: Long, person: Long, name: String, vararg times: LocalTime): Triple<Medicine, Schedule, List<Pause>> {
        val med = Medicine(id = id, personId = person, name = name)
        val schedule = Schedule(start = day.minusDays(10), frequency = Frequency.FixedTimes(times.toList()))
        return Triple(med, schedule, emptyList())
    }

    private val items = listOf(
        item(1, 1, "Amoxicillin", LocalTime.of(8, 0), LocalTime.of(14, 0)),
        item(2, 1, "Metformin", LocalTime.of(14, 0)),
        item(3, 2, "Mom pill", LocalTime.of(9, 0)),
    )

    private fun taken(med: Long, h: Int) = DoseEvent(medicineId = med, planned = at(h), taken = at(h), status = DoseStatus.TAKEN)

    @Test fun nextGroupIsTheEarliestWaiting() {
        val m = WidgetBuilder.build(items, listOf(taken(1, 8)), at(13, 35), WidgetSettings(), myPersonId = 1)
        assertEquals(at(14), m.at)
        assertFalse(m.late)
        assertEquals(25, m.minutes)
        assertEquals(listOf("Amoxicillin", "Metformin"), m.nextRows.map { it.medicine.name })
    }

    @Test fun otherPeopleAreLeftOutUnlessChosen() {
        val me = WidgetBuilder.build(items, emptyList(), at(7), WidgetSettings(), myPersonId = 1)
        assertEquals(at(8), me.at)
        val mom = WidgetBuilder.build(items, emptyList(), at(7), WidgetSettings(personId = 2), myPersonId = 1)
        assertEquals(at(9), mom.at)
        assertEquals(listOf("Mom pill"), mom.rows.map { it.medicine.name })
    }

    @Test fun lateDosesComeFirstWithMinutesLate() {
        val m = WidgetBuilder.build(items, emptyList(), at(8, 40), WidgetSettings(), myPersonId = 1)
        assertEquals(at(8), m.at)
        assertTrue(m.late)
        assertEquals(40, m.minutes)
    }

    @Test fun justPastTheTimeIsNotLateYetAndShowsNow() {
        val m = WidgetBuilder.build(items, emptyList(), at(8, 10), WidgetSettings(), myPersonId = 1)
        assertEquals(at(8), m.at)
        assertFalse(m.late)
        assertEquals(0, m.minutes)
    }

    @Test fun rowsListEverythingWaitingAndNextRowsOnlyTheFirstGroup() {
        val m = WidgetBuilder.build(items, emptyList(), at(7), WidgetSettings(), myPersonId = 1)
        assertEquals(listOf("Amoxicillin", "Amoxicillin", "Metformin"), m.rows.map { it.medicine.name })
        assertEquals(1, m.nextRows.size)
    }

    @Test fun allDoneWhenEverythingIsTaken() {
        val m = WidgetBuilder.build(items, listOf(taken(1, 8), taken(1, 14), taken(2, 14)), at(15), WidgetSettings(), myPersonId = 1)
        assertNull(m.at)
        assertTrue(m.allDone)
        assertTrue(m.rows.isEmpty())
        assertEquals(LocalDateTime.of(day.plusDays(1), LocalTime.of(8, 0)), m.following)
    }

    @Test fun nothingPlannedIsNotAllDone() {
        val m = WidgetBuilder.build(emptyList(), emptyList(), at(9), WidgetSettings(), myPersonId = 1)
        assertNull(m.at)
        assertFalse(m.allDone)
    }

    @Test fun rowsThatFitFollowTheHeight() {
        assertEquals(1, WidgetBuilder.rowsThatFit(100f, true, 5))
        assertEquals(2, WidgetBuilder.rowsThatFit(260f, true, 2))
        assertEquals(5, WidgetBuilder.rowsThatFit(600f, true, 5))
        assertEquals(3, WidgetBuilder.rowsThatFit(300f, false, 5))
    }
}
