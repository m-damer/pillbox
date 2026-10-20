package com.pillbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 5 (v5 added schedule.shifts, v2 added schedule.startMinute, v3 added medicine.endedOn, v4 added person relation, color, ring and birth year). Every schema change needs a migration and a bumped version; schemas are exported to
 * app/schemas so migrations can be checked. Backup files (M9) carry their own schema version.
 */
@Database(
    entities = [PersonEntity::class, MedicineEntity::class, ScheduleEntity::class, PauseEntity::class, DoseEventEntity::class],
    version = 5,
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

        /** Version 5: doses moved by "Move next doses" (Taken late). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedule ADD COLUMN shifts TEXT")
            }
        }

        /** Version 4: who a person is, their avatar color, whether their doses ring, and an optional birth year (People, M7). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE person ADD COLUMN relation TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE person ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE person ADD COLUMN ring INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE person ADD COLUMN birthYear INTEGER")
                db.execSQL("UPDATE person SET relation = 'ME' WHERE isMe = 1")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
