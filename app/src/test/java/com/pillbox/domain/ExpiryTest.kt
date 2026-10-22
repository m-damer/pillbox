package com.pillbox.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class ExpiryTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun med(id: Long, expiry: YearMonth?, stock: Double = 10.0) = Medicine(id = id, personId = 1, name = "M$id", stock = stock, expiry = expiry)

    @Test fun noDate() = assertEquals(ExpiryLevel.NONE, ExpiryRules.info(null, today).level)

    @Test fun lastDayIsEndOfMonth() {
        val i = ExpiryRules.info(YearMonth.of(2026, 10), today)
        assertEquals(LocalDate.of(2026, 10, 31), i.lastDay)
        assertEquals(9, i.daysLeft)
        assertEquals(ExpiryLevel.SOON, i.level)
    }
    @Test fun farDatesAreOk() {
        assertEquals(ExpiryLevel.OK, ExpiryRules.info(YearMonth.of(2026, 12), today).level)      // 70 days
        assertEquals(ExpiryLevel.OK, ExpiryRules.info(YearMonth.of(2026, 11), today).level)      // 39 days
    }
    @Test fun thirtyDayBoundary() {
        val t = LocalDate.of(2026, 10, 1)                 // Oct 31 is 30 days away
        assertEquals(ExpiryLevel.SOON, ExpiryRules.info(YearMonth.of(2026, 10), t).level)
        val t2 = LocalDate.of(2026, 9, 30)                // 31 days away
        assertEquals(ExpiryLevel.OK, ExpiryRules.info(YearMonth.of(2026, 10), t2).level)
    }
    @Test fun lastDayIsStillUsableThenExpired() {
        assertEquals(ExpiryLevel.SOON, ExpiryRules.info(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31)).level)
        assertEquals(ExpiryLevel.EXPIRED, ExpiryRules.info(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 1)).level)
    }
    @Test fun parse() {
        assertEquals(YearMonth.of(2027, 5), ExpiryRules.parse("2027-05"))
        assertNull(ExpiryRules.parse("nonsense"))
        assertNull(ExpiryRules.parse(null))
    }
    @Test fun noticesOnThirtySevenAndLastDay() {
        val oct = YearMonth.of(2026, 10)
        assertEquals(1, ExpiryRules.noticesFor(listOf(med(1, oct)), LocalDate.of(2026, 10, 1)).size)   // 30 days
        assertEquals(1, ExpiryRules.noticesFor(listOf(med(1, oct)), LocalDate.of(2026, 10, 24)).size)  // 7 days
        assertEquals(1, ExpiryRules.noticesFor(listOf(med(1, oct)), LocalDate.of(2026, 10, 31)).size)  // last day
        assertEquals(0, ExpiryRules.noticesFor(listOf(med(1, oct)), LocalDate.of(2026, 10, 15)).size)  // no notice day
        assertEquals(0, ExpiryRules.noticesFor(listOf(med(1, oct)), LocalDate.of(2026, 11, 1)).size)   // already expired
    }
    @Test fun emptyBoxesAreNotAnnounced() {
        assertTrue(ExpiryRules.noticesFor(listOf(med(1, YearMonth.of(2026, 10), stock = 0.0)), LocalDate.of(2026, 10, 24)).isEmpty())
    }
}
