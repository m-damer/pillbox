package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMathTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun med(stock: Double, dose: Double = 1.0, pack: Int? = 60) = Medicine(personId = 1, name = "M", doseAmount = dose, stock = stock, packSize = pack)
    private fun twice(start: LocalDate = today, end: LocalDate? = null, days: DayPattern = DayPattern.EveryDay) =
        Schedule(start, end, Frequency.FixedTimes(listOf(t(8), t(20)), days))

    @Test fun daysLeftWholeDaysRoundedDown() {
        assertEquals(4, StockMath.daysLeft(med(9.0), twice()))
        assertEquals(4, StockMath.daysLeft(med(8.0), twice()))
        assertEquals(3, StockMath.daysLeft(med(7.0), twice()))
    }

    @Test fun halfTabletDose() = assertEquals(8, StockMath.daysLeft(med(8.0, dose = 0.5), twice()))

    @Test fun asNeededHasNoDaysLeft() =
        assertNull(StockMath.daysLeft(med(10.0), Schedule(today, null, Frequency.AsNeeded())))

    @Test fun specificDaysUseAverageOverAWeek() {
        val mondays = twice(days = DayPattern.DaysOfWeek(setOf(java.time.DayOfWeek.MONDAY)))
        assertEquals(2.0 / 7.0, StockMath.averageDosesPerDay(mondays)!!, 1e-9)
    }

    @Test fun levels() {
        assertEquals(StockLevel.OUT, StockMath.info(med(0.0), twice(), today).level)
        assertEquals(StockLevel.BUY_SOON, StockMath.info(med(8.0), twice(), today).level) // 4 days, remind at 5
        assertEquals(StockLevel.ENOUGH, StockMath.info(med(30.0), twice(), today).level)
        assertEquals(StockLevel.ENOUGH, StockMath.info(med(5.0), Schedule(today, null, Frequency.AsNeeded()), today).level)
    }

    @Test fun courseIsEnoughWhenItCoversTheRest() {
        val course = twice(start = today.minusDays(3), end = today.plusDays(3)) // 4 days left x 2 = 8
        assertEquals(StockLevel.ENOUGH, StockMath.info(med(8.0), course, today).level)
        assertEquals(StockLevel.BUY_SOON, StockMath.info(med(7.0), course, today).level)
    }

    @Test fun addStockTotal() {
        assertEquals(68.0, StockMath.newTotal(8.0, 1, 60), 0.0)
        assertEquals(34, StockMath.daysFor(68.0, med(8.0), twice()))
    }

    @Test fun barFraction() {
        assertEquals(0.5f, StockMath.barFraction(med(30.0)), 0.001f)
        assertEquals(1f, StockMath.barFraction(med(90.0)), 0.001f)
        assertEquals(0f, StockMath.barFraction(med(0.0)), 0.001f)
    }

    // ---- restart ----
    @Test fun restartKeepsCourseLengthAndStartsNow() {
        val s = twice(start = today.minusDays(10), end = today.minusDays(4)) // 7 days
        val r = ScheduleEngine.restarted(s, LocalDateTime.of(2026, 10, 19, 14, 30))
        assertEquals(today, r.start)
        assertEquals(today.plusDays(6), r.end)
        assertEquals(listOf(today.atTime(20, 0)), ScheduleEngine.plannedDoses(r, today))
    }

    @Test fun restartOngoingStaysOngoing() {
        val r = ScheduleEngine.restarted(twice(start = today.minusDays(30)), today.atTime(9, 0))
        assertNull(r.end)
    }

    // ---- week summary ----
    @Test fun weekSummary() {
        val s = twice(start = today.minusDays(30))
        val takenEvents = (1..6).flatMap { d ->
            listOf(8, 20).filter { !(d == 4 && it == 20) }.map {
                DoseEvent(planned = today.minusDays(d.toLong()).atTime(it, 0), taken = today.minusDays(d.toLong()).atTime(it, 5), status = DoseStatus.TAKEN)
            }
        }
        val w = WeekSummary.build(s, emptyList(), takenEvents, today)
        assertEquals(7, w.days.size)
        assertEquals(DayMark.PENDING, w.days.last().mark)
        assertEquals(DayMark.MISSED, w.days.first { it.date == today.minusDays(4) }.mark)
        assertEquals(listOf(today.minusDays(4)), w.missedDays)
        assertEquals(11, w.taken)
        assertEquals(12, w.counted)
    }

    @Test fun weekSummaryIgnoresPausedDays() {
        val s = twice(start = today.minusDays(30))
        val pause = Pause(today.minusDays(3).atStartOfDay(), today.minusDays(1).atStartOfDay())
        val w = WeekSummary.build(s, listOf(pause), emptyList(), today)
        assertEquals(DayMark.NONE, w.days.first { it.date == today.minusDays(3) }.mark)
        assertTrue(today.minusDays(3) !in w.missedDays)
    }
}
