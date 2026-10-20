package com.pillbox.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** What happened to one planned dose. Paused doses and doses still to come are not recorded at all. */
enum class DoseOutcome { ON_TIME, LATE, SKIPPED, MISSED }

data class DoseRecord(
    val medicine: Medicine,
    val planned: LocalDateTime,
    val outcome: DoseOutcome,
    val lateMinutes: Int = 0,
    /** The reason code the person gave when skipping, if any. */
    val reason: String? = null,
) {
    /** Skipped and missed doses both count as "Missed" in History. */
    val needsAttention get() = outcome != DoseOutcome.ON_TIME
}

enum class HistoryMark { ALL_TAKEN, SOME_MISSED, MISSED_DAY, PAUSED, PENDING, NONE, FUTURE }

/** One calendar cell. [date] is null for the empty cells before the first day. */
data class HistoryDay(val date: LocalDate?, val mark: HistoryMark)

/** How many times an as-needed medicine was taken in the window. Not part of the on-time share, since nothing was planned. */
data class AsNeededCount(val medicine: Medicine, val count: Int)

data class History(
    val start: LocalDate,
    val today: LocalDate,
    val marks: Map<LocalDate, HistoryMark>,
    val records: List<DoseRecord>,
    val asNeeded: List<AsNeededCount> = emptyList(),
) {
    val onTime get() = records.count { it.outcome == DoseOutcome.ON_TIME }
    val late get() = records.count { it.outcome == DoseOutcome.LATE }
    val missed get() = records.count { it.outcome == DoseOutcome.SKIPPED || it.outcome == DoseOutcome.MISSED }
    val total get() = records.size

    /** Share of doses taken on time, or null when there is nothing to count yet. */
    val percent: Int? get() = if (total == 0) null else (onTime * 100.0 / total).roundToInt()

    /** Late, missed and skipped doses, newest first. */
    val attention: List<DoseRecord> get() = records.filter { it.needsAttention }.sortedByDescending { it.planned }

    /** Calendar rows, [firstDay] first. Empty cells before the window, "future" cells after today. */
    fun weeks(firstDay: DayOfWeek): List<List<HistoryDay>> {
        val back = (start.dayOfWeek.value - firstDay.value + 7) % 7
        var day = start.minusDays(back.toLong())
        val rows = mutableListOf<List<HistoryDay>>()
        while (!day.isAfter(today)) {
            rows += (0 until 7).map { i ->
                val d = day.plusDays(i.toLong())
                when {
                    d.isBefore(start) -> HistoryDay(null, HistoryMark.NONE)
                    d.isAfter(today) -> HistoryDay(d, HistoryMark.FUTURE)
                    else -> HistoryDay(d, marks[d] ?: HistoryMark.NONE)
                }
            }
            day = day.plusDays(7)
        }
        return rows
    }
}

/** Pure rules behind the History screen and the doctor report. */
object HistoryBuilder {
    const val DEFAULT_DAYS = 30

    /**
     * The last [days] days ending today. A dose counts when it was taken or skipped, or when it is missed
     * (no answer after the next dose was due, or after the day ended). Paused doses are left out. As-needed doses are counted apart in [History.asNeeded].
     */
    fun build(
        items: List<Triple<Medicine, Schedule, List<Pause>>>,
        events: List<DoseEvent>,
        now: LocalDateTime,
        days: Int = DEFAULT_DAYS,
    ): History {
        val today = now.toLocalDate()
        val start = today.minusDays((days - 1).toLong())
        val byDose = events.filter { it.planned != null }.associateBy { it.medicineId to it.planned }
        val records = mutableListOf<DoseRecord>()
        val marks = mutableMapOf<LocalDate, HistoryMark>()

        for (offset in 0 until days) {
            val date = start.plusDays(offset.toLong())
            var counted = 0
            var good = 0
            var pending = false
            var anyDoses = false
            var allPaused = true
            for ((med, schedule, pauses) in items) {
                val planned = ScheduleEngine.plannedDoses(schedule, date)
                if (planned.isNotEmpty()) anyDoses = true
                val visible = ScheduleEngine.visibleDoses(schedule, pauses, date)
                if (visible.isNotEmpty()) allPaused = false
                visible.forEachIndexed { i, at ->
                    val event = byDose[med.id to at]
                    // A stopped medicine does not count doses from the day it was stopped that nobody answered.
                    if (event == null && med.status == MedicineStatus.STOPPED && med.endedOn != null && !date.isBefore(med.endedOn)) return@forEachIndexed
                    val outcome: DoseOutcome? = when (event?.status) {
                        DoseStatus.TAKEN -> if ((event.lateMinutes ?: 0) > 0) DoseOutcome.LATE else DoseOutcome.ON_TIME
                        DoseStatus.SKIPPED -> DoseOutcome.SKIPPED
                        DoseStatus.MISSED -> DoseOutcome.MISSED
                        else -> if (LateRules.isMissed(now, at, visible.getOrNull(i + 1))) DoseOutcome.MISSED else null
                    }
                    if (outcome == null) { pending = true; return@forEachIndexed }
                    records += DoseRecord(med, at, outcome, event?.lateMinutes ?: 0, event?.skipReason)
                    counted++
                    if (outcome == DoseOutcome.ON_TIME || outcome == DoseOutcome.LATE) good++
                }
            }
            marks[date] = when {
                pending -> HistoryMark.PENDING
                counted == 0 -> if (anyDoses && allPaused) HistoryMark.PAUSED else HistoryMark.NONE
                good == counted -> HistoryMark.ALL_TAKEN
                good == 0 -> HistoryMark.MISSED_DAY
                else -> HistoryMark.SOME_MISSED
            }
        }
        val prn = events.filter { it.planned == null && it.status == DoseStatus.TAKEN && it.taken?.toLocalDate()?.let { d -> !d.isBefore(start) && !d.isAfter(today) } == true }
            .groupingBy { it.medicineId }.eachCount()
        val asNeeded = items.mapNotNull { (m, _, _) -> prn[m.id]?.let { AsNeededCount(m, it) } }.sortedBy { it.medicine.name.lowercase() }
        return History(start, today, marks, records, asNeeded)
    }
}
