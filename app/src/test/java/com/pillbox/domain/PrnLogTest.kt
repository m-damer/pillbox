package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrnLogTest {
    private val d = LocalDate.of(2026, 10, 17)
    private fun at(h: Int, m: Int = 0) = d.atTime(h, m)

    @Test fun nothing_taken_is_allowed() {
        val day = PrnRules.day(at(14), emptyList(), 360, 4)
        assertEquals(0, day.count); assertNull(day.nextAllowed); assertTrue(day.check is TakeCheck.Allowed)
    }

    @Test fun after_a_dose_the_next_one_is_gap_later() {
        val day = PrnRules.day(at(14, 10), listOf(at(8, 5), at(14, 5)), 360, 4)
        assertEquals(listOf(at(14, 5), at(8, 5)), day.takenToday)
        assertEquals(at(20, 5), day.nextAllowed)
        assertTrue(!day.limitReached)
    }

    @Test fun the_daily_maximum_blocks_until_tomorrow() {
        val taken = listOf(at(3), at(9, 5), at(15, 5), at(21, 5))
        val day = PrnRules.day(at(21, 30), taken, 360, 4)
        assertTrue(day.limitReached)
        assertEquals(at(21, 5).plusHours(6), day.nextAllowed) // the gap runs past midnight, so it decides
        val early = PrnRules.day(at(12), listOf(at(1), at(2), at(3), at(4)), 360, 4)
        assertEquals(d.plusDays(1).atStartOfDay(), early.nextAllowed)
    }

    @Test fun yesterdays_doses_do_not_count_today_but_the_gap_still_applies() {
        val day = PrnRules.day(at(1), listOf(d.minusDays(1).atTime(22, 0)), 360, 4)
        assertEquals(0, day.count)
        assertEquals(at(4), day.nextAllowed)
    }

    @Test fun no_maximum_only_checks_the_gap() {
        val day = PrnRules.day(at(12), List(6) { at(it) }, 60, null)
        assertTrue(!day.limitReached)
        assertNull(day.nextAllowed)
    }
}
