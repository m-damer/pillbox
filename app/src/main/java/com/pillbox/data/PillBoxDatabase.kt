package com.pillbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1. Every schema change needs a migration and a bumped version; schemas are exported to
 * app/schemas so migrations can be checked. Backup files (M9) carry their own schema version.
 */
@Database(
    entities = [PersonEntity::class, MedicineEntity::class, ScheduleEntity::class, PauseEntity::class, DoseEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PillBoxDatabase : RoomDatabase() {
    abstract fun persons(): PersonDao
    abstract fun medicines(): MedicineDao
    abstract fun schedules(): ScheduleDao
    abstract fun pauses(): PauseDao
    abstract fun doseEvents(): DoseEventDao

    companion object {
        const val FILE_NAME = "pillbox.db"

        fun create(context: Context): PillBoxDatabase =
            Room.databaseBuilder(context.applicationContext, PillBoxDatabase::class.java, FILE_NAME).build()
    }
}
