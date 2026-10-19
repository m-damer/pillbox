package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LateRulesTest {
    private val d = LocalDate.of(2026, 10, 17)
    private fun at(h: Int, m: Int = 0) = d.atTime(h, m)
    private val fixedTimes = Frequency.FixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)))
    private val hours8 = Frequency.EveryNHours(LocalTime.of(8, 0), 8)
    private val prn = Frequency.AsNeeded()

    // ---- late threshold ----
    @Test fun onTimeAtOrBeforeThreshold() {
        assertNull(LateRules.lateMinutes(at(8), at(8, 30), LateThreshold.MIN_30))
        assertNull(LateRules.lateMinutes(at(8), at(7, 40), LateThreshold.MIN_15)) // early is not late
    }

    @Test fun lateAfterThreshold() {
        assertEquals(31, LateRules.lateMinutes(at(8), at(8, 31), LateThreshold.MIN_30))
        assertEquals(16, LateRules.lateMinutes(at(8), at(8, 16), LateThreshold.MIN_15))
        assertEquals(61, LateRules.lateMinutes(at(8), at(9, 1), LateThreshold.HOUR_1))
    }

    // ---- rule table 1 ----
    @Test fun fixedTimesFollowTheSetting() {
        assertEquals(LateAction.ShowSheet, LateRules.lateAction(fixedTimes, LateSetting.ASK))
        assertEquals(LateAction.MoveNextDoses, LateRules.lateAction(fixedTimes, LateSetting.MOVE_NEXT))
        assertEquals(LateAction.KeepTimes, LateRules.lateAction(fixedTimes, LateSetting.KEEP))
    }

    @Test fun fixedGapAlwaysAsks() {
        for (s in LateSetting.values()) assertEquals(LateAction.ShowSheet, LateRules.lateAction(hours8, s))
    }

    @Test fun asNeededIsNeverLate() {
        for (s in LateSetting.values()) assertEquals(LateAction.NotApplicable, LateRules.lateAction(prn, s))
    }

    @Test fun moveShiftsOnlyTodaysRemainingDosesByTheDelay() {
        val today = listOf(at(8), at(14), at(20))
        // 8:00 dose taken at 9:10 -> 14:00 becomes 15:10, 20:00 becomes 21:10
        assertEquals(listOf(at(15, 10), at(21, 10)), LateRules.moveRemainingToday(today, at(8), at(9, 10)))
        // the already-taken dose is not part of the result
        assertEquals(listOf(at(21, 10)), LateRules.moveRemainingToday(today, at(14), at(15, 10)))
    }

    @Test fun moveWithNoDelayChangesNothing() {
        val today = listOf(at(8), at(14))
        assertEquals(listOf(at(14)), LateRules.moveRemainingToday(today, at(8), at(8)))
    }

    // ---- every few hours: Move ----
    @Test fun hoursMoveRestartsTheChainFromTheActualTime() {
        // N=8, first 8:00; dose 8:00 taken at 9:30. Next cycle starts tomorrow 8:00.
        val chain = LateRules.hoursChainAfter(at(9, 30), 8, at(8).plusDays(1))
        assertEquals(listOf(at(17, 30)), chain) // 17:30; 01:30 would leave only 6.5 h before 8:00 -> dropped
    }

    @Test fun hoursMoveKeepsMinimumGapBeforeNextCycle() {
        val chain = LateRules.hoursChainAfter(at(8, 20), 2, at(8).plusDays(1))
        assertEquals(10, chain.size)
        assertEquals(at(10, 20), chain.first())
        assertEquals(at(4, 20).plusDays(1), chain.last()) // 6:20 would leave only 1 h 40 min before 8:00
    }

    @Test fun realGapIsReported() {
        assertEquals(440, LateRules.gapMinutes(at(8), at(15, 20))) // 7 h 20 min
    }

    // ---- missed ----
    @Test fun missedWhenNextDoseIsDue() {
        assertEquals(at(14), LateRules.missedAt(at(8), at(14)))
        assertEquals(false, LateRules.isMissed(at(13, 59), at(8), at(14)))
        assertEquals(true, LateRules.isMissed(at(14), at(8), at(14)))
    }

    @Test fun lastDoseOfTheDayIsMissedAtElevenFiftyNine() {
        assertEquals(at(23, 59), LateRules.missedAt(at(20), null))
        assertEquals(at(23, 59), LateRules.missedAt(at(20), at(8).plusDays(1)))
        assertEquals(false, LateRules.isMissed(at(23, 58), at(20), null))
        assertEquals(true, LateRules.isMissed(at(23, 59), at(20), null))
    }

    // ---- as needed guard ----
    @Test fun firstAsNeededDoseIsAllowed() {
        assertEquals(TakeCheck.Allowed, LateRules.asNeededCheck(at(10), emptyList(), 360, 4))
    }

    @Test fun tooSoonBeforeGapEnds() {
        val r = LateRules.asNeededCheck(at(12), listOf(at(8)), 360, 4)
        assertEquals(TakeCheck.TooSoon(at(14)), r)
    }

    @Test fun allowedExactlyWhenGapEnds() {
        assertEquals(TakeCheck.Allowed, LateRules.asNeededCheck(at(14), listOf(at(8)), 360, 4))
    }

    @Test fun dailyMaximumBlocksAndSaysWhenNextIsAllowed() {
        val taken = listOf(at(0, 30), at(6, 30), at(12, 30), at(18, 30))
        val r = LateRules.asNeededCheck(at(19), taken, 360, 4)
        // last dose 18:30 + 6 h gap = 00:30, which is later than midnight
        assertEquals(TakeCheck.DailyMaxReached(at(0, 30).plusDays(1)), r)
    }

    @Test fun dailyMaximumNextAllowedRespectsGapAcrossMidnight() {
        val taken = listOf(at(8), at(13), at(18), at(23, 30))
        val r = LateRules.asNeededCheck(at(23, 45), taken, 360, 4)
        assertEquals(TakeCheck.DailyMaxReached(at(5, 30).plusDays(1)), r) // 23:30 + 6 h
    }

    @Test fun gapStillAppliesAfterMidnight() {
        val r = LateRules.asNeededCheck(at(1).plusDays(1), listOf(at(23)), 360, 4)
        assertEquals(TakeCheck.TooSoon(at(5).plusDays(1)), r)
    }

    @Test fun noDailyMaximumMeansOnlyTheGapApplies() {
        val taken = (0 until 10).map { at(0).plusHours(it.toLong() * 2) }
        assertEquals(TakeCheck.Allowed, LateRules.asNeededCheck(at(23), taken.filter { it.hour < 5 }, 60, null))
    }

    // ---- every few hours guard ----
    @Test fun hoursGuard() {
        assertEquals(TakeCheck.Allowed, LateRules.hoursCheck(at(10), null, 8))
        assertEquals(TakeCheck.TooSoon(at(16)), LateRules.hoursCheck(at(15), at(8), 8))
        assertEquals(TakeCheck.Allowed, LateRules.hoursCheck(at(16), at(8), 8))
    }
}
