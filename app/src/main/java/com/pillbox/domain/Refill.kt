package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The Refill alert screen: how many days before running out, at what time, and for whom. */
data class RefillSettings(
    val daysBefore: Int = DEFAULT_DAYS,
    val time: LocalTime = LocalTime.of(9, 0),
    val includePeople: Boolean = true,
) {
    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 30
        const val DEFAULT_DAYS = StockMath.DEFAULT_REMIND_DAYS
        val PRESETS = listOf(3, 5, 7, 10)
        fun clampDays(d: Int) = d.coerceIn(MIN_DAYS, MAX_DAYS)
    }
}

/** One medicine that needs a refill alert now. [daysLeft] is null only when [out] is true and there is no daily use. */
data class RefillAlert(val medicine: Medicine, val daysLeft: Int?, val runsOutOn: LocalDate?, val out: Boolean)

/** Pure rules for refill alerts. Android only shows what this decides. */
object RefillPlanner {

    /** The next time the daily alert should ring: today at the chosen time if still ahead, otherwise tomorrow. */
    fun nextFire(settings: RefillSettings, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(settings.time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Medicines to tell the person about today: active, not paused, low or out of stock.
     * With [RefillSettings.includePeople] off, only the "me" profile ([myPersonId]) is included.
     */
    fun due(
        items: List<Triple<Medicine, Schedule?, List<Pause>>>,
        settings: RefillSettings,
        now: LocalDateTime,
        myPersonId: Long?,
    ): List<RefillAlert> {
        val today = now.toLocalDate()
        return items
            .filter { (m, _, pauses) ->
                m.status == MedicineStatus.ACTIVE &&
                    Pause.activeAt(pauses, now) == null &&
                    (settings.includePeople || myPersonId == null || m.personId == myPersonId)
            }
            .mapNotNull { (m, s, _) ->
                val info = StockMath.info(m, s, today, settings.daysBefore)
                when (info.level) {
                    StockLevel.ENOUGH -> null
                    StockLevel.OUT -> RefillAlert(m, 0, today, out = true)
                    StockLevel.BUY_SOON -> RefillAlert(m, info.daysLeft, info.daysLeft?.let { today.plusDays(it.toLong()) }, out = false)
                }
            }
            .sortedBy { it.daysLeft ?: Int.MAX_VALUE }
    }

    /**
     * Preview on the settings screen: the date the alert first rings for a medicine with [daysLeft] days of stock.
     * That is today when it is already within [daysBefore] days.
     */
    fun firstAlertDate(today: LocalDate, daysLeft: Int, daysBefore: Int): LocalDate =
        if (daysLeft <= daysBefore) today else today.plusDays((daysLeft - daysBefore).toLong())
}
