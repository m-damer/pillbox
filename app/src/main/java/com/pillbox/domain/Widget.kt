package com.pillbox.domain

import java.time.Duration
import java.time.LocalDateTime

enum class WidgetSize(val maxRows: Int) { SMALL(0), MEDIUM(2), LARGE(5) }

/** What the home screen widget shows. One choice for every widget on the home screen. [personId] null means "me". */
data class WidgetSettings(
    val size: WidgetSize = WidgetSize.MEDIUM,
    val personId: Long? = null,
    val showNames: Boolean = true,
    val takeAll: Boolean = true,
)

/** One line of the widget: a dose waiting, with its planned time. */
data class WidgetModel(
    /** The group of doses that are next (or late). Null when nothing is waiting today. */
    val at: LocalDateTime?,
    val late: Boolean,
    /** Minutes until [at], or minutes late when [late]. */
    val minutes: Long,
    /** Every dose waiting today, next first. The widget shows as many as fit. */
    val rows: List<DoseRow>,
    /** Doses at exactly [at]: these are what "Take all" marks. */
    val nextRows: List<DoseRow>,
    /** True when something was planned today and all of it is taken or skipped. */
    val allDone: Boolean,
    /** The first dose after today's, when nothing is waiting today. */
    val following: LocalDateTime?,
)

object WidgetBuilder {
    /**
     * Doses waiting today for one person. [items] are all active medicines; [myPersonId] is who "me" is.
     * Late doses come first, then the next ones, then later ones.
     */
    fun build(
        items: List<Triple<Medicine, Schedule, List<Pause>>>,
        events: List<DoseEvent>,
        now: LocalDateTime,
        settings: WidgetSettings,
        myPersonId: Long?,
        threshold: LateThreshold = TodayBuilder.DEFAULT_LATE_THRESHOLD,
    ): WidgetModel {
        val who = settings.personId ?: myPersonId
        val mine = if (who == null) items else items.filter { it.first.personId == who }
        val today = TodayBuilder.build(mine, events, now.toLocalDate(), now, threshold)
        val waiting = today.groups.filter { it.state == GroupState.NEXT || it.state == GroupState.LATE || it.state == GroupState.UPCOMING }
            .filter { it.pending.isNotEmpty() }
        val head = waiting.firstOrNull()
        val rows = waiting.flatMap { it.pending }
        return WidgetModel(
            at = head?.time,
            late = head?.state == GroupState.LATE,
            minutes = head?.let { Duration.between(if (head.state == GroupState.LATE) head.time else now, if (head.state == GroupState.LATE) now else head.time).toMinutes().coerceAtLeast(0) } ?: 0,
            rows = rows,
            nextRows = head?.pending.orEmpty(),
            allDone = head == null && today.total > 0,
            following = if (head == null) ReminderPlanner.nextDue(mine, now) else null,
        )
    }

    /** How many dose lines fit in a widget [heightDp] tall, with the header (and the button when [button]). Never fewer than one. */
    fun rowsThatFit(heightDp: Float, button: Boolean, max: Int): Int {
        val header = 84f
        val buttonHeight = if (button) 56f else 0f
        val line = 56f
        return ((heightDp - header - buttonHeight) / line).toInt().coerceIn(1, maxOf(1, max))
    }
}
