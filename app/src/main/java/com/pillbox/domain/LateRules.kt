package com.pillbox.domain

import java.time.Duration
import java.time.LocalDateTime

enum class LateSetting { ASK, MOVE_NEXT, KEEP }

enum class LateThreshold(val minutes: Int) { MIN_15(15), MIN_30(30), HOUR_1(60) }

sealed interface LateAction {
    /** Show the Taken late sheet (Move next doses / Keep my times, with Always do this). */
    data object ShowSheet : LateAction
    data object MoveNextDoses : LateAction
    data object KeepTimes : LateAction
    /** As-needed medicines have no due time. */
    data object NotApplicable : LateAction
}

sealed interface TakeCheck {
    data object Allowed : TakeCheck
    /** Gap not met. The user may Wait or Take anyway; taking anyway logs the dose with an Early mark. */
    data class TooSoon(val nextAllowed: LocalDateTime) : TakeCheck
    /** As needed only. No Take button; no override on purpose. */
    data class DailyMaxReached(val nextAllowed: LocalDateTime) : TakeCheck
}

object LateRules {

    /** Minutes late, or null when the dose is on time (at or before the threshold) or taken early. */
    fun lateMinutes(planned: LocalDateTime, taken: LocalDateTime, threshold: LateThreshold): Int? {
        val m = Duration.between(planned, taken).toMinutes()
        return if (m > threshold.minutes) m.toInt() else null
    }

    /** Rule table 1: medicine kind x setting. */
    fun lateAction(frequency: Frequency, setting: LateSetting): LateAction = when (frequency) {
        is Frequency.AsNeeded -> LateAction.NotApplicable
        is Frequency.EveryNHours -> LateAction.ShowSheet // fixed gap: always ask
        is Frequency.FixedTimes -> when (setting) {
            LateSetting.ASK -> LateAction.ShowSheet
            LateSetting.MOVE_NEXT -> LateAction.MoveNextDoses
            LateSetting.KEEP -> LateAction.KeepTimes
        }
    }

    /**
     * Fixed times, "Move next doses": shift only the remaining doses of that day later by the delay.
     * Tomorrow returns to the normal times. Doses already taken are untouched.
     */
    fun moveRemainingToday(dosesToday: List<LocalDateTime>, lateDose: LocalDateTime, takenAt: LocalDateTime): List<LocalDateTime> {
        val delay = Duration.between(lateDose, takenAt)
        if (delay.isNegative || delay.isZero) return dosesToday.filter { it.isAfter(lateDose) }
        return dosesToday.filter { it.isAfter(lateDose) }.map { it.plus(delay) }
    }

    /**
     * Every few hours, "Move": next dose = actual time + N hours and the rest of the cycle follows,
     * keeping the minimum gap guarantee before [nextCycleFirst]. The next cycle starts again at its first dose.
     */
    fun hoursChainAfter(takenAt: LocalDateTime, hours: Int, nextCycleFirst: LocalDateTime): List<LocalDateTime> {
        val out = mutableListOf<LocalDateTime>()
        var t = takenAt.plusHours(hours.toLong())
        while (!t.plusHours(hours.toLong()).isAfter(nextCycleFirst)) {
            out += t
            t = t.plusHours(hours.toLong())
        }
        return out
    }

    /** Real gap in minutes between the last dose and the one just taken, for the "gap is 7 h 20 min" line. */
    fun gapMinutes(previousTaken: LocalDateTime, taken: LocalDateTime): Int = Duration.between(previousTaken, taken).toMinutes().toInt()

    /**
     * A dose becomes Missed when the next dose of the same medicine is due,
     * or at 11:59 PM on its own day, whichever comes first. Until then it stays "late".
     */
    fun missedAt(planned: LocalDateTime, nextPlanned: LocalDateTime?): LocalDateTime {
        val endOfDay = planned.toLocalDate().atTime(23, 59)
        return if (nextPlanned != null && nextPlanned.isBefore(endOfDay)) nextPlanned else endOfDay
    }

    fun isMissed(now: LocalDateTime, planned: LocalDateTime, nextPlanned: LocalDateTime?): Boolean =
        !now.isBefore(missedAt(planned, nextPlanned))

    /**
     * Rule table 4, as-needed. [takenTimes] are all recent actual taken times (any order).
     * Daily maximum is checked first; a day is the local calendar day. The gap rule applies across midnight.
     */
    fun asNeededCheck(now: LocalDateTime, takenTimes: List<LocalDateTime>, minGapMinutes: Int, maxPerDay: Int?): TakeCheck {
        val last = takenTimes.filter { !it.isAfter(now) }.maxOrNull()
        val gapEnds = last?.plusMinutes(minGapMinutes.toLong())
        if (maxPerDay != null) {
            val today = takenTimes.count { it.toLocalDate() == now.toLocalDate() && !it.isAfter(now) }
            if (today >= maxPerDay) {
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val next = if (gapEnds != null && gapEnds.isAfter(midnight)) gapEnds else midnight
                return TakeCheck.DailyMaxReached(next)
            }
        }
        return if (gapEnds != null && now.isBefore(gapEnds)) TakeCheck.TooSoon(gapEnds) else TakeCheck.Allowed
    }

    /** Every few hours: gap = N hours from the last actual taken time. */
    fun hoursCheck(now: LocalDateTime, lastTaken: LocalDateTime?, hours: Int): TakeCheck {
        val gapEnds = lastTaken?.plusHours(hours.toLong()) ?: return TakeCheck.Allowed
        return if (now.isBefore(gapEnds)) TakeCheck.TooSoon(gapEnds) else TakeCheck.Allowed
    }
}
