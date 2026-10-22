package com.pillbox.domain

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Logging a dose that was taken but not marked in the app. Only the last [WINDOW_DAYS] days can be logged or corrected;
 * older doses stay as they were, because a medical record should not rest on guesses. A dose nobody logs stays Missed.
 */
object LateLog {
    const val WINDOW_DAYS = 7L

    /** A dose can be logged once its time has come and while it is inside the window. */
    fun canLog(planned: LocalDateTime, now: LocalDateTime): Boolean =
        !planned.isAfter(now) && !planned.toLocalDate().isBefore(now.toLocalDate().minusDays(WINDOW_DAYS))

    /**
     * The moment the person says they took it: the chosen clock time on the dose's day. A time more than 6 hours before the
     * planned time means after midnight (planned 11:30 PM, taken 12:10 AM). Null when that moment has not happened yet.
     */
    fun takenAt(planned: LocalDateTime, picked: LocalTime, now: LocalDateTime): LocalDateTime? {
        var at = planned.toLocalDate().atTime(picked)
        if (at.isBefore(planned.minusHours(6))) at = at.plusDays(1)
        return at.takeUnless { it.isAfter(now) }
    }
}
