package com.pillbox.data

import com.pillbox.domain.DayPattern
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.WithFood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {
    private val d = LocalDate.of(2026, 10, 17)
    private fun roundTrip(s: Schedule) = Mappers.toDomain(Mappers.toEntity(1, s))

    @Test fun fixedEveryDay() {
        val s = Schedule(d, null, Frequency.FixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), DayPattern.EveryDay), WithFood.AFTER_MEAL)
        assertEquals(s, roundTrip(s))
    }

    @Test fun fixedDaysOfWeek() {
        val s = Schedule(d, d.plusDays(30), Frequency.FixedTimes(listOf(LocalTime.of(9, 0)), DayPattern.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))), WithFood.ANY)
        assertEquals(s, roundTrip(s))
        assertEquals(0b1000001, Mappers.toEntity(1, s).daysMask)
    }

    @Test fun fixedEveryNDays() {
        val s = Schedule(d, null, Frequency.FixedTimes(listOf(LocalTime.of(7, 0)), DayPattern.EveryNDays(3, d)), WithFood.ANY)
        assertEquals(s, roundTrip(s))
    }

    @Test fun everyFewHours() {
        val s = Schedule(d, null, Frequency.EveryNHours(LocalTime.of(8, 0), 5), WithFood.ANY)
        assertEquals(s, roundTrip(s))
    }

    @Test fun asNeeded() {
        val s = Schedule(d, null, Frequency.AsNeeded(240, 3), WithFood.ANY)
        assertEquals(s, roundTrip(s))
        val noMax = Schedule(d, null, Frequency.AsNeeded(360, null), WithFood.ANY)
        assertEquals(noMax, roundTrip(noMax))
    }

    @Test fun pauseRoundTrip() {
        val p = Pause(LocalDateTime.of(2026, 10, 17, 9, 0), null)
        assertEquals(p, Mappers.toDomain(Mappers.toEntity(1, p)))
        val q = Pause(LocalDateTime.of(2026, 10, 17, 9, 0), LocalDateTime.of(2026, 10, 20, 9, 0))
        assertEquals(q, Mappers.toDomain(Mappers.toEntity(1, q)))
    }

    @Test fun doseEventRoundTrip() {
        val e = DoseEvent(5, 2, LocalDateTime.of(2026, 10, 17, 8, 0), LocalDateTime.of(2026, 10, 17, 8, 12), DoseStatus.TAKEN, null, false, 12)
        assertEquals(e, Mappers.toDomain(Mappers.toEntity(e)))
    }

    @Test fun storedDateTimesSortAsText() {
        val a = Mappers.dt(LocalDateTime.of(2026, 1, 2, 9, 5))
        val b = Mappers.dt(LocalDateTime.of(2026, 1, 2, 10, 0))
        assertTrue(a < b)
        assertEquals("2026-01-02T09:05:00", a)
    }
}
