package com.pillbox.data

import androidx.room.withTransaction
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.LateRules
import com.pillbox.domain.Medicine
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.Person
import com.pillbox.domain.Schedule
import com.pillbox.domain.ScheduleEngine
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

    suspend fun myPersonId(): Long? = db.persons().me()?.id

    /** Whose medicines the Today, Medicines, History and Report screens show. Null (or a removed person) means "me". */
    private val activeId = MutableStateFlow<Long?>(null)
    val activePersonId: StateFlow<Long?> = activeId

    fun selectPerson(id: Long?) { activeId.value = id }

    /** The person on screen now, and again whenever they are edited or the selection changes. */
    fun observeActivePerson(meName: String): Flow<Person> = flow {
        ensureMe(meName)
        emitAll(
            combine(observePeople(), activeId) { people, id -> people.firstOrNull { it.id == id } ?: people.first { it.isMe } }.distinctUntilChanged(),
        )
    }

    /** One-shot version of [observeActivePerson], for saving a new medicine for whoever is on screen. */
    suspend fun activePerson(meName: String): Person {
        val me = ensureMe(meName)
        val id = activeId.value ?: return me
        return db.persons().get(id)?.let(Mappers::toDomain) ?: me
    }

    /** Names of the people other than me, by id, for labeling reminders. Empty when I am the only person. */
    suspend fun otherPeopleNames(): Map<Long, String> = observePeople().first().filter { !it.isMe }.associate { it.id to it.name }

    suspend fun getPerson(id: Long): Person? = db.persons().get(id)?.let(Mappers::toDomain)

    suspend fun addPerson(person: Person): Long = db.withTransaction {
        val next = (db.persons().observeAll().first().maxOfOrNull { it.sortOrder } ?: 0) + 1
        db.persons().insert(Mappers.toEntity(person.copy(id = 0, sortOrder = next)))
    }

    /** Doses of the people whose reminders are off must not ring, so alarms use this list instead of every medicine. */
    suspend fun allActiveRinging(): List<MedicineWithSchedule> {
        val silent = db.persons().silentIds().toSet()
        return allActiveWithSchedules().filter { it.medicine.personId !in silent }
    }

    /** Turns reminders for every person but me on or off at once. */
    suspend fun setRingForOthers(ring: Boolean) {
        observePeople().first().filter { !it.isMe && it.ring != ring }.forEach { updatePerson(it.copy(ring = ring)) }
    }
    suspend fun updatePerson(person: Person) = db.persons().update(Mappers.toEntity(person))

    /** Removing a person removes their medicines, schedules and history (cascade). The UI must warn first. */
    suspend fun removePerson(person: Person) {
        if (person.isMe) return
        db.persons().delete(Mappers.toEntity(person))
        if (activeId.value == person.id) activeId.value = null
    }

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

    /** Adds [amount] to stock. [packSize] (tablets per box) is remembered for next time when given. */
    suspend fun addStock(medicineId: Long, amount: Double, packSize: Int? = null) {
        val m = db.medicines().get(medicineId) ?: return
        db.medicines().update(m.copy(stock = m.stock + amount, packSize = packSize ?: m.packSize))
    }

    /** Stop: reminders end and the medicine moves to Finished. History and stock are kept. */
    suspend fun stop(medicineId: Long, today: LocalDate) {
        val m = db.medicines().get(medicineId) ?: return
        db.medicines().update(m.copy(status = MedicineStatus.STOPPED.name, endedOn = today.toEpochDay()))
    }

    /** Restart a stopped or finished medicine with the same details, starting now. */
    suspend fun restart(medicineId: Long, now: LocalDateTime) = db.withTransaction {
        val m = db.medicines().get(medicineId) ?: return@withTransaction
        val s = db.schedules().forMedicine(medicineId)
        if (s != null) db.schedules().upsert(Mappers.toEntity(medicineId, ScheduleEngine.restarted(Mappers.toDomain(s), now), s.id))
        db.medicines().update(m.copy(status = MedicineStatus.ACTIVE.name, endedOn = null))
    }

    /** Courses whose last day is before [today] move to Finished. Returns how many moved. */
    suspend fun finishEndedCourses(today: LocalDate): Int = db.withTransaction {
        var moved = 0
        for (item in allActiveWithSchedules()) {
            val end = item.schedule?.end ?: continue
            if (end.isBefore(today)) {
                db.medicines().get(item.medicine.id)?.let {
                    db.medicines().update(it.copy(status = MedicineStatus.FINISHED.name, endedOn = end.toEpochDay()))
                    moved++
                }
            }
        }
        moved
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

    /** One medicine with its schedule and pauses; emits again on any change. Null if it was deleted. */
    fun observeMedicine(id: Long): Flow<MedicineWithSchedule?> =
        combine(db.medicines().observe(id), db.schedules().observeForMedicine(id), db.pauses().observeForMedicine(id)) { m, s, p ->
            m?.let { MedicineWithSchedule(Mappers.toDomain(it), s?.let(Mappers::toDomain), p.map(Mappers::toDomain)) }
        }

    /** Stopped and finished medicines together, most recently ended first. */
    fun observeFinished(personId: Long): Flow<List<MedicineWithSchedule>> =
        combine(observeMedicines(personId, MedicineStatus.STOPPED), observeMedicines(personId, MedicineStatus.FINISHED)) { a, b ->
            (a + b).sortedByDescending { it.medicine.endedOn?.toEpochDay() ?: 0L }
        }

    /** Events for one medicine planned in [from, to). */
    fun observeDoseEventsFor(medicineId: Long, from: LocalDateTime, to: LocalDateTime): Flow<List<DoseEvent>> =
        db.doseEvents().observePlannedBetweenFor(medicineId, Mappers.dt(from), Mappers.dt(to)).map { l -> l.map(Mappers::toDomain) }

    /** Every active medicine with its schedule and pauses. Used to (re)arm alarms in M4. */
    suspend fun allActiveWithSchedules(): List<MedicineWithSchedule> {
        val pauses = db.pauses().all().groupBy { it.medicineId }
        val schedules = db.schedules().allActive().associateBy { it.medicineId }
        return db.medicines().allActive().map { e ->
            MedicineWithSchedule(Mappers.toDomain(e), schedules[e.id]?.let(Mappers::toDomain), pauses[e.id].orEmpty().map(Mappers::toDomain))
        }
    }

    // ---- pause ----
    /**
     * Pauses a medicine. A timed pause on an "every N days" schedule restarts its counting on the day
     * reminders come back.
     */
    suspend fun pause(medicineId: Long, pause: Pause) = db.withTransaction {
        db.pauses().insert(Mappers.toEntity(medicineId, pause))
        val until = pause.until
        if (until != null) restartCounting(medicineId, until.toLocalDate())
    }

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
        if (resumed) restartCounting(medicineId, now.toLocalDate())
        resumed
    }

    private suspend fun restartCounting(medicineId: Long, date: LocalDate) {
        val s = db.schedules().forMedicine(medicineId) ?: return
        db.schedules().upsert(Mappers.toEntity(medicineId, ScheduleEngine.resumedOn(Mappers.toDomain(s), date), s.id))
    }

    // ---- dose events ----
    suspend fun recordDose(event: DoseEvent): Long = db.doseEvents().insert(Mappers.toEntity(event.copy(id = 0)))
    suspend fun updateDose(event: DoseEvent) = db.doseEvents().update(Mappers.toEntity(event))

    /** Undo after Take or Skip. */
    suspend fun deleteDose(id: Long) = db.doseEvents().delete(id)

    suspend fun doseEventsFor(medicineId: Long, from: LocalDateTime, to: LocalDateTime): List<DoseEvent> =
        db.doseEvents().plannedBetween(medicineId, Mappers.dt(from), Mappers.dt(to)).map(Mappers::toDomain)

    /** Every medicine's events planned in [from, to). Used by reminders. */
    suspend fun allDoseEventsFor(from: LocalDateTime, to: LocalDateTime): List<DoseEvent> =
        db.doseEvents().allPlannedBetween(Mappers.dt(from), Mappers.dt(to)).map(Mappers::toDomain)

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

    /**
     * Saves "Move next doses": [entries] are added to the medicine's moved doses. Returns the moves it had before,
     * so Undo can put them back.
     */
    suspend fun moveDoses(medicineId: Long, entries: Map<LocalDateTime, LocalDateTime?>): Map<LocalDateTime, LocalDateTime?> = db.withTransaction {
        val e = db.schedules().forMedicine(medicineId) ?: return@withTransaction emptyMap()
        val s = Mappers.toDomain(e)
        db.schedules().upsert(Mappers.toEntity(medicineId, s.copy(shifts = LateRules.mergeShifts(s.shifts, entries, LocalDateTime.now())), e.id))
        s.shifts
    }

    /** Puts back the moves [moveDoses] returned. */
    suspend fun setShifts(medicineId: Long, shifts: Map<LocalDateTime, LocalDateTime?>) = db.withTransaction {
        val e = db.schedules().forMedicine(medicineId) ?: return@withTransaction
        db.schedules().upsert(Mappers.toEntity(medicineId, Mappers.toDomain(e).copy(shifts = shifts), e.id))
    }

    /** Undo of [takeDose]: removes the event and puts back [restore] (less than a full dose if stock ran out). */
    suspend fun undoTake(medicine: Medicine, eventId: Long, restore: Double = medicine.doseAmount) = db.withTransaction {
        db.doseEvents().delete(eventId)
        db.medicines().get(medicine.id)?.let { db.medicines().update(it.copy(stock = it.stock + restore)) }
    }
}
