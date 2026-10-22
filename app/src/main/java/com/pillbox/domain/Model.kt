package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure Kotlin domain model. No Android imports, so every rule can be unit tested on the JVM. */

/**
 * The medicine types, in the order the type popup shows them. Saved by name, so a name never changes.
 * ML is the Syrup type (it was the first liquid, so its name stays). Each type has its own unit word and icon.
 */
enum class DoseUnit { TABLET, CAPSULE, POWDER, ML, SOLUTION, DROPS, SPRAY, INHALER, SUPPOSITORY, CREAM, OINTMENT, GEL, PATCH, INJECTION, OTHER }

/** What a pack is called when more is bought: box, bottle, tube or inhaler. */
enum class PackKind { BOX, BOTTLE, TUBE, INHALER }

object MedicineTypes {
    fun packKind(t: DoseUnit): PackKind = when (t) {
        DoseUnit.ML, DoseUnit.SOLUTION, DoseUnit.DROPS, DoseUnit.SPRAY -> PackKind.BOTTLE
        DoseUnit.CREAM, DoseUnit.OINTMENT, DoseUnit.GEL -> PackKind.TUBE
        DoseUnit.INHALER -> PackKind.INHALER
        else -> PackKind.BOX
    }

    /** A tube of cream has no honest count of applications, so for these the number is only an estimate. */
    fun isEstimate(t: DoseUnit) = packKind(t) == PackKind.TUBE
}

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

    /** First dose, then every [hours] hours, wrapping past midnight (1..24), on the days [days] allows. */
    data class EveryNHours(val first: LocalTime, val hours: Int, val days: DayPattern = DayPattern.EveryDay) : Frequency {
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
    /** Time of the first dose on [start]. Doses earlier than this on the start day do not exist. Null = from the start of the day. */
    val startTime: LocalTime? = null,
    /**
     * Doses moved by "Move next doses" (Taken late): the planned time they would have had to the time they have now.
     * A null value means that dose was dropped. Only a day or two of these are kept.
     */
    val shifts: Map<LocalDateTime, LocalDateTime?> = emptyMap(),
) {
    fun isActiveOn(date: LocalDate) = !date.isBefore(start) && (end == null || !date.isAfter(end))
}

enum class MedicineStatus { ACTIVE, STOPPED, FINISHED }

/** Who a person is to the user, for the "Who is this?" chips. ME is the owner of the phone. */
enum class Relation { ME, PARENT, CHILD, PARTNER, OTHER }

/**
 * [colorIndex] picks an avatar color (see PeopleRules.COLORS). [ring] is whether this person's doses ring on this phone.
 * [birthYear] is optional and only the owner's is used (shown on the doctor report next to the name).
 */
data class Person(
    val id: Long = 0,
    val name: String,
    val isMe: Boolean = false,
    val sortOrder: Int = 0,
    val relation: Relation = if (isMe) Relation.ME else Relation.OTHER,
    val colorIndex: Int = 0,
    val ring: Boolean = true,
    val birthYear: Int? = null,
)

/** [packSize] is tablets (or doses) per box, used by Add stock. Frequency details live in [Schedule]. */
data class Medicine(
    val id: Long = 0,
    val personId: Long,
    val name: String,
    val strength: String? = null,
    val unit: DoseUnit = DoseUnit.TABLET,
    val doseAmount: Double = 1.0,
    val stock: Double = 0.0,
    val packSize: Int? = null,
    val status: MedicineStatus = MedicineStatus.ACTIVE,
    /** Day the course finished or the user stopped it. Null while active. */
    val endedOn: LocalDate? = null,
    /** Optional printed expiry month of the box. The last day is the end of that month. */
    val expiry: java.time.YearMonth? = null,
)

enum class DoseStatus { PLANNED, TAKEN, SKIPPED, MISSED }

data class DoseEvent(
    val id: Long = 0,
    val medicineId: Long = 0,
    val planned: LocalDateTime?,       // null for as-needed doses
    val taken: LocalDateTime? = null,
    val status: DoseStatus = DoseStatus.PLANNED,
    val skipReason: String? = null,
    val early: Boolean = false,
    val lateMinutes: Int? = null,
    /** Taken earlier and logged afterwards ("I took it" on a missed dose), not marked at the time. */
    val loggedLater: Boolean = false,
)

/** Every medicine is paused. [until] is when the first one resumes, or null if all wait for the user. */
data class AllPaused(val until: LocalDateTime?)

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

        /** The pause that is on at [now], if any. */
        fun activeAt(pauses: List<Pause>, now: LocalDateTime): Pause? = pauses.firstOrNull { it.covers(now) }

        /**
         * When every medicine is paused: the moment the first one comes back (null = all wait for the user).
         * Returns [AllPaused] only if there is at least one medicine and each has a pause on at [now].
         */
        fun allPaused(pausesPerMedicine: List<List<Pause>>, now: LocalDateTime): AllPaused? {
            if (pausesPerMedicine.isEmpty()) return null
            val active = pausesPerMedicine.map { activeAt(it, now) ?: return null }
            return AllPaused(active.mapNotNull { it.until }.minOrNull())
        }
    }
}
