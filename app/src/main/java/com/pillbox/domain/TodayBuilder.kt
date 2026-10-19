package com.pillbox.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class RowStatus { PENDING, TAKEN, SKIPPED }

/** next = the group to act on now; late = overdue past the threshold; future = a day that has not started. */
enum class GroupState { DONE, NEXT, LATE, UPCOMING, FUTURE_DAY, MISSED_DAY }

data class DoseRow(
    val medicine: Medicine,
    val schedule: Schedule,
    val planned: LocalDateTime,
    val event: DoseEvent?,
    /** Still not marked after the next dose of the same medicine was due (or after 11:59 PM). */
    val missed: Boolean,
) {
    val status: RowStatus
        get() = when (event?.status) {
            DoseStatus.TAKEN -> RowStatus.TAKEN
            DoseStatus.SKIPPED -> RowStatus.SKIPPED
            else -> RowStatus.PENDING
        }
}

data class DoseGroup(val time: LocalDateTime, val rows: List<DoseRow>, val state: GroupState, val minutesLate: Int) {
    val pending: List<DoseRow> get() = rows.filter { it.status == RowStatus.PENDING }
    val actionable: Boolean get() = state == GroupState.NEXT || state == GroupState.LATE
}

data class TodayModel(val date: LocalDate, val groups: List<DoseGroup>, val taken: Int, val total: Int)

/** Which doses a day shows and what state each group is in. Pure functions. */
object TodayBuilder {
    /** The Late setting's threshold; the Settings screen (M8) will replace this default. */
    val DEFAULT_LATE_THRESHOLD = LateThreshold.MIN_30

    fun build(
        items: List<Triple<Medicine, Schedule, List<Pause>>>,
        events: List<DoseEvent>,
        date: LocalDate,
        now: LocalDateTime,
        threshold: LateThreshold = DEFAULT_LATE_THRESHOLD,
    ): TodayModel {
        val today = now.toLocalDate()
        val rows = items.flatMap { (med, schedule, pauses) ->
            val planned = ScheduleEngine.visibleDoses(schedule, pauses, date)
            planned.mapIndexed { i, at ->
                val event = events.firstOrNull { it.medicineId == med.id && it.planned == at }
                val next = planned.getOrNull(i + 1)
                val missed = event == null && when {
                    date < today -> true
                    date == today -> LateRules.isMissed(now, at, next)
                    else -> false
                }
                DoseRow(med, schedule, at, event, missed)
            }
        }.sortedWith(compareBy({ it.planned }, { it.medicine.name.lowercase() }))

        var nextAssigned = false
        val groups = rows.groupBy { it.planned }.toSortedMap().map { (time, groupRows) ->
            val hasPending = groupRows.any { it.status == RowStatus.PENDING }
            var late = 0
            val state = when {
                !hasPending -> GroupState.DONE
                date < today -> GroupState.MISSED_DAY
                date > today -> GroupState.FUTURE_DAY
                time.plusMinutes(threshold.minutes.toLong()).isBefore(now) -> {
                    late = Duration.between(time, now).toMinutes().toInt()
                    GroupState.LATE
                }
                !nextAssigned -> { nextAssigned = true; GroupState.NEXT }
                else -> GroupState.UPCOMING
            }
            DoseGroup(time, groupRows, state, late)
        }
        return TodayModel(date, groups, rows.count { it.status == RowStatus.TAKEN }, rows.size)
    }
}
