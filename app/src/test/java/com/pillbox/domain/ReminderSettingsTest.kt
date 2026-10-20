package com.pillbox.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSettingsTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val night = QuietHours(enabled = true) // 10 PM to 7 AM

    @Test fun windowCrossingMidnight() {
        assertTrue(night.contains(t(23)))
        assertTrue(night.contains(t(0)))
        assertTrue(night.contains(t(6, 59)))
        assertTrue(night.contains(t(22)))
        assertFalse(night.contains(t(7)))
        assertFalse(night.contains(t(12)))
    }

    @Test fun windowInsideOneDay() {
        val q = QuietHours(enabled = true, from = t(13), until = t(15))
        assertTrue(q.contains(t(14)))
        assertFalse(q.contains(t(15)))
        assertFalse(q.contains(t(12, 59)))
    }

    @Test fun sameStartAndEndMeansNoWindow() {
        val q = QuietHours(enabled = true, from = t(8), until = t(8))
        assertFalse(q.contains(t(8)))
        assertFalse(q.contains(t(20)))
    }

    @Test fun offNeverSilences() {
        val off = QuietHours(enabled = false, doseRings = false)
        assertFalse(off.silencesDoseAt(t(23)))
        assertFalse(off.silencesRefillAt(t(23)))
    }

    @Test fun doseRingsByDefaultEvenAtNight() {
        assertFalse(night.silencesDoseAt(t(23)))
        assertTrue(night.silencesRefillAt(t(23)))
        assertFalse(night.silencesRefillAt(t(12)))
    }

    @Test fun doseSilencedOnlyWhenChosen() {
        val q = night.copy(doseRings = false)
        assertTrue(q.silencesDoseAt(t(1)))
        assertFalse(q.silencesDoseAt(t(9)))
        assertFalse(night.copy(muteRefill = false).silencesRefillAt(t(1)))
    }

    @Test fun anyTimeFoodKeepsUsualTimes() {
        assertNull(MealTimes().suggest(WithFood.ANY, 2))
    }

    @Test fun withMealUsesTheMeals() {
        val m = MealTimes()
        assertEquals(listOf(t(7, 30)), m.suggest(WithFood.WITH_MEAL, 1))
        assertEquals(listOf(t(7, 30), t(20)), m.suggest(WithFood.WITH_MEAL, 2))
        assertEquals(listOf(t(7, 30), t(13, 30), t(20)), m.suggest(WithFood.WITH_MEAL, 3))
    }

    @Test fun beforeAndAfterShiftThirtyMinutes() {
        val m = MealTimes()
        assertEquals(listOf(t(7), t(19, 30)), m.suggest(WithFood.BEFORE_MEAL, 2))
        assertEquals(listOf(t(8), t(20, 30)), m.suggest(WithFood.AFTER_MEAL, 2))
    }

    @Test fun otherCountsGiveNoSuggestion() {
        assertNull(MealTimes().suggest(WithFood.WITH_MEAL, 4))
        assertNull(MealTimes().suggest(WithFood.WITH_MEAL, 0))
    }

    @Test fun withReplacesOneMeal() {
        val m = MealTimes().with(MealTimes.Meal.LUNCH, t(12))
        assertEquals(t(12), m[MealTimes.Meal.LUNCH])
        assertEquals(t(7, 30), m[MealTimes.Meal.BREAKFAST])
    }

    @Test fun keepRingingControlsRepeats() {
        assertEquals(ReminderPlanner.MAX_NAGS, ReminderSettings().maxRepeats)
        assertEquals(0, ReminderSettings(keepRinging = false).maxRepeats)
    }

    // ---- Add medicine uses the meals
    private val base = AddMedicineForm(name = "Metformin", meals = MealTimes())

    @Test fun addFormStartsAtMealTimes() {
        assertEquals(listOf(t(7, 30), t(20)), base.withFreq(FreqChoice.TWICE).times())
        assertEquals(listOf(t(7), t(19, 30)), base.copy(withFood = WithFood.BEFORE_MEAL).times())
        assertEquals(t(7, 30), base.firstTime())
    }

    @Test fun addFormWithoutMealsKeepsOldBehaviour() {
        assertEquals(listOf(t(8), t(20)), base.copy(meals = null).times())
    }

    @Test fun pickedFirstDoseWinsOverMeals() {
        assertEquals(listOf(t(9), t(21)), base.withFirstDose(t(9)).times())
    }

    @Test fun anyTimeAndAsNeededIgnoreMeals() {
        assertEquals(listOf(t(8), t(20)), base.copy(withFood = WithFood.ANY).times())
        assertTrue(base.withFreq(FreqChoice.AS_NEEDED).times().isEmpty())
    }

    @Test fun editModeIgnoresMeals() {
        assertEquals(listOf(t(8), t(20)), base.copy(originalStock = 10.0).times())
    }
}
