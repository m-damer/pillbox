package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The one clock the app reads. Normally it is the phone's own time zone. With "Keep my home time" on it is the home zone
 * instead, so a trip changes nothing: doses stay at the same home clock times, alarms ring at the same moments, and Today
 * shows the home day. Every "now", "today" and time-zone lookup in the app goes through here.
 */
object AppClock {
    /** The home zone while "Keep my home time" is on; null follows the phone. */
    @Volatile var home: ZoneId? = null

    val keepsHome get() = home != null
    fun zone(): ZoneId = home ?: ZoneId.systemDefault()
    fun now(): LocalDateTime = LocalDateTime.now(zone())
    fun today(): LocalDate = LocalDate.now(zone())
    fun time(): LocalTime = LocalTime.now(zone())

    /** The zone id to keep as home when the switch is turned on: the one already kept, or the phone's zone now. */
    fun homeToKeep(current: String?): String = current ?: ZoneId.systemDefault().id

    /** A saved id back to a zone; a damaged or unknown id means "follow the phone". */
    fun parse(id: String?): ZoneId? = id?.let { runCatching { ZoneId.of(it) }.getOrNull() }
}
