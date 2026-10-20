package com.pillbox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities use only primitives and Strings, so no TypeConverters are needed.
 * Dates are epoch days (Long); date-times are fixed-format local ISO strings
 * ("yyyy-MM-dd'T'HH:mm:ss"), which sort correctly as text. See Mappers.kt.
 * Settings live in DataStore (M8), not here.
 */

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isMe: Boolean = false,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "OTHER") val relation: String = "OTHER",
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,
    @ColumnInfo(defaultValue = "1") val ring: Boolean = true,
    val birthYear: Int? = null,
)

@Entity(
    tableName = "medicine",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId")],
)
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val name: String,
    val strength: String?,
    val unit: String,
    val doseAmount: Double,
    val stock: Double,
    val packSize: Int?,
    val status: String,
    /** Epoch day the course finished or was stopped (null while active). */
    val endedOn: Long? = null,
)

@Entity(
    tableName = "schedule",
    foreignKeys = [ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicineId", unique = true)],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val startDate: Long,
    val endDate: Long?,
    /** FIXED, HOURS or AS_NEEDED. */
    val kind: String,
    /** FIXED: comma separated minutes of the day, e.g. "480,1200". */
    val timesCsv: String?,
    /** FIXED: EVERY_DAY, DAYS_OF_WEEK or EVERY_N_DAYS. */
    val dayPattern: String?,
    /** DAYS_OF_WEEK: bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int?,
    val everyNDays: Int?,
    val anchorDate: Long?,
    /** HOURS: first dose as minute of the day, and N hours. */
    val firstMinute: Int?,
    val hours: Int?,
    /** AS_NEEDED. */
    val minGapMinutes: Int?,
    val maxPerDay: Int?,
    val withFood: String,
    /** Minute of the day of the first dose on the start date (null = whole day). */
    val startMinute: Int? = null,
)

@Entity(
    tableName = "pause",
    foreignKeys = [ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicineId")],
)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val fromAt: String,
    /** null = until I resume. */
    val untilAt: String?,
)

@Entity(
    tableName = "dose_event",
    foreignKeys = [ForeignKey(entity = MedicineEntity::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicineId", "plannedAt"), Index("takenAt")],
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    /** null for as-needed doses. */
    val plannedAt: String?,
    val takenAt: String?,
    val status: String,
    val skipReason: String?,
    val early: Boolean,
    val lateMinutes: Int?,
)
