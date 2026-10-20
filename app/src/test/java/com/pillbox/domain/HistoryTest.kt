package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private val today = LocalDate.of(2026, 10, 17) // Saturday
    private val now = today.atTime(22, 0)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val med = Medicine(id = 1, personId = 1, name = "Metformin", stock = 50.0)
    private fun twice(start: LocalDate = today.minusDays(40)) = Schedule(start, null, Frequency.FixedTimes(listOf(t(8), t(20))))
    private fun items(m: Medicine = med, s: Schedule = twice(), p: List<Pause> = emptyList()) = listOf(Triple(m, s, p))
    private fun taken(day: LocalDate, h: Int, late: Int? = null) =
        DoseEvent(medicineId = 1, planned = day.atTime(h, 0), taken = day.atTime(h, 5), status = DoseStatus.TAKEN, lateMinutes = late)
    private fun allTaken() = (1..29).flatMap { d -> listOf(8, 20).map { taken(today.minusDays(d.toLong()), it) } } +
        listOf(8, 20).map { taken(today, it) }

    @Test fun everythingTakenIsOneHundredPercent() {
        val h = HistoryBuilder.build(items(), allTaken(), now)
        assertEquals(100, h.percent)
        assertEquals(60, h.total)
        assertTrue(h.attention.isEmpty())
        assertTrue(h.marks.values.all { it == HistoryMark.ALL_TAKEN })
    }

    @Test fun missedAndSkippedAndLateCountAgainstOnTime() {
        val gone = setOf(today.minusDays(1).atTime(20, 0), today.minusDays(2).atTime(8, 0), today.minusDays(3).atTime(8, 0))
        val events = allTaken().filterNot { it.planned in gone } +
            DoseEvent(medicineId = 1, planned = today.minusDays(2).atTime(8, 0), status = DoseStatus.SKIPPED, skipReason = "x") +
            taken(today.minusDays(3), 8, late = 120)
        val h = HistoryBuilder.build(items(), events, now)
        assertEquals(2, h.missed)
        assertEquals(1, h.late)
        assertEquals(60, h.total)
        assertEquals(95, h.percent) // 57 of 60
        assertEquals(HistoryMark.SOME_MISSED, h.marks[today.minusDays(1)])
        assertEquals(HistoryMark.SOME_MISSED, h.marks[today.minusDays(2)])
        assertEquals(HistoryMark.ALL_TAKEN, h.marks[today.minusDays(3)])
    }

    @Test fun attentionListIsNewestFirst() {
        val events = allTaken().filterNot { it.planned == today.minusDays(1).atTime(20, 0) || it.planned == today.minusDays(5).atTime(8, 0) } +
            taken(today.minusDays(5), 8, late = 90)
        val h = HistoryBuilder.build(items(), events, now)
        assertEquals(listOf(today.minusDays(1).atTime(20, 0), today.minusDays(5).atTime(8, 0)), h.attention.map { it.planned })
        assertEquals(listOf(DoseOutcome.MISSED, DoseOutcome.LATE), h.attention.map { it.outcome })
    }

    @Test fun dayWithNothingTakenIsMissedDay() {
        val events = allTaken().filterNot { it.planned!!.toLocalDate() == today.minusDays(4) }
        assertEquals(HistoryMark.MISSED_DAY, HistoryBuilder.build(items(), events, now).marks[today.minusDays(4)])
    }

    @Test fun todayStillToComeIsPendingAndNotCounted() {
        val morning = now.toLocalDate().atTime(7, 0)
        val h = HistoryBuilder.build(items(), allTaken().filter { it.planned!!.toLocalDate() != today }, morning)
        assertEquals(HistoryMark.PENDING, h.marks[today])
        assertEquals(58, h.total) // 29 earlier days x 2
    }

    @Test fun pausedDaysAreNotCounted() {
        val pause = Pause(today.minusDays(6).atStartOfDay(), today.minusDays(3).atStartOfDay())
        val events = allTaken().filterNot { it.planned!!.toLocalDate() in today.minusDays(6)..today.minusDays(4) }
        val h = HistoryBuilder.build(items(p = listOf(pause)), events, now)
        assertEquals(HistoryMark.PAUSED, h.marks[today.minusDays(5)])
        assertEquals(100, h.percent)
        assertEquals(60 - 6, h.total)
    }

    @Test fun stoppedMedicineStopsCounting() {
        val stopped = med.copy(status = MedicineStatus.STOPPED, endedOn = today.minusDays(10))
        val events = allTaken().filter { it.planned!!.toLocalDate() < today.minusDays(10) }
        val h = HistoryBuilder.build(items(m = stopped), events, now)
        assertEquals(100, h.percent)
        assertEquals(HistoryMark.NONE, h.marks[today.minusDays(5)])
    }

    @Test fun asNeededAndNoMedicinesGiveNoHistory() {
        assertNull(HistoryBuilder.build(emptyList(), emptyList(), now).percent)
        val prn = Schedule(today.minusDays(5), null, Frequency.AsNeeded())
        assertNull(HistoryBuilder.build(items(s = prn), emptyList(), now).percent)
    }

    @Test fun medicineStartedYesterdayOnlyCountsSinceThen() {
        val h = HistoryBuilder.build(items(s = twice(today.minusDays(1))), emptyList(), now)
        assertEquals(3, h.total) // yesterday x2, today 8:00 missed; today 20:00 can still be taken until midnight
        assertEquals(0, h.percent)
        assertEquals(HistoryMark.NONE, h.marks[today.minusDays(5)])
    }

    @Test fun calendarWeeksStartOnChosenDay() {
        val h = HistoryBuilder.build(items(), allTaken(), now)
        val weeks = h.weeks(DayOfWeek.SUNDAY)
        // 18 Sep (Fri) .. 17 Oct (Sat): Sunday-first weeks, leading blanks 5, last row ends on Saturday 17th.
        assertEquals(5, weeks.size)
        assertEquals(5, weeks.first().count { it.date == null })
        assertEquals(LocalDate.of(2026, 9, 18), weeks.first().first { it.date != null }.date)
        assertEquals(today, weeks.last().last().date)
        assertTrue(weeks.all { it.size == 7 })
        val monday = h.weeks(DayOfWeek.MONDAY)
        assertEquals(4, monday.first().count { it.date == null })
        assertEquals(HistoryMark.FUTURE, monday.last().last().mark) // Sunday 18th is after today
    }

    @Test fun midnightDoseBelongsToTheDayItRings() {
        val hours = Schedule(today.minusDays(40), null, Frequency.EveryNHours(t(20), 8, DayPattern.EveryDay))
        val h = HistoryBuilder.build(listOf(Triple(med, hours, emptyList())), emptyList(), now)
        assertTrue(h.records.all { it.planned.toLocalDate() in h.start..today })
    }
}
