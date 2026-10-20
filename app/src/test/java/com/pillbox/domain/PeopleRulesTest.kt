package com.pillbox.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleRulesTest {
    @Test fun nameIsTrimmedAndSquashed() = assertEquals("Mom Hassan", PeopleRules.cleanName("  Mom   Hassan  "))
    @Test fun nameIsCutAtTheLimit() = assertEquals(PeopleRules.MAX_NAME, PeopleRules.cleanName("x".repeat(80)).length)
    @Test fun blankNameCannotBeSaved() { assertFalse(PeopleRules.canSave("   ")); assertTrue(PeopleRules.canSave(" A ")) }

    @Test fun initialsUseTwoLettersOnlyWhenTheFirstIsTaken() =
        assertEquals(listOf("M", "Mo", "D", "Ma"), PeopleRules.initials(listOf("Me", "Mom", "Dad", "Maya")))

    @Test fun initialsWorkInArabic() = assertEquals(listOf("أ", "أب"), PeopleRules.initials(listOf("أنا", "أبي")))
    @Test fun initialsOfAOneLetterNameStayOne() = assertEquals(listOf("M", "M"), PeopleRules.initials(listOf("Me", "M")))
    @Test fun initialsOfEmptyNameAreEmpty() = assertEquals(listOf(""), PeopleRules.initials(listOf("  ")))

    @Test fun nextColorPrefersAnUnusedOne() {
        assertEquals(0, PeopleRules.nextColor(emptyList()))
        assertEquals(2, PeopleRules.nextColor(listOf(0, 1)))
        assertEquals(0, PeopleRules.nextColor(List(PeopleRules.COLORS) { it }))
    }

    @Test fun colorIndexAlwaysFits() { assertEquals(1, PeopleRules.colorFor(7)); assertEquals(5, PeopleRules.colorFor(-1)) }

    @Test fun birthYear() {
        assertNull(PeopleRules.parseBirthYear("", 2026).getOrThrow())
        assertEquals(1958, PeopleRules.parseBirthYear(" 1958 ", 2026).getOrThrow())
        assertTrue(PeopleRules.parseBirthYear("19", 2026).isFailure)
        assertTrue(PeopleRules.parseBirthYear("2030", 2026).isFailure)
        assertTrue(PeopleRules.parseBirthYear("abcd", 2026).isFailure)
    }

    @Test fun summaryCountsMissedDosesAndFindsTheFirst() {
        val m = Medicine(id = 1, personId = 1, name = "A", stock = 10.0)
        val s = Schedule(LocalDate.of(2026, 1, 1), null, Frequency.FixedTimes(listOf(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(14, 0))))
        val day = LocalDate.of(2026, 5, 1)
        val rows = listOf(
            DoseRow(m, s, day.atTime(8, 0), null, missed = true),
            DoseRow(m, s, day.atTime(14, 0), null, missed = true),
        )
        val model = TodayModel(day, listOf(DoseGroup(rows[0].planned, rows, GroupState.MISSED_DAY, 0)), taken = 0, total = 2)
        val sum = PeopleRules.summarize(model)
        assertEquals(2, sum.missed)
        assertEquals(day.atTime(8, 0), sum.firstMissed)
    }
}
