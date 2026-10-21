package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** What the person chose on the Doctor report screen. */
data class ReportOptions(
    val days: Int = 30,
    val medicines: Boolean = true,
    val dayByDay: Boolean = true,
    val missedLate: Boolean = true,
) {
    companion object { val PERIODS = listOf(7, 30, 90) }
}

/** One medicine's row in the report. */
data class ReportMedicine(val medicine: Medicine, val schedule: Schedule, val onTime: Int, val late: Int, val missed: Int, val asNeeded: Int = 0)

/** The symbol for one day in the day-by-day grid. */
enum class ReportDay { OK, LATE, MISSED, PAUSED, PENDING, NONE }

data class ReportModel(
    val patient: String,
    val start: LocalDate,
    val end: LocalDate,
    val created: LocalDate,
    val history: History,
    val medicines: List<ReportMedicine>,
    val options: ReportOptions,
) {
    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

    /** Missed or skipped wins over late, late wins over all-on-time. */
    fun dayMark(date: LocalDate): ReportDay {
        val day = history.records.filter { it.planned.toLocalDate() == date }
        return when {
            day.any { it.outcome == DoseOutcome.MISSED || it.outcome == DoseOutcome.SKIPPED } -> ReportDay.MISSED
            day.any { it.outcome == DoseOutcome.LATE } -> ReportDay.LATE
            day.isNotEmpty() -> ReportDay.OK
            history.marks[date] == HistoryMark.PENDING -> ReportDay.PENDING
            history.marks[date] == HistoryMark.PAUSED -> ReportDay.PAUSED
            else -> ReportDay.NONE
        }
    }
}

/** Pure rules behind the doctor report. The PDF drawing only lays out what this decides. */
object ReportBuilder {
    fun build(
        patient: String,
        items: List<Triple<Medicine, Schedule, List<Pause>>>,
        events: List<DoseEvent>,
        now: LocalDateTime,
        options: ReportOptions,
    ): ReportModel {
        val history = HistoryBuilder.build(items, events, now, options.days)
        val rows = items
            .filter { (m, _, _) -> m.status == MedicineStatus.ACTIVE || history.records.any { it.medicine.id == m.id } || history.asNeeded.any { it.medicine.id == m.id } }
            .map { (m, s, _) ->
                val mine = history.records.filter { it.medicine.id == m.id }
                ReportMedicine(
                    m, s,
                    onTime = mine.count { it.outcome == DoseOutcome.ON_TIME },
                    late = mine.count { it.outcome == DoseOutcome.LATE },
                    missed = mine.count { it.outcome == DoseOutcome.SKIPPED || it.outcome == DoseOutcome.MISSED },
                    asNeeded = history.asNeeded.firstOrNull { it.medicine.id == m.id }?.count ?: 0,
                )
            }
            .sortedBy { it.medicine.name.lowercase() }
        return ReportModel(patient, history.start, history.today, now.toLocalDate(), history, rows, options)
    }
}
