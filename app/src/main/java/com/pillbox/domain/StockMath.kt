package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.floor

enum class StockLevel { ENOUGH, BUY_SOON, OUT }

/** Stock numbers for one medicine. [daysLeft] is null for as-needed medicines (no fixed use per day). */
data class StockInfo(val stock: Double, val daysLeft: Int?, val level: StockLevel)

/** Pure stock rules: days left, Buy soon, new totals after Add stock. */
object StockMath {
    const val DEFAULT_REMIND_DAYS = 5

    /** Average doses per day over a week, counting only the days the schedule runs. Null for as-needed. */
    fun averageDosesPerDay(schedule: Schedule): Double? {
        val (perDay, days) = when (val f = schedule.frequency) {
            is Frequency.FixedTimes -> f.times.size.toDouble() to f.days
            is Frequency.EveryNHours -> (24 / f.hours).toDouble() to f.days
            is Frequency.AsNeeded -> return null
        }
        return when (days) {
            DayPattern.EveryDay -> perDay
            is DayPattern.DaysOfWeek -> perDay * days.days.size / 7.0
            is DayPattern.EveryNDays -> perDay / days.n
        }
    }

    /** Whole days of stock left (rounded down), or null when there is no daily use. */
    fun daysLeft(medicine: Medicine, schedule: Schedule?): Int? {
        schedule ?: return null
        val perDay = (averageDosesPerDay(schedule) ?: return null) * medicine.doseAmount
        if (perDay <= 0.0) return null
        return floor(medicine.stock / perDay + 1e-9).toInt()
    }

    /** Doses still to come from [today] on until the end of the course, counting today's remaining ones as full. Null when ongoing. */
    private fun amountNeeded(medicine: Medicine, schedule: Schedule, today: LocalDate): Double? {
        val end = schedule.end ?: return null
        var doses = 0
        var day = maxOf(today, schedule.start)
        while (!day.isAfter(end)) {
            doses += ScheduleEngine.plannedDoses(schedule, day).size
            day = day.plusDays(1)
        }
        return doses * medicine.doseAmount
    }

    fun info(medicine: Medicine, schedule: Schedule?, today: LocalDate, remindDays: Int = DEFAULT_REMIND_DAYS): StockInfo {
        val days = daysLeft(medicine, schedule)
        val level = when {
            medicine.stock <= 0.0 -> StockLevel.OUT
            days == null || schedule == null -> StockLevel.ENOUGH
            else -> {
                val needed = amountNeeded(medicine, schedule, today)
                if (needed != null) (if (medicine.stock >= needed) StockLevel.ENOUGH else StockLevel.BUY_SOON)
                else if (days <= remindDays) StockLevel.BUY_SOON else StockLevel.ENOUGH
            }
        }
        return StockInfo(medicine.stock, days, level)
    }

    /** Stock after buying [boxes] boxes of [perBox]. */
    fun newTotal(stock: Double, boxes: Int, perBox: Int): Double = stock + boxes.toLong() * perBox

    /** Days the new total lasts, for the Add stock summary. Null when there is no daily use. */
    fun daysFor(total: Double, medicine: Medicine, schedule: Schedule?): Int? = daysLeft(medicine.copy(stock = total), schedule)

    /** Fraction for the stock bar: stock against one full box (or itself when the box size is unknown), 0..1. */
    fun barFraction(medicine: Medicine): Float {
        val full = (medicine.packSize ?: 0).toDouble().takeIf { it > 0.0 } ?: medicine.stock
        return if (full <= 0.0) 0f else (medicine.stock / full).coerceIn(0.0, 1.0).toFloat()
    }
}

enum class DayMark { ALL_TAKEN, MISSED, PENDING, NONE }
data class DayEntry(val date: LocalDate, val mark: DayMark)

/** The "Last 7 days" card on Medicine detail. */
data class WeekSummary(val days: List<DayEntry>, val taken: Int, val counted: Int, val missedDays: List<LocalDate>) {
    companion object {
        /**
         * Last 7 days ending [today]. Today is always pending (it can still be taken). A past day is
         * all-taken only if every visible dose was taken; paused doses are not counted.
         */
        fun build(schedule: Schedule?, pauses: List<Pause>, events: List<DoseEvent>, today: LocalDate): WeekSummary {
            val takenAt = events.filter { it.status == DoseStatus.TAKEN && it.planned != null }.map { it.planned!! }.toSet()
            val entries = mutableListOf<DayEntry>()
            var taken = 0
            var counted = 0
            val missed = mutableListOf<LocalDate>()
            for (i in 6 downTo 0) {
                val day = today.minusDays(i.toLong())
                val doses = if (schedule == null) emptyList() else ScheduleEngine.visibleDoses(schedule, pauses, day)
                if (day == today) {
                    val done = doses.count { it in takenAt }
                    taken += done; counted += done
                    entries += DayEntry(day, if (doses.isEmpty()) DayMark.NONE else DayMark.PENDING)
                    continue
                }
                if (doses.isEmpty()) { entries += DayEntry(day, DayMark.NONE); continue }
                val done = doses.count { it in takenAt }
                taken += done; counted += doses.size
                if (done == doses.size) entries += DayEntry(day, DayMark.ALL_TAKEN)
                else { entries += DayEntry(day, DayMark.MISSED); missed += day }
            }
            return WeekSummary(entries, taken, counted, missed)
        }
    }
}
