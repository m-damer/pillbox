package com.pillbox.data

import androidx.room.withTransaction
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Medicine
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.Person
import com.pillbox.domain.Schedule
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class MedicineWithSchedule(val medicine: Medicine, val schedule: Schedule?, val pauses: List<Pause>)

/** The only class the UI and the alarm code talk to. Domain objects in, domain objects out. */
class PillBoxRepository(private val db: PillBoxDatabase) {

    // ---- people ----
    fun observePeople(): Flow<List<Person>> = db.persons().observeAll().map { l -> l.map(Mappers::toDomain) }

    /** The "Me" profile always exists after the first call. */
    suspend fun ensureMe(name: String): Person = db.withTransaction {
        db.persons().me()?.let { return@withTransaction Mappers.toDomain(it) }
        val me = Person(name = name, isMe = true)
        me.copy(id = db.persons().insert(Mappers.toEntity(me)))
    }

    suspend fun addPerson(person: Person): Long = db.persons().insert(Mappers.toEntity(person.copy(id = 0)))
    suspend fun updatePerson(person: Person) = db.persons().update(Mappers.toEntity(person))

    /** Removing a person removes their medicines, schedules and history (cascade). The UI must warn first. */
    suspend fun removePerson(person: Person) { if (!person.isMe) db.persons().delete(Mappers.toEntity(person)) }

    // ---- medicines ----
    suspend fun addMedicine(medicine: Medicine, schedule: Schedule): Long = db.withTransaction {
        val id = db.medicines().insert(Mappers.toEntity(medicine.copy(id = 0)))
        db.schedules().upsert(Mappers.toEntity(id, schedule))
        id
    }

    suspend fun isDuplicateName(personId: Long, name: String): Boolean = db.medicines().countActiveWithName(personId, name.trim()) > 0

    suspend fun updateMedicine(medicine: Medicine, schedule: Schedule) = db.withTransaction {
        db.medicines().update(Mappers.toEntity(medicine))
        val existing = db.schedules().forMedicine(medicine.id)
        db.schedules().upsert(Mappers.toEntity(medicine.id, schedule, existing?.id ?: 0))
    }

    /** Deleting removes the medicine and its whole history. The UI offers Stop first. */
    suspend fun deleteMedicine(medicine: Medicine) = db.medicines().delete(Mappers.toEntity(medicine))

    suspend fun setStatus(medicineId: Long, status: MedicineStatus) {
        val m = db.medicines().get(medicineId) ?: return
        db.medicines().update(m.copy(status = status.name))
    }

    suspend fun addStock(medicineId: Long, amount: Double) {
        val m = db.medicines().get(medicineId) ?: return
        db.medicines().update(m.copy(stock = m.stock + amount))
    }

    suspend fun getMedicine(id: Long): Medicine? = db.medicines().get(id)?.let(Mappers::toDomain)
    suspend fun getSchedule(medicineId: Long): Schedule? = db.schedules().forMedicine(medicineId)?.let(Mappers::toDomain)

    fun observeMedicines(personId: Long, status: MedicineStatus = MedicineStatus.ACTIVE): Flow<List<MedicineWithSchedule>> =
        db.medicines().observeForPerson(personId, status.name).map { list ->
            list.map { e ->
                MedicineWithSchedule(
                    Mappers.toDomain(e),
                    db.schedules().forMedicine(e.id)?.let(Mappers::toDomain),
                    db.pauses().forMedicine(e.id).map(Mappers::toDomain),
                )
            }
        }

    /** Every active medicine with its schedule and pauses. Used to (re)arm alarms in M4. */
    suspend fun allActiveWithSchedules(): List<MedicineWithSchedule> {
        val pauses = db.pauses().all().groupBy { it.medicineId }
        val schedules = db.schedules().allActive().associateBy { it.medicineId }
        return db.medicines().allActive().map { e ->
            MedicineWithSchedule(Mappers.toDomain(e), schedules[e.id]?.let(Mappers::toDomain), pauses[e.id].orEmpty().map(Mappers::toDomain))
        }
    }

    // ---- pause ----
    suspend fun pause(medicineId: Long, pause: Pause) { db.pauses().insert(Mappers.toEntity(medicineId, pause)) }

    /** Ends every open pause now. Returns true if something was resumed. */
    suspend fun resume(medicineId: Long, now: LocalDateTime): Boolean = db.withTransaction {
        var resumed = false
        for (p in db.pauses().forMedicine(medicineId)) {
            val domain = Mappers.toDomain(p)
            if (domain.covers(now)) {
                db.pauses().update(p.copy(untilAt = Mappers.dt(now)))
                resumed = true
            }
        }
        resumed
    }

    // ---- dose events ----
    suspend fun recordDose(event: DoseEvent): Long = db.doseEvents().insert(Mappers.toEntity(event.copy(id = 0)))
    suspend fun updateDose(event: DoseEvent) = db.doseEvents().update(Mappers.toEntity(event))

    /** Undo after Take or Skip. */
    suspend fun deleteDose(id: Long) = db.doseEvents().delete(id)

    suspend fun doseEventsFor(medicineId: Long, from: LocalDateTime, to: LocalDateTime): List<DoseEvent> =
        db.doseEvents().plannedBetween(medicineId, Mappers.dt(from), Mappers.dt(to)).map(Mappers::toDomain)

    fun observeDoseEvents(personId: Long, from: LocalDateTime, to: LocalDateTime): Flow<List<DoseEvent>> =
        db.doseEvents().observePlannedBetween(personId, Mappers.dt(from), Mappers.dt(to)).map { l -> l.map(Mappers::toDomain) }

    /** Taken times for the as-needed and every-few-hours rules. */
    suspend fun takenTimesSince(medicineId: Long, since: LocalDateTime): List<LocalDateTime> =
        db.doseEvents().takenSince(medicineId, Mappers.dt(since)).map(Mappers::dt)

    /** Marks a dose taken and takes it out of stock in one transaction. */
    suspend fun takeDose(medicine: Medicine, event: DoseEvent): Long = db.withTransaction {
        val id = db.doseEvents().insert(Mappers.toEntity(event.copy(id = 0, medicineId = medicine.id, status = DoseStatus.TAKEN)))
        db.medicines().get(medicine.id)?.let { db.medicines().update(it.copy(stock = (it.stock - medicine.doseAmount).coerceAtLeast(0.0))) }
        id
    }

    /** Undo of [takeDose]: removes the event and puts back [restore] (less than a full dose if stock ran out). */
    suspend fun undoTake(medicine: Medicine, eventId: Long, restore: Double = medicine.doseAmount) = db.withTransaction {
        db.doseEvents().delete(eventId)
        db.medicines().get(medicine.id)?.let { db.medicines().update(it.copy(stock = it.stock + restore)) }
    }
}
