package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PauseTest {
    private val now = LocalDateTime.of(2026, 10, 17, 12, 0)

    @Test fun threeDaysEndsAtNineOnTheFourthDay() {
        val p = Pause.forDays(now, 3)
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), p.until)
    }

    @Test fun activeAtFindsOnlyThePauseThatCoversNow() {
        val old = Pause(now.minusDays(9), now.minusDays(5))
        val current = Pause.forDays(now.minusHours(1), 1)
        assertEquals(current, Pause.activeAt(listOf(old, current), now))
        assertNull(Pause.activeAt(listOf(old), now))
        assertNull(Pause.activeAt(listOf(current), now.plusDays(2)))
    }

    @Test fun untilResumedStaysActive() {
        val p = Pause.untilResumed(now)
        assertNotNull(Pause.activeAt(listOf(p), now.plusDays(400)))
    }

    @Test fun allPausedNeedsEveryMedicinePaused() {
        val timed = listOf(Pause.forDays(now, 3))
        val open = listOf(Pause.untilResumed(now))
        assertNull(Pause.allPaused(emptyList(), now))
        assertNull(Pause.allPaused(listOf(timed, emptyList()), now))
        assertEquals(AllPaused(LocalDateTime.of(2026, 10, 20, 9, 0)), Pause.allPaused(listOf(timed), now))
        assertEquals(AllPaused(null), Pause.allPaused(listOf(open, open), now))
    }

    @Test fun allPausedReportsTheFirstMedicineToComeBack() {
        val short = listOf(Pause.forDays(now, 1))
        val long = listOf(Pause.forDays(now, 7))
        val open = listOf(Pause.untilResumed(now))
        assertEquals(AllPaused(LocalDateTime.of(2026, 10, 18, 9, 0)), Pause.allPaused(listOf(long, short, open), now))
    }

    @Test fun expiredPauseNoLongerCountsAsAllPaused() {
        val p = listOf(Pause.forDays(now, 1))
        assertNull(Pause.allPaused(listOf(p), now.plusDays(2)))
    }
}
