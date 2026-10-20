package com.pillbox.data

import android.content.Context
import androidx.room.Room
import com.pillbox.domain.BackupCodec
import com.pillbox.domain.BackupResult
import com.pillbox.domain.DayPattern
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.WithFood
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Backup, restore and "Delete all my data" against a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    private lateinit var db: PillBoxDatabase
    private lateinit var repo: PillBoxRepository
    private val day = LocalDate.of(2026, 10, 17)
    private val at = LocalDateTime.of(2026, 10, 17, 9, 41)
    private val sched = Schedule(day, null, Frequency.FixedTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), DayPattern.EveryDay), WithFood.ANY)

    @Before fun open() {
        val ctx = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(ctx, PillBoxDatabase::class.java).allowMainThreadQueries().build()
        repo = PillBoxRepository(db)
    }

    @After fun close() = db.close()

    private suspend fun fill(): Long {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(Medicine(personId = me.id, name = "Amoxicillin 500 \"mg\"", stock = 12.5), sched)
        repo.pause(id, Pause(at.minusDays(3), at.minusDays(2)))
        repo.recordDose(DoseEvent(medicineId = id, planned = at.minusDays(1), taken = at.minusDays(1).plusMinutes(7), status = DoseStatus.TAKEN, lateMinutes = 7))
        repo.recordDose(DoseEvent(medicineId = id, planned = at, status = DoseStatus.SKIPPED, skipReason = "Felt sick"))
        return id
    }

    @Test fun whatIsBackedUpComesBackThroughTheFile() = runBlocking {
        fill()
        val before = repo.exportBackup()
        val file = BackupCodec.encode(before, at)
        val read = (BackupCodec.decode(file) as BackupResult.Ok).data

        repo.deleteEverything()
        assertEquals(0, repo.exportBackup().summary.medicines)
        repo.restoreBackup(read)

        val after = repo.exportBackup()
        assertEquals(before.summary, after.summary)
        assertEquals(1, after.summary.medicines)
        assertEquals(2, after.summary.doses)
        assertEquals(before.persons, after.persons)
        assertEquals(before.medicines, after.medicines)
        assertEquals(before.schedules, after.schedules)
        assertEquals(before.pauses, after.pauses)
        assertEquals(before.doseEvents, after.doseEvents)
    }

    @Test fun restoringReplacesWhatWasThereBefore() = runBlocking {
        fill()
        val saved = repo.exportBackup()
        val me = repo.ensureMe("Me")
        repo.addMedicine(Medicine(personId = me.id, name = "Added after the backup", stock = 3.0), sched)
        assertEquals(2, repo.exportBackup().summary.medicines)
        repo.restoreBackup(saved)
        assertEquals(1, repo.exportBackup().summary.medicines)
    }

    @Test fun deleteEverythingEmptiesAllTables() = runBlocking {
        fill()
        repo.deleteEverything()
        val s = repo.exportBackup().summary
        assertEquals(0, s.people); assertEquals(0, s.medicines); assertEquals(0, s.doses)
        // The app can start again: "Me" is made on the next use.
        assertTrue(repo.ensureMe("Me").isMe)
    }

    @Test fun aBadRowChangesNothing() = runBlocking {
        fill()
        val good = repo.exportBackup()
        val broken = good.copy(medicines = good.medicines.map { it + ("name" to null) })
        val failed = runCatching { repo.restoreBackup(broken) }.isFailure
        assertTrue(failed)
        assertEquals(good.summary, repo.exportBackup().summary)
    }
}
