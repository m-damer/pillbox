package com.pillbox.data

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.pillbox.domain.DayPattern
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.WithFood
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PillBoxRepositoryTest {
    private lateinit var db: PillBoxDatabase
    private lateinit var repo: PillBoxRepository
    private val day = LocalDate.of(2026, 10, 17)
    private val sched = Schedule(day, null, Frequency.FixedTimes(listOf(LocalTime.of(8, 0)), DayPattern.EveryDay), WithFood.ANY)

    @Before fun open() {
        val ctx = RuntimeEnvironment.getApplication() as Context
        db = Room.inMemoryDatabaseBuilder(ctx, PillBoxDatabase::class.java).allowMainThreadQueries().build()
        repo = PillBoxRepository(db)
    }

    @After fun close() = db.close()

    private fun med(personId: Long, name: String = "Metformin", stock: Double = 10.0) =
        Medicine(personId = personId, name = name, stock = stock)

    @Test fun meExistsOnce() = runBlocking {
        val a = repo.ensureMe("Me")
        val b = repo.ensureMe("Other name")
        assertEquals(a.id, b.id)
        assertTrue(a.isMe)
    }

    @Test fun addAndReadMedicine() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        assertEquals("Metformin", repo.getMedicine(id)?.name)
        assertEquals(sched, repo.getSchedule(id))
        assertEquals(1, repo.allActiveWithSchedules().size)
    }

    @Test fun duplicateNameIgnoresCase() = runBlocking {
        val me = repo.ensureMe("Me")
        repo.addMedicine(med(me.id), sched)
        assertTrue(repo.isDuplicateName(me.id, " metformin "))
        assertFalse(repo.isDuplicateName(me.id, "Aspirin"))
    }

    @Test fun stoppedMedicineLeavesActiveList() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        repo.setStatus(id, MedicineStatus.STOPPED)
        assertTrue(repo.allActiveWithSchedules().isEmpty())
    }

    @Test fun takeAndUndoMoveStock() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 10.0), sched)
        val m = repo.getMedicine(id)!!
        val at = LocalDateTime.of(2026, 10, 17, 8, 5)
        val eventId = repo.takeDose(m, DoseEvent(planned = at.withMinute(0), taken = at, status = DoseStatus.TAKEN))
        assertEquals(9.0, repo.getMedicine(id)!!.stock, 0.0)
        assertEquals(listOf(at), repo.takenTimesSince(id, at.minusHours(1)))
        repo.undoTake(m, eventId)
        assertEquals(10.0, repo.getMedicine(id)!!.stock, 0.0)
        assertTrue(repo.takenTimesSince(id, at.minusHours(1)).isEmpty())
    }

    @Test fun stockNeverGoesNegative() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 0.0), sched)
        repo.takeDose(repo.getMedicine(id)!!, DoseEvent(planned = null, taken = day.atTime(8, 0), status = DoseStatus.TAKEN))
        assertEquals(0.0, repo.getMedicine(id)!!.stock, 0.0)
    }

    @Test fun pauseAndResume() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        repo.pause(id, Pause(day.atTime(9, 0), null))
        val now = day.atTime(12, 0)
        assertTrue(repo.resume(id, now))
        assertFalse(repo.resume(id, now.plusHours(1)))
        assertEquals(now, repo.allActiveWithSchedules().single().pauses.single().until)
    }

    @Test fun deletingMedicineRemovesEverything() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        val m = repo.getMedicine(id)!!
        repo.takeDose(m, DoseEvent(planned = day.atTime(8, 0), taken = day.atTime(8, 1), status = DoseStatus.TAKEN))
        repo.deleteMedicine(m)
        assertNull(repo.getMedicine(id))
        assertNull(repo.getSchedule(id))
        assertTrue(repo.takenTimesSince(id, day.atStartOfDay()).isEmpty())
    }

    @Test fun updateKeepsOneSchedule() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        val newSched = sched.copy(frequency = Frequency.EveryNHours(LocalTime.of(6, 0), 6))
        repo.updateMedicine(repo.getMedicine(id)!!.copy(name = "Metformin 500"), newSched)
        assertEquals(newSched, repo.getSchedule(id))
        assertNotNull(repo.getMedicine(id))
        assertEquals(1, db.schedules().allActive().size)
    }
}
