package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure Kotlin domain model. No Android imports, so every rule can be unit tested on the JVM. */

enum class DoseUnit { TABLET, CAPSULE, ML, DROPS, INJECTION }

enum class WithFood { ANY, BEFORE_MEAL, WITH_MEAL, AFTER_MEAL }

/** Which calendar days a fixed-time medicine runs on. */
sealed interface DayPattern {
    fun matches(date: LocalDate): Boolean

    data object EveryDay : DayPattern {
        override fun matches(date: LocalDate) = true
    }

    data class DaysOfWeek(val days: Set<DayOfWeek>) : DayPattern {
        init { require(days.isNotEmpty()) { "Pick at least one day" } }
        override fun matches(date: LocalDate) = date.dayOfWeek in days
    }

    /** Day 1 is [anchor]; then every [n] days. After a pause the anchor moves to the resume day. */
    data class EveryNDays(val n: Int, val anchor: LocalDate) : DayPattern {
        init { require(n >= 2) { "Every N days needs N >= 2" } }
        override fun matches(date: LocalDate): Boolean {
            val d = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
            return d >= 0 && d % n == 0L
        }
    }
}

sealed interface Frequency {
    /** Once, twice, 3 times a day, chosen days, every N days. */
    data class FixedTimes(val times: List<LocalTime>, val days: DayPattern = DayPattern.EveryDay) : Frequency {
        init { require(times.isNotEmpty()) { "At least one time" } }
    }

    /** First dose, then every [hours] hours, wrapping past midnight (1..24). */
    data class EveryNHours(val first: LocalTime, val hours: Int) : Frequency {
        init { require(hours in 1..24) { "Hours must be 1..24" } }
    }

    /** No due time. Guarded by a minimum gap and an optional daily maximum. */
    data class AsNeeded(val minGapMinutes: Int = 6 * 60, val maxPerDay: Int? = 4) : Frequency
}

data class Schedule(
    val start: LocalDate,
    val end: LocalDate? = null,
    val frequency: Frequency,
    val withFood: WithFood = WithFood.ANY,
) {
    fun isActiveOn(date: LocalDate) = !date.isBefore(start) && (end == null || !date.isAfter(end))
}

enum class DoseStatus { PLANNED, TAKEN, SKIPPED, MISSED }

data class DoseEvent(
    val planned: LocalDateTime?,       // null for as-needed doses
    val taken: LocalDateTime? = null,
    val status: DoseStatus = DoseStatus.PLANNED,
    val skipReason: String? = null,
    val early: Boolean = false,
    val lateMinutes: Int? = null,
)

/** A pause. [until] == null means "until I resume". */
data class Pause(val from: LocalDateTime, val until: LocalDateTime?) {
    fun covers(moment: LocalDateTime) = !moment.isBefore(from) && (until == null || moment.isBefore(until))

    companion object {
        const val AUTO_RESUME_HOUR = 9

        /** A timed pause ends at 9:00 AM on the end date. */
        fun forDays(start: LocalDateTime, days: Int): Pause {
            require(days >= 1)
            return Pause(start, start.toLocalDate().plusDays(days.toLong()).atTime(AUTO_RESUME_HOUR, 0))
        }

        fun untilResumed(start: LocalDateTime) = Pause(start, null)
    }
}
