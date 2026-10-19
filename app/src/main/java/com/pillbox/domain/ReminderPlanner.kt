package com.pillbox.domain

import java.time.LocalDateTime

/** Which dose rings next and which doses are waiting at a given time. Pure functions; the alarm code only calls these. */
object ReminderPlanner {
    /** Earliest dose strictly after [after] across all medicines. One alarm is armed for this time and re-armed after it fires. */
    fun nextDue(items: List<Triple<Medicine, Schedule, List<Pause>>>, after: LocalDateTime): LocalDateTime? =
        items.mapNotNull { (_, schedule, pauses) -> ScheduleEngine.nextDose(schedule, pauses, after) }.minOrNull()

    /** Doses planned exactly at [at] that are not yet taken or skipped. */
    fun dueRows(items: List<Triple<Medicine, Schedule, List<Pause>>>, events: List<DoseEvent>, at: LocalDateTime): List<DoseRow> =
        TodayBuilder.build(items, events, at.toLocalDate(), at).groups.firstOrNull { it.time == at }?.pending.orEmpty()

    const val SNOOZE_MINUTES = 15L
    const val NAG_MINUTES = 5L
    const val MAX_NAGS = 3
}
