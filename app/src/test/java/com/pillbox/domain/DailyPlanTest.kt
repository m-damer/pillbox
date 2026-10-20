package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanTest {
    private val today = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun med(name: String, stock: Double = 20.0) = Medicine(id = 1, personId = 1, name = name, stock = stock)
    private fun sched(vararg times: LocalTime, end: LocalDate? = null) =
        Schedule(today.minusDays(3), end, Frequency.FixedTimes(times.toList()), WithFood.WITH_MEAL)

    @Test fun groups_by_time_and_marks_the_new_one() {
        val plan = PlanBuilder.build(
            listOf(med("Amoxicillin") to sched(t(6), t(14))),
            med("Metformin"), sched(t(14), t(20, 30)), listOf(t(14), t(20, 30)), today,
        )
        assertEquals(listOf(t(6), t(14), t(20, 30)), plan.groups.map { it.time })
        val two = plan.groups[1].entries
        assertEquals(listOf("Amoxicillin", "Metformin"), two.map { it.name })
        assertEquals(listOf(false, true), two.map { it.isNew })
        assertEquals(0, plan.groups[1].changeable)
        assertEquals(1, plan.groups[2].changeable)
        assertNull(plan.groups[0].changeable)
    }

    @Test fun every_few_hours_times_cannot_be_changed_here() {
        val s = Schedule(today, null, Frequency.EveryNHours(t(8), 8), WithFood.ANY, startTime = t(8))
        val plan = PlanBuilder.build(emptyList(), med("Ibuprofen"), s, emptyList(), today)
        assertEquals(2, plan.groups.size)
        assertTrue(plan.groups.all { it.changeable == null })
    }

    @Test fun as_needed_has_no_slot_but_is_listed_coming_up() {
        val s = Schedule(today, null, Frequency.AsNeeded(), WithFood.ANY)
        val plan = PlanBuilder.build(emptyList(), med("Paracetamol"), s, emptyList(), today)
        assertTrue(plan.groups.isEmpty())
        assertTrue(plan.comingUp.single().asNeeded)
    }

    @Test fun coming_up_shows_last_dose_or_refill_day() {
        val end = today.plusDays(6)
        val plan = PlanBuilder.build(
            listOf(med("Amoxicillin") to sched(t(8), end = end)),
            med("Metformin", stock = 10.0), sched(t(8), t(20)), listOf(t(8), t(20)), today,
        )
        assertEquals(end, plan.comingUp[0].lastDose)
        assertNull(plan.comingUp[0].refillBy)
        assertNull(plan.comingUp[1].lastDose)
        assertEquals(today.plusDays(5), plan.comingUp[1].refillBy)
    }

    @Test fun a_course_that_starts_later_shows_its_first_day() {
        val later = today.plusDays(2)
        val s = Schedule(later, null, Frequency.FixedTimes(listOf(t(9))), WithFood.ANY)
        val plan = PlanBuilder.build(emptyList(), med("Vitamin D"), s, listOf(t(9)), today)
        assertEquals(listOf(t(9)), plan.groups.map { it.time })
    }
}
