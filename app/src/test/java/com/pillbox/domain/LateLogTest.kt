package com.pillbox.domain

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class LateLogTest {
    private val now = LocalDateTime.of(2026, 10, 22, 14, 0)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)

    @Test fun windowIsSevenDays() {
        assertTrue(LateLog.canLog(at(15, 8), now))     // 7 days ago
        assertFalse(LateLog.canLog(at(14, 23, 59), now)) // 8 days ago
        assertTrue(LateLog.canLog(at(22, 8), now))
    }
    @Test fun futureDoseCannotBeLogged() {
        assertFalse(LateLog.canLog(at(22, 15), now))
        assertTrue(LateLog.canLog(at(22, 14), now))
    }
    @Test fun sameDayTime() {
        assertEquals(at(21, 8, 40), LateLog.takenAt(at(21, 8), LocalTime.of(8, 40), now))
    }
    @Test fun earlyIsAllowed() {
        assertEquals(at(21, 7, 30), LateLog.takenAt(at(21, 8), LocalTime.of(7, 30), now))
    }
    @Test fun afterMidnightRollsToNextDay() {
        assertEquals(at(22, 0, 10), LateLog.takenAt(at(21, 23, 30), LocalTime.of(0, 10), now))
    }
    @Test fun timeThatHasNotComeIsRejected() {
        assertNull(LateLog.takenAt(at(22, 8), LocalTime.of(15, 0), now))
    }
    @Test fun loggedDoseTodayWithLaterDoseToComeCanMove() {
        val doses = listOf(at(22, 8), at(22, 20))
        assertTrue(LateLog.movesApply(at(22, 8), doses, now))
    }
    @Test fun nothingMovesWhenNoLaterDoseIsLeftToday() {
        assertFalse(LateLog.movesApply(at(22, 8), listOf(at(22, 8)), now))
        assertFalse(LateLog.movesApply(at(22, 8), listOf(at(22, 8), at(22, 13)), now)) // the next dose has already passed
    }
    @Test fun olderDaysAreHistory() {
        assertFalse(LateLog.movesApply(at(21, 8), listOf(at(21, 8), at(21, 20)), now))
    }
}
