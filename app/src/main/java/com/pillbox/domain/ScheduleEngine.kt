package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** Which planned doses exist on a calendar day. Pure functions. */
object ScheduleEngine {

    /**
     * Doses that ring on [date], sorted. A dose that rings after midnight belongs to the day it rings.
     * As-needed medicines have no planned doses.
     */
    fun plannedDoses(schedule: Schedule, date: LocalDate): List<LocalDateTime> {
        if (schedule.shifts.isEmpty()) return rawDoses(schedule, date)
        // A moved dose can land on the next day, so look at the days around and keep what rings on [date].
        return (-1L..1L).flatMap { rawDoses(schedule, date.plusDays(it)) }
            .mapNotNull { if (it in schedule.shifts) schedule.shifts[it] else it }
            .filter { it.toLocalDate() == date }
            .distinct()
            .sorted()
    }

    /** The doses the schedule itself makes on [date], before any moves. */
    private fun rawDoses(schedule: Schedule, date: LocalDate): List<LocalDateTime> {
        val from = schedule.startTime?.let { schedule.start.atTime(it) }
        val all = when (val f = schedule.frequency) {
            is Frequency.FixedTimes ->
                if (schedule.isActiveOn(date) && f.days.matches(date)) f.times.sorted().map { date.atTime(it) } else emptyList()

            is Frequency.EveryNHours ->
                (cycle(schedule, f, date.minusDays(1)) + cycle(schedule, f, date))
                    .filter { it.toLocalDate() == date }
                    .sorted()

            is Frequency.AsNeeded -> emptyList()
        }
        return all.filter { from == null || !it.isBefore(from) }
    }

    /**
     * Every-few-hours cycle that starts on [cycleDay]: first dose, then first + N, first + 2N ...
     * A dose is added only if at least N hours remain before the next day's first dose,
     * so the gap is never below N. That gives floor(24 / N) doses.
     */
    fun cycle(schedule: Schedule, f: Frequency.EveryNHours, cycleDay: LocalDate): List<LocalDateTime> {
        if (!schedule.isActiveOn(cycleDay) || !f.days.matches(cycleDay)) return emptyList()
        val first = cycleDay.atTime(f.first)
        return (0 until 24 / f.hours).map { first.plusHours((it * f.hours).toLong()) }
    }

    /** First visible planned dose strictly after [after], looking up to a year ahead. Null for as-needed or when none is left. */
    fun nextDose(schedule: Schedule, pauses: List<Pause>, after: LocalDateTime): LocalDateTime? {
        val firstDay = after.toLocalDate()
        for (i in 0..400L) {
            val day = firstDay.plusDays(i)
            if (schedule.end != null && day.isAfter(schedule.end.plusDays(1))) return null
            visibleDoses(schedule, pauses, day).firstOrNull { it.isAfter(after) }?.let { return it }
        }
        return null
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

    /**
     * Restart a stopped or finished medicine with the same details: starts now, keeps the course length,
     * and has no doses earlier than the restart time on the first day.
     */
    fun restarted(schedule: Schedule, now: LocalDateTime): Schedule {
        val today = now.toLocalDate()
        val length = schedule.end?.let { java.time.temporal.ChronoUnit.DAYS.between(schedule.start, it) }
        val f = schedule.frequency
        val frequency = when {
            f is Frequency.FixedTimes && f.days is DayPattern.EveryNDays -> f.copy(days = f.days.copy(anchor = today))
            f is Frequency.EveryNHours && f.days is DayPattern.EveryNDays -> f.copy(days = f.days.copy(anchor = today))
            else -> f
        }
        return schedule.copy(
            start = today, end = length?.let { today.plusDays(it) }, frequency = frequency,
            startTime = now.toLocalTime().withSecond(0).withNano(0),
        )
    }
}
