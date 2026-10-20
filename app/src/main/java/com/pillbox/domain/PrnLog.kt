package com.pillbox.domain

import java.time.LocalDateTime

/** What the as-needed screen shows: today's taken times (newest first), the daily maximum and what Take now may do. */
data class PrnDay(val takenToday: List<LocalDateTime>, val max: Int?, val minGapMinutes: Int, val check: TakeCheck) {
    val count get() = takenToday.size
    val limitReached get() = check is TakeCheck.DailyMaxReached
    /** When the next dose is allowed, or null when it is allowed now. */
    val nextAllowed: LocalDateTime? get() = when (check) {
        is TakeCheck.TooSoon -> check.nextAllowed
        is TakeCheck.DailyMaxReached -> check.nextAllowed
        TakeCheck.Allowed -> null
    }
}

object PrnRules {
    /** [recentTaken] are the medicine's taken times from the last day or two, any order. */
    fun day(now: LocalDateTime, recentTaken: List<LocalDateTime>, minGapMinutes: Int, max: Int?): PrnDay {
        val today = recentTaken.filter { it.toLocalDate() == now.toLocalDate() && !it.isAfter(now) }.sortedDescending()
        return PrnDay(today, max, minGapMinutes, LateRules.asNeededCheck(now, recentTaken, minGapMinutes, max))
    }
}
