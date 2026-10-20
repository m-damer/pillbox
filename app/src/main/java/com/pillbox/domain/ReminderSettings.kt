package com.pillbox.domain

import java.time.LocalTime

/** The three alarm tones the app carries (res/raw). */
enum class Tone { GENTLE_BELL, MORNING_CHIME, LOUD_ALARM }

/**
 * A quiet window such as 10:00 PM to 7:00 AM. It may cross midnight. A dose reminder is never silenced unless
 * [doseRings] is turned off on purpose; refill alerts are muted by default.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val from: LocalTime = LocalTime.of(22, 0),
    val until: LocalTime = LocalTime.of(7, 0),
    val doseRings: Boolean = true,
    val muteRefill: Boolean = true,
) {
    /** True when [t] is inside the window. Start is inside, end is outside. From equal to until means no window. */
    fun contains(t: LocalTime): Boolean = when {
        from == until -> false
        from < until -> t >= from && t < until
        else -> t >= from || t < until
    }

    fun activeAt(t: LocalTime): Boolean = enabled && contains(t)

    /** Dose reminders go quiet only when quiet hours are on, it is that time, and the user chose not to ring. */
    fun silencesDoseAt(t: LocalTime): Boolean = activeAt(t) && !doseRings

    fun silencesRefillAt(t: LocalTime): Boolean = activeAt(t) && muteRefill
}

/** When the user eats. Used to suggest dose times for before, with and after meal medicines. */
data class MealTimes(
    val breakfast: LocalTime = LocalTime.of(7, 30),
    val lunch: LocalTime = LocalTime.of(13, 30),
    val dinner: LocalTime = LocalTime.of(20, 0),
    val bedtime: LocalTime = LocalTime.of(22, 0),
) {
    enum class Meal { BREAKFAST, LUNCH, DINNER, BEDTIME }

    operator fun get(m: Meal): LocalTime = when (m) {
        Meal.BREAKFAST -> breakfast; Meal.LUNCH -> lunch; Meal.DINNER -> dinner; Meal.BEDTIME -> bedtime
    }

    fun with(m: Meal, t: LocalTime): MealTimes = when (m) {
        Meal.BREAKFAST -> copy(breakfast = t); Meal.LUNCH -> copy(lunch = t); Meal.DINNER -> copy(dinner = t); Meal.BEDTIME -> copy(bedtime = t)
    }

    /**
     * Suggested dose times for a medicine taken [perDay] times (1 to 3) with [food]: breakfast, then dinner, then lunch in
     * between. Before a meal is 30 minutes earlier, after a meal 30 minutes later. Null when the food rule is "any time",
     * so the usual times stay.
     */
    fun suggest(food: WithFood, perDay: Int): List<LocalTime>? {
        val offset = when (food) {
            WithFood.ANY -> return null
            WithFood.BEFORE_MEAL -> -OFFSET_MINUTES
            WithFood.WITH_MEAL -> 0L
            WithFood.AFTER_MEAL -> OFFSET_MINUTES
        }
        val meals = when (perDay) {
            1 -> listOf(breakfast)
            2 -> listOf(breakfast, dinner)
            3 -> listOf(breakfast, lunch, dinner)
            else -> return null
        }
        return meals.map { it.plusMinutes(offset) }
    }

    companion object { const val OFFSET_MINUTES = 30L }
}

/** Everything on the Sound and repeat, Quiet hours and Meal times screens. */
data class ReminderSettings(
    val tone: Tone = Tone.GENTLE_BELL,
    val vibration: Boolean = true,
    /** Minutes between repeats when nobody answers. */
    val repeatMinutes: Int = 5,
    /** Keep ringing (repeat) until the user answers. Off: ring once. */
    val keepRinging: Boolean = true,
    val quiet: QuietHours = QuietHours(),
    val meals: MealTimes = MealTimes(),
) {
    /** How many repeats after the first ring. */
    val maxRepeats: Int get() = if (keepRinging) ReminderPlanner.MAX_NAGS else 0

    companion object { val REPEAT_CHOICES = listOf(2, 5, 10) }
}
