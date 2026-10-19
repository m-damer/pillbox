package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayBuilderTest {
    private val sat = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun med(id: Long, name: String) = Medicine(id = id, personId = 1, name = name)
    private fun fixed(vararg times: LocalTime) = Schedule(sat.minusDays(10), null, Frequency.FixedTimes(times.toList()))
    private val amox = Triple(med(1, "Amoxicillin"), fixed(t(6), t(14), t(22)), emptyList<Pause>())
    private val metf = Triple(med(2, "Metformin"), fixed(t(14), t(20, 30)), emptyList<Pause>())
    private fun taken(id: Long, at: LocalDateTime, takenAt: LocalDateTime = at.plusMinutes(5)) =
        DoseEvent(medicineId = id, planned = at, taken = takenAt, status = DoseStatus.TAKEN)

    @Test fun groupsByTimeAndCounts() {
        val m = TodayBuilder.build(listOf(amox, metf), listOf(taken(1, sat.atTime(6, 0))), sat, sat.atTime(9, 0))
        assertEquals(listOf(6, 14, 20, 22), m.groups.map { it.time.hour })
        assertEquals(listOf("Amoxicillin", "Metformin"), m.groups[1].rows.map { it.medicine.name })
        assertEquals(1, m.taken)
        assertEquals(5, m.total)
    }

    @Test fun firstPendingGroupIsNextOthersUpcoming() {
        val m = TodayBuilder.build(listOf(amox, metf), listOf(taken(1, sat.atTime(6, 0))), sat, sat.atTime(9, 0))
        assertEquals(listOf(GroupState.DONE, GroupState.NEXT, GroupState.UPCOMING, GroupState.UPCOMING), m.groups.map { it.state })
        assertTrue(m.groups[1].actionable)
        assertFalse(m.groups[2].actionable)
    }

    @Test fun dueWithinThresholdIsStillNext() {
        val m = TodayBuilder.build(listOf(amox), emptyList(), sat, sat.atTime(6, 20))
        assertEquals(GroupState.NEXT, m.groups[0].state)
    }

    @Test fun overdueBeyondThresholdIsLateAndNextMovesOn() {
        val m = TodayBuilder.build(listOf(amox, metf), emptyList(), sat, sat.atTime(15, 0))
        val byHour = m.groups.associate { it.time.hour to it }
        assertEquals(GroupState.LATE, byHour[6]!!.state)
        assertEquals(GroupState.LATE, byHour[14]!!.state)
        assertEquals(60, byHour[14]!!.minutesLate)
        assertEquals(GroupState.NEXT, byHour[20]!!.state)
        assertEquals(GroupState.UPCOMING, byHour[22]!!.state)
    }

    @Test fun missedWhenNextDoseOfSameMedicineIsDue() {
        val m = TodayBuilder.build(listOf(amox), emptyList(), sat, sat.atTime(14, 10))
        assertTrue(m.groups[0].rows[0].missed) // 6:00 passed, 14:00 is due
        assertFalse(m.groups[1].rows[0].missed)
    }

    @Test fun skippedCountsAsDoneNotTaken() {
        val skip = DoseEvent(medicineId = 1, planned = sat.atTime(6, 0), status = DoseStatus.SKIPPED, skipReason = "side_effects")
        val m = TodayBuilder.build(listOf(amox), listOf(skip), sat, sat.atTime(9, 0))
        assertEquals(GroupState.DONE, m.groups[0].state)
        assertEquals(RowStatus.SKIPPED, m.groups[0].rows[0].status)
        assertEquals(0, m.taken)
    }

    @Test fun pastDayShowsMissedAndNoActions() {
        val yesterday = sat.minusDays(1)
        val m = TodayBuilder.build(listOf(amox), listOf(taken(1, yesterday.atTime(6, 0))), yesterday, sat.atTime(9, 0))
        assertEquals(GroupState.DONE, m.groups[0].state)
        assertEquals(GroupState.MISSED_DAY, m.groups[1].state)
        assertTrue(m.groups[1].rows[0].missed)
        assertFalse(m.groups[1].actionable)
    }

    @Test fun futureDayIsPlannedOnly() {
        val m = TodayBuilder.build(listOf(amox, metf), emptyList(), sat.plusDays(1), sat.atTime(9, 0))
        assertTrue(m.groups.all { it.state == GroupState.FUTURE_DAY })
        assertTrue(m.groups.flatMap { it.rows }.none { it.missed })
        assertEquals(0, m.taken)
    }

    @Test fun pausedAndAsNeededAreNotListed() {
        val paused = Triple(med(3, "Paused"), fixed(t(9)), listOf(Pause(sat.atStartOfDay(), null)))
        val prn = Triple(med(4, "Painkiller"), Schedule(sat, null, Frequency.AsNeeded()), emptyList<Pause>())
        val m = TodayBuilder.build(listOf(paused, prn, amox), emptyList(), sat, sat.atTime(7, 0))
        assertEquals(3, m.total)
    }

    @Test fun pastMidnightDoseBelongsToTheDayItRings() {
        val night = Triple(med(5, "Syrup"), Schedule(sat.minusDays(1), null, Frequency.EveryNHours(t(20), 8)), emptyList<Pause>())
        val m = TodayBuilder.build(listOf(night), emptyList(), sat, sat.atTime(1, 0))
        assertEquals(listOf(4, 12, 20), m.groups.map { it.time.hour }) // 04:00 from yesterday's cycle, then today's 12:00 and 20:00
    }

    @Test fun takenEventsMatchByMedicineAndPlannedTime() {
        // same planned time, another medicine taken: must not mark this one
        val m = TodayBuilder.build(listOf(amox, metf), listOf(taken(2, sat.atTime(14, 0))), sat, sat.atTime(13, 0))
        val g = m.groups.first { it.time.hour == 14 }
        assertEquals(listOf(RowStatus.PENDING, RowStatus.TAKEN), g.rows.map { it.status })
    }
}
