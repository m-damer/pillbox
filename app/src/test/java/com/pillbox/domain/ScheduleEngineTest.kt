package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {
    private val sat = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun hours(first: LocalTime, n: Int, start: LocalDate = sat, end: LocalDate? = null) =
        Schedule(start, end, Frequency.EveryNHours(first, n))
    private fun fixed(vararg times: LocalTime, days: DayPattern = DayPattern.EveryDay, start: LocalDate = sat, end: LocalDate? = null) =
        Schedule(start, end, Frequency.FixedTimes(times.toList(), days))
    private fun hoursOf(list: List<LocalDateTime>) = list.map { it.hour }

    // ---- fixed times ----
    @Test fun twiceADay() {
        val s = fixed(t(8), t(20))
        assertEquals(listOf(sat.atTime(8, 0), sat.atTime(20, 0)), ScheduleEngine.plannedDoses(s, sat))
    }

    @Test fun nothingBeforeStartOrAfterEnd() {
        val s = fixed(t(8), start = sat, end = sat.plusDays(2))
        assertTrue(ScheduleEngine.plannedDoses(s, sat.minusDays(1)).isEmpty())
        assertEquals(1, ScheduleEngine.plannedDoses(s, sat.plusDays(2)).size)
        assertTrue(ScheduleEngine.plannedDoses(s, sat.plusDays(3)).isEmpty())
    }

    @Test fun timesAreSortedEvenIfGivenOutOfOrder() {
        val s = fixed(t(20), t(8), t(14))
        assertEquals(listOf(8, 14, 20), hoursOf(ScheduleEngine.plannedDoses(s, sat)))
    }

    @Test fun chosenDaysOnlyMonWedFri() {
        val s = fixed(t(9), days = DayPattern.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), start = LocalDate.of(2026, 10, 12))
        val ran = (12..18).filter { ScheduleEngine.plannedDoses(s, LocalDate.of(2026, 10, it)).isNotEmpty() }
        assertEquals(listOf(12, 14, 16), ran)
    }

    @Test fun everyThreeDaysCountsFromStart() {
        val s = fixed(t(9), days = DayPattern.EveryNDays(3, sat))
        val ran = (0..9).filter { ScheduleEngine.plannedDoses(s, sat.plusDays(it.toLong())).isNotEmpty() }
        assertEquals(listOf(0, 3, 6, 9), ran)
    }

    // ---- every few hours ----
    @Test fun everyTwoHoursFromEightGivesTwelveDosesAroundTheClock() {
        val s = hours(t(8), 2)
        val day = sat.plusDays(1) // a day that also receives the previous cycle's after-midnight doses
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22), hoursOf(ScheduleEngine.plannedDoses(s, day)))
    }

    @Test fun everyFiveHoursFromEightGivesFourDosesAndDropsFourAm() {
        val s = hours(t(8), 5)
        assertEquals(listOf(8, 13, 18, 23), hoursOf(ScheduleEngine.plannedDoses(s, sat.plusDays(1))))
    }

    @Test fun gapIsNeverBelowN() {
        for (n in 1..24) for (first in listOf(t(0), t(6, 30), t(8), t(23, 59))) {
            val s = hours(first, n)
            val doses = (ScheduleEngine.plannedDoses(s, sat.plusDays(1)) + ScheduleEngine.plannedDoses(s, sat.plusDays(2))).sorted()
            doses.zipWithNext { a, b -> assertTrue("n=$n first=$first $a->$b", java.time.Duration.between(a, b).toHours() >= n) }
            assertEquals(24 / n, ScheduleEngine.plannedDoses(s, sat.plusDays(1)).size)
        }
    }

    @Test fun pastMidnightDoseBelongsToTheDayItRings() {
        val s = hours(t(20), 3) // 20, 23, 2, 5, 8, 11, 14, 17
        val first = ScheduleEngine.plannedDoses(s, sat) // start day: only the cycle that starts today
        assertEquals(listOf(20, 23), hoursOf(first))
        val next = ScheduleEngine.plannedDoses(s, sat.plusDays(1))
        assertEquals(listOf(2, 5, 8, 11, 14, 17, 20, 23), hoursOf(next))
        assertTrue(next.all { it.toLocalDate() == sat.plusDays(1) })
    }

    @Test fun cycleStopsAfterEndDateButFinishesItsOwnWrap() {
        val s = hours(t(8), 2, end = sat)
        val afterEnd = ScheduleEngine.plannedDoses(s, sat.plusDays(1))
        assertEquals(listOf(0, 2, 4, 6), hoursOf(afterEnd))
        assertTrue(ScheduleEngine.plannedDoses(s, sat.plusDays(2)).isEmpty())
    }

    @Test fun everyTwentyFourHoursIsOneDose() {
        assertEquals(1, ScheduleEngine.plannedDoses(hours(t(8), 24), sat.plusDays(3)).size)
    }

    // ---- as needed ----
    @Test fun asNeededHasNoPlannedDoses() {
        val s = Schedule(sat, null, Frequency.AsNeeded())
        assertTrue(ScheduleEngine.plannedDoses(s, sat).isEmpty())
    }

    // ---- pause ----
    @Test fun timedPauseEndsAtNineAmOnTheEndDate() {
        val p = Pause.forDays(sat.atTime(14, 0), 3)
        assertEquals(sat.plusDays(3).atTime(9, 0), p.until)
        assertTrue(p.covers(sat.plusDays(3).atTime(8, 59)))
        assertFalse(p.covers(sat.plusDays(3).atTime(9, 0)))
    }

    @Test fun pauseHidesDosesAndMarksDayPaused() {
        val s = fixed(t(8), t(20))
        val p = Pause.forDays(sat.atTime(7, 0), 1) // until Sun 9:00
        assertTrue(ScheduleEngine.visibleDoses(s, listOf(p), sat).isEmpty())
        assertTrue(ScheduleEngine.isPausedDay(s, listOf(p), sat))
        // Sunday: 8:00 still paused, 20:00 visible -> not a fully paused day
        assertEquals(listOf(20), hoursOf(ScheduleEngine.visibleDoses(s, listOf(p), sat.plusDays(1))))
        assertFalse(ScheduleEngine.isPausedDay(s, listOf(p), sat.plusDays(1)))
    }

    @Test fun untilResumedPauseNeverEnds() {
        val p = Pause.untilResumed(sat.atTime(8, 0))
        assertTrue(p.covers(sat.plusDays(400).atTime(8, 0)))
    }

    @Test fun everyNDaysRestartsFromTheResumeDay() {
        val s = fixed(t(9), days = DayPattern.EveryNDays(3, sat))
        val resumed = ScheduleEngine.resumedOn(s, sat.plusDays(4)) // resume on day +4
        val ran = (4..12).filter { ScheduleEngine.plannedDoses(resumed, sat.plusDays(it.toLong())).isNotEmpty() }
        assertEquals(listOf(4, 7, 10), ran)
    }

    @Test fun resumeDoesNotChangeOtherSchedules() {
        val s = fixed(t(9), days = DayPattern.DaysOfWeek(setOf(DayOfWeek.MONDAY)))
        assertEquals(s, ScheduleEngine.resumedOn(s, sat.plusDays(4)))
        val h = hours(t(8), 4)
        assertEquals(h, ScheduleEngine.resumedOn(h, sat.plusDays(4)))
    }

    // ---- stock ----
    @Test fun daysLeft() {
        assertEquals(4, ScheduleEngine.daysLeft(8.0, 1.0, 2.0))
        assertEquals(34, ScheduleEngine.daysLeft(68.0, 1.0, 2.0))
        assertEquals(8, ScheduleEngine.daysLeft(4.0, 0.5, 1.0))
        assertEquals(12.0, ScheduleEngine.dosesPerDay(Frequency.EveryNHours(t(8), 2)), 0.0)
    }
}
