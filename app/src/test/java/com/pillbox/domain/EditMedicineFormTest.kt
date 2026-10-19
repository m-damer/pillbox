package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditMedicineFormTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val started = LocalDate.of(2026, 10, 10)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private fun roundTrip(form: AddMedicineForm) {
        val med = form.medicine(1).copy(id = 7)
        val schedule = form.schedule(today)
        val back = AddMedicineForm.fromExisting(med, schedule)
        assertEquals(schedule, back.schedule(today.plusDays(30)))
        assertEquals(med, back.updated(med))
    }

    @Test fun twiceADayRoundTrips() = roundTrip(
        AddMedicineForm(name = "Metformin", strength = "850", dose = 1.0, boxCount = 8, freq = FreqChoice.TWICE, firstDose = t(14), withFood = WithFood.AFTER_MEAL, pickedDate = started, start = StartChoice.PICK),
    )

    @Test fun customTimesAreKept() {
        val f = AddMedicineForm(name = "A", freq = FreqChoice.TWICE, firstDose = t(8)).withDoseTime(1, t(20, 30))
        val back = AddMedicineForm.fromExisting(f.medicine(1), f.schedule(today))
        assertEquals(listOf(t(8), t(20, 30)), back.times())
    }

    @Test fun everyFewHoursRoundTrips() = roundTrip(AddMedicineForm(name = "A", freq = FreqChoice.HOURS, firstDose = t(6), hours = 6))
    @Test fun asNeededRoundTrips() = roundTrip(AddMedicineForm(name = "A", freq = FreqChoice.AS_NEEDED, maxPerDay = 3, minGapHours = 4))
    @Test fun specificDaysRoundTrip() = roundTrip(
        AddMedicineForm(name = "A", freq = FreqChoice.ONCE, days = DaysChoice.SPECIFIC, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
    )
    @Test fun sevenDayCourseRoundTrips() = roundTrip(AddMedicineForm(name = "A", length = LengthChoice.SEVEN))
    @Test fun otherLengthRoundTrips() = roundTrip(AddMedicineForm(name = "A", length = LengthChoice.OTHER, lengthDays = 12))

    @Test fun startDateCannotChange() {
        val f = AddMedicineForm(name = "A", pickedDate = started, start = StartChoice.PICK)
        val back = AddMedicineForm.fromExisting(f.medicine(1), f.schedule(today))
        assertEquals(started, back.copy(start = StartChoice.TOMORROW).startDate(today.plusDays(5)))
    }

    @Test fun everyNDaysKeepsItsAnchor() {
        val anchor = LocalDate.of(2026, 10, 15)
        val s = Schedule(started, null, Frequency.FixedTimes(listOf(t(8)), DayPattern.EveryNDays(3, anchor)), WithFood.ANY, t(8))
        val f = AddMedicineForm.fromExisting(Medicine(personId = 1, name = "A"), s)
        assertEquals(DayPattern.EveryNDays(3, anchor), (f.schedule(today).frequency as Frequency.FixedTimes).days)
        val changed = f.copy(everyN = 4).schedule(today).frequency as Frequency.FixedTimes
        assertEquals(DayPattern.EveryNDays(4, anchor), changed.days)
    }

    @Test fun switchingToEveryNDaysCountsFromToday() {
        val f = AddMedicineForm.fromExisting(Medicine(personId = 1, name = "A"), AddMedicineForm(name = "A", pickedDate = started, start = StartChoice.PICK).schedule(today))
        val days = (f.copy(days = DaysChoice.EVERY_N, everyN = 2).schedule(today).frequency as Frequency.FixedTimes).days
        assertEquals(DayPattern.EveryNDays(2, today), days)
    }

    @Test fun stockStaysExactUnlessChanged() {
        val med = Medicine(id = 3, personId = 1, name = "A", stock = 7.5, packSize = 30)
        val form = AddMedicineForm.fromExisting(med, AddMedicineForm(name = "A").schedule(today))
        assertEquals(7.5, form.updated(med).stock, 0.0)
        assertEquals(30, form.updated(med).packSize)
        assertEquals(12.0, form.copy(boxCount = 12).updated(med).stock, 0.0)
    }

    @Test fun strengthWithoutKnownUnitIsKeptAsText() {
        val med = Medicine(personId = 1, name = "A", strength = "5 %")
        val f = AddMedicineForm.fromExisting(med, AddMedicineForm(name = "A").schedule(today))
        assertEquals("5 %", f.strength)
    }

    @Test fun editFormKnowsItIsEditing() {
        val med = Medicine(personId = 1, name = "A")
        assertTrue(AddMedicineForm.fromExisting(med, AddMedicineForm(name = "A").schedule(today)).isEdit)
        assertFalse(AddMedicineForm().isEdit)
    }
}
