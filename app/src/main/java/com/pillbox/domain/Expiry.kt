package com.pillbox.domain

import java.time.LocalDate
import java.time.YearMonth

enum class ExpiryLevel { NONE, OK, SOON, EXPIRED }

/** [lastDay] is the last day the medicine can be used: the end of the printed month. */
data class ExpiryInfo(val level: ExpiryLevel, val daysLeft: Int?, val lastDay: LocalDate?)

/**
 * The optional expiry date of a box. Boxes print a month and a year, so that is all the app asks for; the last day is the end of that month.
 * The app only warns. It never says a medicine is safe.
 */
object ExpiryRules {
    /** "Expires soon" starts this many days before the last day. */
    const val SOON_DAYS = 30

    /** Days before the last day on which a notification is shown, besides the last day itself. */
    val NOTICE_DAYS = listOf(SOON_DAYS, 7)

    fun parse(text: String?): YearMonth? = text?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    fun info(expiry: YearMonth?, today: LocalDate): ExpiryInfo {
        if (expiry == null) return ExpiryInfo(ExpiryLevel.NONE, null, null)
        val last = expiry.atEndOfMonth()
        val left = java.time.temporal.ChronoUnit.DAYS.between(today, last).toInt()
        val level = when {
            left < 0 -> ExpiryLevel.EXPIRED
            left <= SOON_DAYS -> ExpiryLevel.SOON
            else -> ExpiryLevel.OK
        }
        return ExpiryInfo(level, left, last)
    }

    /** Medicines to tell the person about today: 30 days before, 7 days before and on the last day. Only boxes that still have something in them. */
    fun noticesFor(medicines: List<Medicine>, today: LocalDate): List<Pair<Medicine, ExpiryInfo>> =
        medicines.mapNotNull { m ->
            if (m.stock <= 0.0) return@mapNotNull null
            val info = info(m.expiry, today)
            val left = info.daysLeft ?: return@mapNotNull null
            if (left == 0 || left in NOTICE_DAYS) m to info else null
        }.sortedBy { it.second.daysLeft }

    /** The years offered in the picker: from last year to 15 years ahead. */
    fun yearRange(today: LocalDate): IntRange = (today.year - 1)..(today.year + 15)
}
