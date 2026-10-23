package com.pillbox.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert suspend fun insert(p: PersonEntity): Long
    @Update suspend fun update(p: PersonEntity)
    @Delete suspend fun delete(p: PersonEntity)
    @Query("SELECT * FROM person ORDER BY sortOrder, id") fun observeAll(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM person WHERE id = :id") suspend fun get(id: Long): PersonEntity?
    @Query("SELECT * FROM person WHERE isMe = 1 LIMIT 1") suspend fun me(): PersonEntity?
    @Query("SELECT id FROM person WHERE ring = 0") suspend fun silentIds(): List<Long>
}

@Dao
interface MedicineDao {
    @Insert suspend fun insert(m: MedicineEntity): Long
    @Update suspend fun update(m: MedicineEntity)
    @Delete suspend fun delete(m: MedicineEntity)
    @Query("SELECT * FROM medicine WHERE id = :id") suspend fun get(id: Long): MedicineEntity?
    @Query("SELECT * FROM medicine WHERE id = :id") fun observe(id: Long): Flow<MedicineEntity?>
    @Query("SELECT * FROM medicine WHERE personId = :personId AND status = :status ORDER BY name COLLATE NOCASE")
    fun observeForPerson(personId: Long, status: String): Flow<List<MedicineEntity>>
    @Query("SELECT * FROM medicine WHERE status = 'ACTIVE'") suspend fun allActive(): List<MedicineEntity>
    /** Every box with an expiry date, whatever its status: a stopped medicine can still have tablets left. */
    @Query("SELECT * FROM medicine WHERE expiry IS NOT NULL OR packs IS NOT NULL") suspend fun withExpiry(): List<MedicineEntity>
    @Query("SELECT COUNT(*) FROM medicine WHERE personId = :personId AND lower(name) = lower(:name) AND status = 'ACTIVE'")
    suspend fun countActiveWithName(personId: Long, name: String): Int
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: ScheduleEntity): Long
    @Query("SELECT * FROM schedule WHERE medicineId = :medicineId") suspend fun forMedicine(medicineId: Long): ScheduleEntity?
    @Query("SELECT * FROM schedule WHERE medicineId = :medicineId") fun observeForMedicine(medicineId: Long): Flow<ScheduleEntity?>
    @Query("SELECT s.* FROM schedule s JOIN medicine m ON m.id = s.medicineId WHERE m.status = 'ACTIVE'")
    suspend fun allActive(): List<ScheduleEntity>
}

@Dao
interface PauseDao {
    @Insert suspend fun insert(p: PauseEntity): Long
    @Update suspend fun update(p: PauseEntity)
    @Query("SELECT * FROM pause WHERE medicineId = :medicineId ORDER BY fromAt") suspend fun forMedicine(medicineId: Long): List<PauseEntity>
    @Query("SELECT * FROM pause WHERE medicineId = :medicineId ORDER BY fromAt") fun observeForMedicine(medicineId: Long): Flow<List<PauseEntity>>
    @Query("SELECT * FROM pause") suspend fun all(): List<PauseEntity>
}

@Dao
interface DoseEventDao {
    @Insert suspend fun insert(d: DoseEventEntity): Long
    @Update suspend fun update(d: DoseEventEntity)
    @Query("DELETE FROM dose_event WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM dose_event WHERE id = :id") suspend fun get(id: Long): DoseEventEntity?

    /** Events planned in [fromAt, toAt) for the medicines of one person. Use Mappers.dt for the strings. */
    @Query(
        "SELECT d.* FROM dose_event d JOIN medicine m ON m.id = d.medicineId " +
            "WHERE m.personId = :personId AND d.plannedAt >= :fromAt AND d.plannedAt < :toAt ORDER BY d.plannedAt",
    )
    fun observePlannedBetween(personId: Long, fromAt: String, toAt: String): Flow<List<DoseEventEntity>>

    /** As-needed doses (no planned time) taken in [fromAt, toAt) for one person. History and the doctor report count them. */
    @Query(
        "SELECT d.* FROM dose_event d JOIN medicine m ON m.id = d.medicineId " +
            "WHERE m.personId = :personId AND d.plannedAt IS NULL AND d.status = 'TAKEN' AND d.takenAt >= :fromAt AND d.takenAt < :toAt ORDER BY d.takenAt",
    )
    fun observeAsNeededBetween(personId: Long, fromAt: String, toAt: String): Flow<List<DoseEventEntity>>

    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId AND plannedAt >= :fromAt AND plannedAt < :toAt ORDER BY plannedAt")
    suspend fun plannedBetween(medicineId: Long, fromAt: String, toAt: String): List<DoseEventEntity>

    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId AND plannedAt >= :fromAt AND plannedAt < :toAt ORDER BY plannedAt")
    fun observePlannedBetweenFor(medicineId: Long, fromAt: String, toAt: String): Flow<List<DoseEventEntity>>

    @Query("SELECT * FROM dose_event WHERE plannedAt >= :fromAt AND plannedAt < :toAt ORDER BY plannedAt")
    suspend fun allPlannedBetween(fromAt: String, toAt: String): List<DoseEventEntity>

    /** Actual taken times since [sinceAt], newest first. Used by the minimum gap and daily maximum rules. */
    @Query("SELECT takenAt FROM dose_event WHERE medicineId = :medicineId AND status = 'TAKEN' AND takenAt >= :sinceAt ORDER BY takenAt DESC")
    suspend fun takenSince(medicineId: Long, sinceAt: String): List<String>

    /** Taken doses since [sinceAt], newest first, with their ids (as-needed log and its Undo). */
    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId AND status = 'TAKEN' AND takenAt >= :sinceAt ORDER BY takenAt DESC")
    suspend fun takenEventsSince(medicineId: Long, sinceAt: String): List<DoseEventEntity>

    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId ORDER BY COALESCE(takenAt, plannedAt) DESC LIMIT :limit")
    suspend fun latest(medicineId: Long, limit: Int): List<DoseEventEntity>
}

/** Whole-table reads and writes for backup, restore and "Delete all my data". */
@Dao
interface BackupDao {
    @Query("SELECT * FROM person ORDER BY id") suspend fun persons(): List<PersonEntity>
    @Query("SELECT * FROM medicine ORDER BY id") suspend fun medicines(): List<MedicineEntity>
    @Query("SELECT * FROM schedule ORDER BY id") suspend fun schedules(): List<ScheduleEntity>
    @Query("SELECT * FROM pause ORDER BY id") suspend fun pauses(): List<PauseEntity>
    @Query("SELECT * FROM dose_event ORDER BY id") suspend fun doseEvents(): List<DoseEventEntity>

    @Insert suspend fun insertPersons(rows: List<PersonEntity>)
    @Insert suspend fun insertMedicines(rows: List<MedicineEntity>)
    @Insert suspend fun insertSchedules(rows: List<ScheduleEntity>)
    @Insert suspend fun insertPauses(rows: List<PauseEntity>)
    @Insert suspend fun insertDoseEvents(rows: List<DoseEventEntity>)

    @Query("DELETE FROM dose_event") suspend fun clearDoseEvents()
    @Query("DELETE FROM pause") suspend fun clearPauses()
    @Query("DELETE FROM schedule") suspend fun clearSchedules()
    @Query("DELETE FROM medicine") suspend fun clearMedicines()
    @Query("DELETE FROM person") suspend fun clearPersons()
}
