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

    private val everyThreeDays = Schedule(day, null, Frequency.FixedTimes(listOf(LocalTime.of(8, 0)), DayPattern.EveryNDays(3, day)), WithFood.ANY)

    private suspend fun anchorOf(id: Long) =
        ((repo.getSchedule(id)!!.frequency as Frequency.FixedTimes).days as DayPattern.EveryNDays).anchor

    @Test fun timedPauseRestartsEveryNDaysCountingOnTheResumeDay() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), everyThreeDays)
        repo.pause(id, Pause.forDays(day.atTime(12, 0), 5))
        assertEquals(day.plusDays(5), anchorOf(id))
    }

    @Test fun pauseUntilResumedKeepsAnchorUntilResume() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), everyThreeDays)
        repo.pause(id, Pause.untilResumed(day.atTime(12, 0)))
        assertEquals(day, anchorOf(id))
        assertTrue(repo.resume(id, day.plusDays(9).atTime(10, 0)))
        assertEquals(day.plusDays(9), anchorOf(id))
    }

    @Test fun resumeNowBeforeTheEndDateMovesTheAnchorBack() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), everyThreeDays)
        repo.pause(id, Pause.forDays(day.atTime(12, 0), 5))
        repo.resume(id, day.plusDays(1).atTime(8, 0))
        assertEquals(day.plusDays(1), anchorOf(id))
    }

    @Test fun deletingMedicineAlsoRemovesItsPauses() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched)
        repo.pause(id, Pause.untilResumed(day.atTime(12, 0)))
        repo.deleteMedicine(repo.getMedicine(id)!!)
        assertTrue(repo.allActiveWithSchedules().isEmpty())
        assertTrue(db.pauses().all().isEmpty())
    }

    @Test fun savingAnEditKeepsHistoryPausesAndStartDate() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 7.5), sched)
        val existing = repo.getMedicine(id)!!
        repo.takeDose(existing, DoseEvent(planned = day.atTime(8, 0), taken = day.atTime(8, 1), status = DoseStatus.TAKEN))
        repo.pause(id, Pause.untilResumed(day.atTime(12, 0)))

        val fresh = repo.getMedicine(id)!!
        val form = com.pillbox.domain.AddMedicineForm.fromExisting(fresh, repo.getSchedule(id)!!)
            .copy(name = "Metformin XR", firstDose = LocalTime.of(9, 0))
        repo.updateMedicine(form.updated(fresh), form.schedule(day.plusDays(3)))

        val after = repo.allActiveWithSchedules().single()
        assertEquals("Metformin XR", after.medicine.name)
        assertEquals(day, after.schedule!!.start)
        assertEquals(1, after.pauses.size)
        assertEquals(1, repo.takenTimesSince(id, day.atStartOfDay()).size)
        assertEquals(fresh.stock, after.medicine.stock, 0.0001)
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

    @Test fun stopThenRestart() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id), sched.copy(end = day.plusDays(6)))
        repo.stop(id, day.plusDays(2))
        val stopped = repo.getMedicine(id)!!
        assertEquals(MedicineStatus.STOPPED, stopped.status)
        assertEquals(day.plusDays(2), stopped.endedOn)
        assertTrue(repo.allActiveWithSchedules().isEmpty())

        val now = LocalDateTime.of(2026, 10, 25, 9, 30)
        repo.restart(id, now)
        val back = repo.getMedicine(id)!!
        assertEquals(MedicineStatus.ACTIVE, back.status)
        assertNull(back.endedOn)
        val s = repo.getSchedule(id)!!
        assertEquals(now.toLocalDate(), s.start)
        assertEquals(now.toLocalDate().plusDays(6), s.end)
        assertEquals(LocalTime.of(9, 30), s.startTime)
    }

    @Test fun endedCoursesMoveToFinished() = runBlocking {
        val me = repo.ensureMe("Me")
        val ended = repo.addMedicine(med(me.id, "Amoxicillin"), sched.copy(end = day.plusDays(1)))
        val ongoing = repo.addMedicine(med(me.id, "Metformin"), sched)
        assertEquals(1, repo.finishEndedCourses(day.plusDays(2)))
        assertEquals(MedicineStatus.FINISHED, repo.getMedicine(ended)!!.status)
        assertEquals(day.plusDays(1), repo.getMedicine(ended)!!.endedOn)
        assertEquals(MedicineStatus.ACTIVE, repo.getMedicine(ongoing)!!.status)
        assertEquals(0, repo.finishEndedCourses(day.plusDays(2)))
    }

    @Test fun addStockRemembersBoxSize() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 8.0), sched)
        repo.addStock(id, 60.0, packSize = 60)
        val m = repo.getMedicine(id)!!
        assertEquals(68.0, m.stock, 0.0)
        assertEquals(60, m.packSize)
    }

    @Test fun addStockWithoutDateTracksNoBoxes() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 8.0), sched)
        repo.addStock(id, 60.0, packSize = 60)
        assertTrue(repo.getMedicine(id)!!.packs.isEmpty())
    }

    @Test fun boxesFollowTheDosesAndTheUsersChoice() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 2.0).copy(expiry = java.time.YearMonth.of(2026, 12)), sched)
        repo.addStock(id, 30.0, packSize = 30, expiry = java.time.YearMonth.of(2027, 8))
        var m = repo.getMedicine(id)!!
        assertEquals(2, m.packs.size)
        assertEquals(java.time.YearMonth.of(2026, 12), m.expiry)
        var changed: Int? = null
        repo.onPackChange = { _, p -> changed = p.id }
        repo.takeDose(m, com.pillbox.domain.DoseEvent(planned = day.atTime(8, 0)))
        assertNull(changed)
        m = repo.getMedicine(id)!!
        repo.takeDose(m, com.pillbox.domain.DoseEvent(planned = day.atTime(20, 0)))
        assertEquals(2, changed)
        assertEquals(java.time.YearMonth.of(2027, 8), repo.getMedicine(id)!!.expiry)
        repo.usePack(id, 1)
        assertEquals(java.time.YearMonth.of(2027, 8), repo.getMedicine(id)!!.expiry)
    }

    @Test fun userCanPickTheBoxInUse() = runBlocking {
        val me = repo.ensureMe("Me")
        val id = repo.addMedicine(med(me.id, stock = 10.0).copy(expiry = java.time.YearMonth.of(2027, 8)), sched)
        repo.addStock(id, 30.0, packSize = 30, expiry = java.time.YearMonth.of(2026, 12))
        assertEquals(java.time.YearMonth.of(2027, 8), repo.getMedicine(id)!!.expiry)
        repo.usePack(id, 2)
        assertEquals(java.time.YearMonth.of(2026, 12), repo.getMedicine(id)!!.expiry)
        repo.setPackExpiry(id, 2, java.time.YearMonth.of(2026, 10))
        assertEquals(java.time.YearMonth.of(2026, 10), repo.getMedicine(id)!!.expiry)
        assertEquals(1, repo.medicinesWithExpiry().size)
    }
}
