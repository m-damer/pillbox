package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** Which planned doses exist on a calendar day. Pure functions. */
object ScheduleEngine {

    /**
     * Doses that ring on [date], sorted. A dose that rings after midnight belongs to the day it rings.
     * As-needed medicines have no planned doses.
     */
    fun plannedDoses(schedule: Schedule, date: LocalDate): List<LocalDateTime> =
        when (val f = schedule.frequency) {
            is Frequency.FixedTimes ->
                if (schedule.isActiveOn(date) && f.days.matches(date)) f.times.sorted().map { date.atTime(it) } else emptyList()

            is Frequency.EveryNHours ->
                (cycle(schedule, f, date.minusDays(1)) + cycle(schedule, f, date))
                    .filter { it.toLocalDate() == date }
                    .sorted()

            is Frequency.AsNeeded -> emptyList()
        }

    /**
     * Every-few-hours cycle that starts on [cycleDay]: first dose, then first + N, first + 2N ...
     * A dose is added only if at least N hours remain before the next day's first dose,
     * so the gap is never below N. That gives floor(24 / N) doses.
     */
    fun cycle(schedule: Schedule, f: Frequency.EveryNHours, cycleDay: LocalDate): List<LocalDateTime> {
        if (!schedule.isActiveOn(cycleDay)) return emptyList()
        val first = cycleDay.atTime(f.first)
        return (0 until 24 / f.hours).map { first.plusHours((it * f.hours).toLong()) }
    }

    /** Planned doses on [date] that are not hidden by a pause. */
    fun visibleDoses(schedule: Schedule, pauses: List<Pause>, date: LocalDate): List<LocalDateTime> =
        plannedDoses(schedule, date).filter { d -> pauses.none { it.covers(d) } }

    /** A day is "paused" in History when it has doses and every one is hidden by a pause. */
    fun isPausedDay(schedule: Schedule, pauses: List<Pause>, date: LocalDate): Boolean {
        val all = plannedDoses(schedule, date)
        return all.isNotEmpty() && all.all { d -> pauses.any { it.covers(d) } }
    }

    /** Doses per normal day, used for "days left". As-needed uses [asNeededAverage]. */
    fun dosesPerDay(frequency: Frequency, asNeededAverage: Double = 1.0): Double = when (frequency) {
        is Frequency.FixedTimes -> frequency.times.size.toDouble()
        is Frequency.EveryNHours -> (24 / frequency.hours).toDouble()
        is Frequency.AsNeeded -> asNeededAverage
    }

    /** Whole days of stock left. Frozen while paused: the caller simply does not recompute. */
    fun daysLeft(stock: Double, doseAmount: Double, dosesPerDay: Double): Int {
        val perDay = doseAmount * dosesPerDay
        return if (perDay <= 0.0) Int.MAX_VALUE else kotlin.math.floor(stock / perDay).toInt()
    }

    /**
     * On resume: Every N days restarts counting from the resume day (first day on or after it).
     * Days of the week and hours need no change.
     */
    fun resumedOn(schedule: Schedule, resumeDate: LocalDate): Schedule {
        val f = schedule.frequency
        if (f is Frequency.FixedTimes && f.days is DayPattern.EveryNDays) {
            return schedule.copy(frequency = f.copy(days = f.days.copy(anchor = resumeDate)))
        }
        return schedule
    }
}
