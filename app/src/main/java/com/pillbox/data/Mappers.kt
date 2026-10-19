package com.pillbox.data

import com.pillbox.domain.DayPattern
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.Person
import com.pillbox.domain.Schedule
import com.pillbox.domain.WithFood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Entity <-> domain mapping. Pure functions, unit-tested without Room. */
object Mappers {
    /** Fixed width, so stored strings sort correctly as text. */
    private val DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun dt(value: LocalDateTime): String = value.format(DT)
    fun dt(value: String): LocalDateTime = LocalDateTime.parse(value, DT)
    private fun minutes(t: LocalTime) = t.hour * 60 + t.minute
    private fun time(minutes: Int) = LocalTime.of(minutes / 60, minutes % 60)

    // ---- person ----
    fun toEntity(p: Person) = PersonEntity(p.id, p.name, p.isMe, p.sortOrder)
    fun toDomain(e: PersonEntity) = Person(e.id, e.name, e.isMe, e.sortOrder)

    // ---- medicine ----
    fun toEntity(m: Medicine) = MedicineEntity(m.id, m.personId, m.name, m.strength, m.unit.name, m.doseAmount, m.stock, m.packSize, m.status.name)
    fun toDomain(e: MedicineEntity) =
        Medicine(e.id, e.personId, e.name, e.strength, DoseUnit.valueOf(e.unit), e.doseAmount, e.stock, e.packSize, MedicineStatus.valueOf(e.status))

    // ---- schedule ----
    fun toEntity(medicineId: Long, s: Schedule, id: Long = 0): ScheduleEntity {
        val base = ScheduleEntity(
            id = id, medicineId = medicineId, startDate = s.start.toEpochDay(), endDate = s.end?.toEpochDay(),
            kind = "", timesCsv = null, dayPattern = null, daysMask = null, everyNDays = null, anchorDate = null,
            firstMinute = null, hours = null, minGapMinutes = null, maxPerDay = null, withFood = s.withFood.name,
            startMinute = s.startTime?.let { minutes(it) },
        )
        fun withDays(b: ScheduleEntity, d: DayPattern) = when (d) {
            DayPattern.EveryDay -> b.copy(dayPattern = "EVERY_DAY")
            is DayPattern.DaysOfWeek -> b.copy(dayPattern = "DAYS_OF_WEEK", daysMask = d.days.fold(0) { m, day -> m or (1 shl (day.value - 1)) })
            is DayPattern.EveryNDays -> b.copy(dayPattern = "EVERY_N_DAYS", everyNDays = d.n, anchorDate = d.anchor.toEpochDay())
        }
        return when (val f = s.frequency) {
            is Frequency.FixedTimes ->
                withDays(base.copy(kind = "FIXED", timesCsv = f.times.sorted().joinToString(",") { minutes(it).toString() }), f.days)
            is Frequency.EveryNHours -> withDays(base.copy(kind = "HOURS", firstMinute = minutes(f.first), hours = f.hours), f.days)
            is Frequency.AsNeeded -> base.copy(kind = "AS_NEEDED", minGapMinutes = f.minGapMinutes, maxPerDay = f.maxPerDay)
        }
    }

    fun toDomain(e: ScheduleEntity): Schedule {
        val days: DayPattern = when (e.dayPattern) {
            "DAYS_OF_WEEK" -> DayPattern.DaysOfWeek(DayOfWeek.values().filter { (e.daysMask!! shr (it.value - 1)) and 1 == 1 }.toSet())
            "EVERY_N_DAYS" -> DayPattern.EveryNDays(e.everyNDays!!, LocalDate.ofEpochDay(e.anchorDate!!))
            else -> DayPattern.EveryDay
        }
        val frequency: Frequency = when (e.kind) {
            "FIXED" -> Frequency.FixedTimes(e.timesCsv!!.split(",").map { time(it.trim().toInt()) }, days)
            "HOURS" -> Frequency.EveryNHours(time(e.firstMinute!!), e.hours!!, days)
            "AS_NEEDED" -> Frequency.AsNeeded(e.minGapMinutes ?: (6 * 60), e.maxPerDay)
            else -> error("Unknown schedule kind ${e.kind}")
        }
        return Schedule(LocalDate.ofEpochDay(e.startDate), e.endDate?.let { LocalDate.ofEpochDay(it) }, frequency, WithFood.valueOf(e.withFood), e.startMinute?.let { time(it) })
    }

    // ---- pause ----
    fun toEntity(medicineId: Long, p: Pause, id: Long = 0) = PauseEntity(id, medicineId, dt(p.from), p.until?.let { dt(it) })
    fun toDomain(e: PauseEntity) = Pause(dt(e.fromAt), e.untilAt?.let { dt(it) })

    // ---- dose event ----
    fun toEntity(d: DoseEvent) = DoseEventEntity(
        d.id, d.medicineId, d.planned?.let { dt(it) }, d.taken?.let { dt(it) }, d.status.name, d.skipReason, d.early, d.lateMinutes,
    )

    fun toDomain(e: DoseEventEntity) = DoseEvent(
        e.id, e.medicineId, e.plannedAt?.let { dt(it) }, e.takenAt?.let { dt(it) }, DoseStatus.valueOf(e.status), e.skipReason, e.early, e.lateMinutes,
    )
}
