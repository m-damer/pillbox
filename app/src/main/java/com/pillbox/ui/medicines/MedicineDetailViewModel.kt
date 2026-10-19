package com.pillbox.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.ScheduleEngine
import com.pillbox.domain.StockInfo
import com.pillbox.domain.StockMath
import com.pillbox.domain.WeekSummary
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loaded: Boolean = false,
    /** Null after the medicine was deleted. */
    val item: MedicineWithSchedule? = null,
    val info: StockInfo? = null,
    /** Last 7 days, for an active medicine. */
    val week: WeekSummary? = null,
    /** Doses taken and planned over the whole course, for a finished medicine. */
    val courseTaken: Int = 0,
    val courseTotal: Int = 0,
    val showStop: Boolean = false,
    val showStock: Boolean = false,
    val showPause: Boolean = false,
    val showDelete: Boolean = false,
    /** The pause that is on right now, if any. */
    val pause: Pause? = null,
)

private data class Dialogs(val stop: Boolean = false, val stock: Boolean = false, val pause: Boolean = false, val delete: Boolean = false)

/** What the toast after Add stock says. */
data class StockResult(val name: String, val total: Double, val days: Int?)

@OptIn(ExperimentalCoroutinesApi::class)
class MedicineDetailViewModel(
    private val repo: PillBoxRepository,
    private val id: Long,
    /** Called after anything that changes reminders, so the next alarm is armed again. */
    private val onChanged: suspend () -> Unit,
) : ViewModel() {
    private val dialogs = MutableStateFlow(Dialogs())

    val state: StateFlow<DetailUiState> = repo.observeMedicine(id).flatMapLatest { item ->
        val today = LocalDate.now()
        val active = item?.medicine?.status == MedicineStatus.ACTIVE
        val from: LocalDateTime
        val to: LocalDateTime
        if (item == null || active || item.schedule == null) {
            from = today.minusDays(6).atStartOfDay(); to = today.plusDays(1).atStartOfDay()
        } else {
            from = item.schedule.start.atStartOfDay()
            to = (item.medicine.endedOn ?: item.schedule.end ?: today).plusDays(1).atStartOfDay()
        }
        combine(repo.observeDoseEventsFor(id, from, to), dialogs) { events, d ->
            if (item == null) return@combine DetailUiState(loaded = true)
            val schedule = item.schedule
            val info = StockMath.info(item.medicine, schedule, today)
            if (active) {
                DetailUiState(
                    true, item, info, WeekSummary.build(schedule, item.pauses, events, today),
                    showStop = d.stop, showStock = d.stock, showPause = d.pause, showDelete = d.delete,
                    pause = Pause.activeAt(item.pauses, LocalDateTime.now()),
                )
            } else {
                val taken = events.count { it.status == DoseStatus.TAKEN }
                val total = if (schedule == null) 0 else {
                    val last = item.medicine.endedOn ?: schedule.end ?: today
                    generateSequence(schedule.start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
                        .sumOf { ScheduleEngine.plannedDoses(schedule, it).size }
                }
                DetailUiState(true, item, info, null, taken, total, showStop = d.stop, showStock = d.stock, showDelete = d.delete)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun showStop(on: Boolean) = dialogs.update { it.copy(stop = on) }
    fun showStock(on: Boolean) = dialogs.update { it.copy(stock = on) }
    fun showPause(on: Boolean) = dialogs.update { it.copy(pause = on) }
    fun showDelete(on: Boolean) = dialogs.update { it.copy(delete = on) }

    /** Stop: reminders end, the medicine moves to Finished. [done] gets its name. */
    fun stop(done: (String) -> Unit) {
        val m = state.value.item?.medicine ?: return
        dialogs.update { it.copy(stop = false) }
        viewModelScope.launch {
            repo.stop(m.id, LocalDate.now())
            onChanged()
            done(m.name)
        }
    }

    /** Pause for [days] days, or until resumed when null. [done] gets the name and the day reminders return (null = until resumed). */
    fun pause(days: Int?, done: (String, LocalDate?) -> Unit) {
        val m = state.value.item?.medicine ?: return
        dialogs.update { it.copy(pause = false) }
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val pause = if (days == null) Pause.untilResumed(now) else Pause.forDays(now, days)
            repo.pause(m.id, pause)
            onChanged()
            done(m.name, pause.until?.toLocalDate())
        }
    }

    fun resume(done: (String) -> Unit) {
        val m = state.value.item?.medicine ?: return
        viewModelScope.launch {
            repo.resume(m.id, LocalDateTime.now())
            onChanged()
            done(m.name)
        }
    }

    /** Deletes the medicine with its schedule and history. [done] gets its name. */
    fun delete(done: (String) -> Unit) {
        val m = state.value.item?.medicine ?: return
        dialogs.update { it.copy(delete = false) }
        viewModelScope.launch {
            repo.deleteMedicine(m)
            onChanged()
            done(m.name)
        }
    }

    fun restart(done: (String) -> Unit) {
        val m = state.value.item?.medicine ?: return
        viewModelScope.launch {
            repo.restart(m.id, LocalDateTime.now())
            onChanged()
            done(m.name)
        }
    }

    fun addStock(boxes: Int, perBox: Int, done: (StockResult) -> Unit) {
        val item = state.value.item ?: return
        dialogs.update { it.copy(stock = false) }
        viewModelScope.launch {
            val amount = boxes.toLong() * perBox
            repo.addStock(item.medicine.id, amount.toDouble(), perBox)
            val total = item.medicine.stock + amount
            done(StockResult(item.medicine.name, total, StockMath.daysFor(total, item.medicine, item.schedule)))
        }
    }
}
