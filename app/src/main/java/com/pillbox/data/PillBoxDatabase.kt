package com.pillbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 3 (v2 added schedule.startMinute, v3 added medicine.endedOn). Every schema change needs a migration and a bumped version; schemas are exported to
 * app/schemas so migrations can be checked. Backup files (M9) carry their own schema version.
 */
@Database(
    entities = [PersonEntity::class, MedicineEntity::class, ScheduleEntity::class, PauseEntity::class, DoseEventEntity::class],
    version = 3,
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

        /** Version 3: the day a medicine finished or was stopped, for the Finished tab. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicine ADD COLUMN endedOn INTEGER")
            }
        }

        /** Version 2: the first dose time of a schedule, so the start day only has doses from then on. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedule ADD COLUMN startMinute INTEGER")
            }
        }

        fun create(context: Context): PillBoxDatabase =
            Room.databaseBuilder(context.applicationContext, PillBoxDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
