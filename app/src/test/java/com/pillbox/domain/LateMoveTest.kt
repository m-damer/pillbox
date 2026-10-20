package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LateMoveTest {
    private val day = LocalDate.of(2026, 10, 20)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private val fixed = Schedule(day.minusDays(5), null, Frequency.FixedTimes(listOf(t(8), t(14), t(22))))
    private val every8 = Schedule(day.minusDays(5), null, Frequency.EveryNHours(t(6), 8))

    @Test fun fixedMoveShiftsOnlyTheRestOfToday() {
        val moves = LateRules.moveEntries(fixed, at(14), at(14, 40))
        assertEquals(mapOf<LocalDateTime, LocalDateTime?>(at(22) to at(22, 40)), moves)
        val moved = fixed.copy(shifts = moves)
        assertEquals(listOf(at(8), at(14), at(22, 40)), ScheduleEngine.plannedDoses(moved, day))
        // tomorrow is normal
        assertEquals(listOf(at(8, d = day.plusDays(1)), at(14, d = day.plusDays(1)), at(22, d = day.plusDays(1))), ScheduleEngine.plannedDoses(moved, day.plusDays(1)))
    }

    @Test fun fixedMoveCanCrossMidnight() {
        val moves = LateRules.moveEntries(fixed, at(22), at(23, 30))
        assertTrue(moves.isEmpty()) // nothing after 22:00 today
        // 14:00 taken at 17:00: the 22:00 dose moves to 1:00 and now rings on the next day.
        val late = fixed.copy(shifts = LateRules.moveEntries(fixed, at(14), at(23)))
        assertEquals(listOf(at(8), at(14)), ScheduleEngine.plannedDoses(late, day))
        assertEquals(at(7, d = day.plusDays(1)), ScheduleEngine.plannedDoses(late, day.plusDays(1)).first())
    }

    @Test fun movedTwiceKeepsOneEntry() {
        val first = fixed.copy(shifts = LateRules.moveEntries(fixed, at(8), at(8, 30)))
        // 14:00 became 14:30, 22:00 became 22:30. Now the 14:30 dose is taken at 15:00.
        val second = LateRules.moveEntries(first, at(14, 30), at(15))
        val both = first.copy(shifts = first.shifts + second)
        assertEquals(setOf(at(14), at(22)), both.shifts.keys)
        assertEquals(at(23), both.shifts[at(22)])
    }

    @Test fun noMoveWhenNotLate() {
        assertTrue(LateRules.moveEntries(fixed, at(14), at(14)).isEmpty())
        assertTrue(LateRules.moveEntries(fixed, at(14), at(13, 50)).isEmpty())
    }

    @Test fun hoursMoveRestartsTheChain() {
        // 6:00, 14:00, 22:00. The 14:00 dose is taken at 14:40 -> 22:40, then 6:40 the next morning (as in the design sheet).
        val moves = LateRules.moveEntries(every8, at(14), at(14, 40))
        assertEquals(mapOf<LocalDateTime, LocalDateTime?>(at(22) to at(22, 40), at(6, d = day.plusDays(1)) to at(6, 40, day.plusDays(1))), moves)
        val moved = every8.copy(shifts = moves)
        assertEquals(listOf(at(6), at(14), at(22, 40)), ScheduleEngine.plannedDoses(moved, day))
        assertEquals(at(6, 40, day.plusDays(1)), ScheduleEngine.plannedDoses(moved, day.plusDays(1)).first())
    }

    @Test fun hoursMoveKeepsTheNumberOfDoses() {
        val moves = LateRules.moveEntries(every8, at(6), at(11))
        assertEquals(at(19), moves[at(14)])
        assertEquals(at(3, d = day.plusDays(1)), moves[at(22)])
        assertEquals(at(11, d = day.plusDays(1)), moves[at(6, d = day.plusDays(1))])
    }

    @Test fun hoursPastMidnightDoseBelongsToItsCycle() {
        val every6 = Schedule(day.minusDays(5), null, Frequency.EveryNHours(t(20), 6))
        // cycle 20:00, 2:00 (+1), 8:00 (+1), 14:00 (+1). The 2:00 dose is taken at 3:00 -> 9:00, 15:00... limit is 20:00.
        val base = every6.copy()
        val moves = LateRules.moveEntries(base, at(2, d = day.plusDays(1)), at(3, d = day.plusDays(1)))
        assertEquals(at(9, d = day.plusDays(1)), moves[at(8, d = day.plusDays(1))])
        assertEquals(at(15, d = day.plusDays(1)), moves[at(14, d = day.plusDays(1))])
        assertEquals(at(21, d = day.plusDays(1)), moves[at(20, d = day.plusDays(1))])
    }

    @Test fun encodeAndDecodeRoundTrip() {
        val m: Map<LocalDateTime, LocalDateTime?> = mapOf(at(14) to at(14, 40), at(22) to null)
        assertEquals(m, LateRules.decodeShifts(LateRules.encodeShifts(m)))
        assertNull(LateRules.encodeShifts(emptyMap()))
        assertEquals(emptyMap<LocalDateTime, LocalDateTime?>(), LateRules.decodeShifts(null))
        assertEquals(emptyMap<LocalDateTime, LocalDateTime?>(), LateRules.decodeShifts("garbage"))
    }

    @Test fun oldShiftsAreForgotten() {
        val old = at(8, d = day.minusDays(500))
        val merged = LateRules.mergeShifts(mapOf(old to at(9, d = day.minusDays(500))), mapOf(at(14) to at(15)), at(12))
        assertEquals(setOf(at(14)), merged.keys)
    }

    @Test fun lateActionTable() {
        assertEquals(LateAction.ShowSheet, LateRules.lateAction(fixed.frequency, LateSetting.ASK))
        assertEquals(LateAction.MoveNextDoses, LateRules.lateAction(fixed.frequency, LateSetting.MOVE_NEXT))
        assertEquals(LateAction.KeepTimes, LateRules.lateAction(fixed.frequency, LateSetting.KEEP))
        assertEquals(LateAction.ShowSheet, LateRules.lateAction(every8.frequency, LateSetting.KEEP))
    }
}
