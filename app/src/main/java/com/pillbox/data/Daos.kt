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

    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId AND plannedAt >= :fromAt AND plannedAt < :toAt ORDER BY plannedAt")
    suspend fun plannedBetween(medicineId: Long, fromAt: String, toAt: String): List<DoseEventEntity>

    /** Actual taken times since [sinceAt], newest first. Used by the minimum gap and daily maximum rules. */
    @Query("SELECT takenAt FROM dose_event WHERE medicineId = :medicineId AND status = 'TAKEN' AND takenAt >= :sinceAt ORDER BY takenAt DESC")
    suspend fun takenSince(medicineId: Long, sinceAt: String): List<String>

    @Query("SELECT * FROM dose_event WHERE medicineId = :medicineId ORDER BY COALESCE(takenAt, plannedAt) DESC LIMIT :limit")
    suspend fun latest(medicineId: Long, limit: Int): List<DoseEventEntity>
}
