package com.pillbox.data

import com.pillbox.domain.BackupData
import com.pillbox.domain.Row

/** Database rows to and from backup rows. A missing field takes its default; a field of the wrong kind throws, before anything is changed. */
internal object BackupMapper {
    class Entities(
        val persons: List<PersonEntity>, val medicines: List<MedicineEntity>, val schedules: List<ScheduleEntity>,
        val pauses: List<PauseEntity>, val doseEvents: List<DoseEventEntity>,
    )

    fun toData(e: Entities) = BackupData(
        persons = e.persons.map { mapOf("id" to it.id, "name" to it.name, "isMe" to it.isMe, "sortOrder" to it.sortOrder, "relation" to it.relation, "colorIndex" to it.colorIndex, "ring" to it.ring, "birthYear" to it.birthYear) },
        medicines = e.medicines.map {
            mapOf("id" to it.id, "personId" to it.personId, "name" to it.name, "strength" to it.strength, "unit" to it.unit, "doseAmount" to it.doseAmount,
                "stock" to it.stock, "packSize" to it.packSize, "status" to it.status, "endedOn" to it.endedOn, "expiry" to it.expiry)
        },
        schedules = e.schedules.map {
            mapOf("id" to it.id, "medicineId" to it.medicineId, "startDate" to it.startDate, "endDate" to it.endDate, "kind" to it.kind, "timesCsv" to it.timesCsv,
                "dayPattern" to it.dayPattern, "daysMask" to it.daysMask, "everyNDays" to it.everyNDays, "anchorDate" to it.anchorDate, "firstMinute" to it.firstMinute,
                "hours" to it.hours, "minGapMinutes" to it.minGapMinutes, "maxPerDay" to it.maxPerDay, "withFood" to it.withFood, "startMinute" to it.startMinute, "shifts" to it.shifts)
        },
        pauses = e.pauses.map { mapOf("id" to it.id, "medicineId" to it.medicineId, "fromAt" to it.fromAt, "untilAt" to it.untilAt) },
        doseEvents = e.doseEvents.map {
            mapOf("id" to it.id, "medicineId" to it.medicineId, "plannedAt" to it.plannedAt, "takenAt" to it.takenAt, "status" to it.status, "skipReason" to it.skipReason,
                "early" to it.early, "lateMinutes" to it.lateMinutes, "loggedLater" to it.loggedLater)
        },
    )

    fun toEntities(d: BackupData) = Entities(
        persons = d.persons.map {
            PersonEntity(it.long("id"), it.str("name"), it.bool("isMe", false), it.int("sortOrder", 0), it.str("relation", "OTHER"), it.int("colorIndex", 0), it.bool("ring", true), it.intOrNull("birthYear"))
        },
        medicines = d.medicines.map {
            MedicineEntity(it.long("id"), it.long("personId"), it.str("name"), it.strOrNull("strength"), it.str("unit"), it.dbl("doseAmount"), it.dbl("stock"), it.intOrNull("packSize"), it.str("status"), it.longOrNull("endedOn"), it.strOrNull("expiry"))
        },
        schedules = d.schedules.map {
            ScheduleEntity(
                it.long("id"), it.long("medicineId"), it.long("startDate"), it.longOrNull("endDate"), it.str("kind"), it.strOrNull("timesCsv"), it.strOrNull("dayPattern"),
                it.intOrNull("daysMask"), it.intOrNull("everyNDays"), it.longOrNull("anchorDate"), it.intOrNull("firstMinute"), it.intOrNull("hours"),
                it.intOrNull("minGapMinutes"), it.intOrNull("maxPerDay"), it.str("withFood"), it.intOrNull("startMinute"), it.strOrNull("shifts"),
            )
        },
        pauses = d.pauses.map { PauseEntity(it.long("id"), it.long("medicineId"), it.str("fromAt"), it.strOrNull("untilAt")) },
        doseEvents = d.doseEvents.map {
            DoseEventEntity(it.long("id"), it.long("medicineId"), it.strOrNull("plannedAt"), it.strOrNull("takenAt"), it.str("status"), it.strOrNull("skipReason"), it.bool("early", false), it.intOrNull("lateMinutes"), it.bool("loggedLater", false))
        },
    )

    private fun Row.long(k: String) = (this[k] as Number).toLong()
    private fun Row.longOrNull(k: String) = (this[k] as Number?)?.toLong()
    private fun Row.int(k: String, d: Int) = (this[k] as Number?)?.toInt() ?: d
    private fun Row.intOrNull(k: String) = (this[k] as Number?)?.toInt()
    private fun Row.dbl(k: String) = (this[k] as Number).toDouble()
    private fun Row.str(k: String) = this[k] as String
    private fun Row.str(k: String, d: String) = this[k] as String? ?: d
    private fun Row.strOrNull(k: String) = this[k] as String?
    private fun Row.bool(k: String, d: Boolean) = this[k] as Boolean? ?: d
}
