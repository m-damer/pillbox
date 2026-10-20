package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalTime

/** One medicine in a time slot of the plan. [draftIndex] is the dose's place in the new medicine's own times, or null when it cannot be changed here. */
data class PlanEntry(val name: String, val unit: DoseUnit, val withFood: WithFood, val isNew: Boolean, val draftIndex: Int?)

data class PlanGroup(val time: LocalTime, val entries: List<PlanEntry>) {
    val changeable get() = entries.firstOrNull { it.isNew && it.draftIndex != null }?.draftIndex
}

/** The "Coming up" lines: when the course ends, or (when ongoing) the day the stock runs out. Both null means ongoing without a known refill day; [asNeeded] has no schedule at all. */
data class ComingUp(val name: String, val lastDose: LocalDate?, val refillBy: LocalDate?, val asNeeded: Boolean)

data class DailyPlan(val groups: List<PlanGroup>, val comingUp: List<ComingUp>)

/** The one-day plan shown before reminders start: every active medicine of the person plus the one being added. Pure Kotlin. */
object PlanBuilder {
    /**
     * [others] are the active medicines already saved; [draft] is the new one. [draftTimes] are its editable dose times (once, twice, 3 times a day),
     * empty for other kinds. The day shown is [today], or the new medicine's first day when it starts later.
     */
    fun build(
        others: List<Pair<Medicine, Schedule>>,
        draft: Medicine,
        draftSchedule: Schedule,
        draftTimes: List<LocalTime>,
        today: LocalDate,
    ): DailyPlan {
        val day = maxOf(today, draftSchedule.start)
        val slots = sortedMapOf<LocalTime, MutableList<PlanEntry>>()
        fun add(m: Medicine, s: Schedule, isNew: Boolean) {
            ScheduleEngine.plannedDoses(s, day).map { it.toLocalTime() }.distinct().forEach { t ->
                val idx = if (isNew) draftTimes.indexOf(t).takeIf { it >= 0 } else null
                slots.getOrPut(t) { mutableListOf() }.add(PlanEntry(m.name, m.unit, s.withFood, isNew, idx))
            }
        }
        others.forEach { (m, s) -> add(m, s, false) }
        add(draft, draftSchedule, true)
        val groups = slots.map { (t, e) -> PlanGroup(t, e.sortedBy { it.isNew }) }
        val comingUp = (others + (draft to draftSchedule)).map { (m, s) ->
            val asNeeded = s.frequency is Frequency.AsNeeded
            val refill = if (s.end == null) StockMath.daysLeft(m, s)?.let { today.plusDays(it.toLong()) } else null
            ComingUp(m.name, s.end, refill, asNeeded)
        }
        return DailyPlan(groups, comingUp)
    }
}
