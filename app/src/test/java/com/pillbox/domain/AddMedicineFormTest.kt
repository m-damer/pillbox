package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddMedicineFormTest {
    private val today = LocalDate.of(2026, 10, 17)
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val base = AddMedicineForm(name = "Metformin")

    @Test fun newBoxStocksTheWholeBox() {
        val m = base.withBox(30).medicine(1)
        assertEquals(30.0, m.stock, 0.0); assertEquals(30, m.packSize)
    }

    @Test fun boxInUseStocksOnlyWhatIsLeft() {
        val m = base.withBox(30).withLeft(12).medicine(1)
        assertEquals(12.0, m.stock, 0.0); assertEquals(30, m.packSize)
    }

    @Test fun leftIsKeptBetweenOneAndTheBox() {
        val f = base.withBox(30)
        assertEquals(30, f.withLeft(99).left()); assertEquals(1, f.withLeft(0).left())
        assertEquals(10, f.withLeft(25).withBox(10).left()) // a smaller box pulls "left" down with it
        assertEquals(null, f.withLeft(30).leftCount)
    }

    @Test fun autoTimes() {
        assertEquals(listOf(t(8)), base.withFreq(FreqChoice.ONCE).times())
        assertEquals(listOf(t(8), t(20)), base.withFreq(FreqChoice.TWICE).times())
        assertEquals(listOf(t(8), t(16), t(0)), base.withFreq(FreqChoice.THREE).times())
    }

    @Test fun timesWrapPastMidnight() {
        val f = base.withFreq(FreqChoice.TWICE).withFirstDose(t(14))
        assertEquals(listOf(t(14), t(2)), f.times())
        assertEquals(listOf(t(2), t(14)), (f.schedule(today).frequency as Frequency.FixedTimes).times)
    }

    @Test fun threeTimesADayIsEvery8Hours() {
        val f = base.withFreq(FreqChoice.THREE).withFirstDose(t(13, 10))
        assertEquals(listOf(t(13, 10), t(21, 10), t(5, 10)), f.times())
    }

    @Test fun editingOneDoseKeepsTheOthers() {
        val f = base.withFreq(FreqChoice.THREE).withDoseTime(1, t(13, 30))
        assertEquals(listOf(t(8), t(13, 30), t(0)), f.times())
        // changing the first dose rebuilds all times
        assertEquals(listOf(t(9), t(17), t(1)), f.withFirstDose(t(9)).times())
    }

    @Test fun everyDayOngoing() {
        val s = base.withFreq(FreqChoice.TWICE).schedule(today)
        assertEquals(today, s.start)
        assertNull(s.end)
        assertEquals(DayPattern.EveryDay, (s.frequency as Frequency.FixedTimes).days)
        assertEquals(WithFood.WITH_MEAL, s.withFood)
    }

    @Test fun startAndLength() {
        assertEquals(today.plusDays(1), base.copy(start = StartChoice.TOMORROW).startDate(today))
        val picked = LocalDate.of(2026, 11, 2)
        val f = base.copy(start = StartChoice.PICK, pickedDate = picked, length = LengthChoice.SEVEN)
        assertEquals(picked, f.startDate(today))
        assertEquals(picked.plusDays(6), f.endDate(today))
        assertEquals(today.plusDays(9), base.copy(length = LengthChoice.OTHER, lengthDays = 10).endDate(today))
    }

    @Test fun specificDaysNeedAtLeastOne() {
        val f = base.copy(days = DaysChoice.SPECIFIC)
        assertTrue(FormError.DAYS_MISSING in f.step3Errors())
        val ok = f.copy(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertTrue(ok.step3Errors().isEmpty())
        assertEquals(DayPattern.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), (ok.schedule(today).frequency as Frequency.FixedTimes).days)
    }

    @Test fun everyNDaysAnchorsOnStart() {
        val s = base.copy(days = DaysChoice.EVERY_N, everyN = 3, start = StartChoice.TOMORROW).schedule(today)
        assertEquals(DayPattern.EveryNDays(3, today.plusDays(1)), (s.frequency as Frequency.FixedTimes).days)
    }

    @Test fun everyFewHours() {
        val s = base.withFreq(FreqChoice.HOURS).copy(hours = 5).schedule(today)
        assertEquals(Frequency.EveryNHours(t(8), 5), s.frequency)
        val doses = ScheduleEngine.plannedDoses(s, today)
        assertEquals(listOf(8, 13, 18, 23), doses.map { it.hour })
    }

    @Test fun everyFewHoursOnChosenDaysOnly() {
        val sat = DayOfWeek.SATURDAY // 2026-10-17 is a Saturday
        val s = base.withFreq(FreqChoice.HOURS).copy(hours = 12, days = DaysChoice.SPECIFIC, weekdays = setOf(sat)).schedule(today)
        assertEquals(2, ScheduleEngine.plannedDoses(s, today).size)
        assertTrue(ScheduleEngine.plannedDoses(s, today.plusDays(1)).isEmpty())
    }

    @Test fun asNeededIgnoresDays() {
        val s = base.withFreq(FreqChoice.AS_NEEDED).copy(maxPerDay = 3, minGapHours = 4, days = DaysChoice.SPECIFIC).schedule(today)
        assertEquals(Frequency.AsNeeded(240, 3), s.frequency)
        assertTrue(base.withFreq(FreqChoice.AS_NEEDED).copy(days = DaysChoice.SPECIFIC).step3Errors().isEmpty())
    }

    @Test fun step1Validation() {
        assertEquals(setOf(FormError.NAME_MISSING), AddMedicineForm(name = "  ").step1Errors())
        assertEquals(setOf(FormError.BOX_MISSING), base.copy(boxCount = null).step1Errors())
        assertEquals(setOf(FormError.BOX_MISSING), base.copy(boxCount = 0).step1Errors())
        assertTrue(base.step1Errors().isEmpty())
    }

    @Test fun medicineFromForm() {
        val m = base.copy(name = " Metformin ", strength = "500", strengthUnit = StrengthUnit.MG, dose = 0.5, boxCount = 28).medicine(7)
        assertEquals("Metformin", m.name)
        assertEquals("500 mg", m.strength)
        assertEquals(28.0, m.stock, 0.0)
        assertEquals(28, m.packSize)
        assertEquals(0.5, m.doseAmount, 0.0)
        assertEquals(7L, m.personId)
        assertNull(base.medicine(1).strength)
    }

    @Test fun typeChangeResetsDoseAndBox() {
        val f = base.withType(DoseUnit.ML)
        assertEquals(5.0, f.dose, 0.0)
        assertEquals(100, f.boxCount)
    }

    @Test fun nextDoseSkipsToTomorrowWhenTodayIsDone() {
        val s = base.withFreq(FreqChoice.TWICE).schedule(today)
        assertEquals(today.atTime(20, 0), ScheduleEngine.nextDose(s, emptyList(), today.atTime(9, 0)))
        assertEquals(today.plusDays(1).atTime(8, 0), ScheduleEngine.nextDose(s, emptyList(), today.atTime(20, 0)))
    }

    @Test fun nextDoseFollowsStartDateAndEnd() {
        val tomorrow = base.copy(start = StartChoice.TOMORROW).schedule(today)
        assertEquals(today.plusDays(1).atTime(8, 0), ScheduleEngine.nextDose(tomorrow, emptyList(), today.atTime(23, 0)))
        val oneDay = base.copy(length = LengthChoice.OTHER, lengthDays = 1).schedule(today)
        assertNull(ScheduleEngine.nextDose(oneDay, emptyList(), today.atTime(21, 0)))
        assertNull(ScheduleEngine.nextDose(base.withFreq(FreqChoice.AS_NEEDED).schedule(today), emptyList(), LocalDateTime.of(2026, 10, 17, 0, 0)))
    }

    @Test fun nextDoseSkipsPausedDoses() {
        val s = base.withFreq(FreqChoice.TWICE).schedule(today)
        val pause = Pause.forDays(today.atTime(7, 0), 1) // until 9:00 tomorrow
        assertEquals(today.plusDays(1).atTime(20, 0), ScheduleEngine.nextDose(s, listOf(pause), today.atTime(6, 0)))
    }

    @Test fun scheduleRemembersTheFirstDoseTime() {
        val s = base.withFreq(FreqChoice.THREE).withFirstDose(t(21, 47)).schedule(today)
        assertEquals(t(21, 47), s.startTime)
        assertEquals(listOf(today.atTime(21, 47)), ScheduleEngine.plannedDoses(s, today))
    }
}
