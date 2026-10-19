package com.pillbox.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.AllPaused
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.LateRules
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.StockLevel
import com.pillbox.domain.StockMath
import com.pillbox.ui.medicines.StockResult
import com.pillbox.domain.RowStatus
import com.pillbox.domain.TakeCheck
import com.pillbox.domain.TodayBuilder
import com.pillbox.domain.TodayModel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reason codes saved with a skipped dose. History and the doctor report (M6) map them back to words. */
enum class SkipReason(val code: String) {
    NOT_FEELING_WELL("not_feeling_well"), SIDE_EFFECTS("side_effects"), RAN_OUT("ran_out"), DOCTOR_SAID("doctor_said"), OTHER("other");

    companion object { fun fromCode(code: String?) = values().firstOrNull { it.code == code } }
}

sealed interface TodaySheet {
    data class Skip(val rows: List<DoseRow>) : TodaySheet
    data class TooSoon(
        val rows: List<DoseRow>, val medicine: String, val lastTaken: LocalDateTime, val minGapHours: Int, val earlyMedicineIds: Set<Long>,
    ) : TodaySheet
    data class OutOfStock(val rows: List<DoseRow>, val medicine: Medicine, val earlyMedicineIds: Set<Long>) : TodaySheet
    /** Add stock, opened from "Update my stock" on the out-of-stock sheet. */
    data class AddStock(val medicine: Medicine, val schedule: Schedule?) : TodaySheet
}

sealed interface ToastKind {
    data class Taken(val names: List<String>) : ToastKind
    data class Skipped(val names: List<String>) : ToastKind
    data class StockUpdated(val unit: DoseUnit, val result: StockResult) : ToastKind
}

/** [undo] removes what the action created. [id] is new for every toast so the same text can show twice. */
data class TodayToast(val id: Int, val kind: ToastKind, val undo: List<Undo>)

data class Undo(val medicine: Medicine, val eventId: Long, val wasTake: Boolean, val restore: Double = 0.0)

/** A medicine that is running low, for the strip at the bottom of Today. */
data class LowStock(val name: String, val unit: DoseUnit, val daysLeft: Int?, val level: StockLevel)

data class TodayUiState(
    val loaded: Boolean = false,
    val hasMedicines: Boolean = false,
    val model: TodayModel? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val now: LocalDateTime = LocalDateTime.now(),
    val sheet: TodaySheet? = null,
    val toast: TodayToast? = null,
    /** Set when every medicine is paused: Today shows the "All medicines paused" screen. */
    val allPaused: AllPaused? = null,
    val lowStock: List<LowStock> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: PillBoxRepository,
    meName: String,
    /** Called after anything that changes reminders. */
    private val onChanged: suspend () -> Unit = {},
) : ViewModel() {
    private var pausedIds: List<Long> = emptyList()
    private val selected = MutableStateFlow(LocalDate.now())
    private val sheet = MutableStateFlow<TodaySheet?>(null)
    private val toast = MutableStateFlow<TodayToast?>(null)
    private var toastCounter = 0

    private val tick = flow { while (true) { emit(LocalDateTime.now()); delay(30_000) } }

    private val data = flow { emit(repo.ensureMe(meName)) }.flatMapLatest { me ->
        selected.flatMapLatest { day ->
            combine(
                repo.observeMedicines(me.id),
                repo.observeDoseEvents(me.id, day.atStartOfDay(), day.plusDays(1).atStartOfDay()),
            ) { medicines, events -> medicines to events }
        }
    }

    val state: StateFlow<TodayUiState> = combine(data, selected, tick, sheet, toast) { (medicines, events), day, now, sheet, toast ->
        val items = medicines.mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
        val allPaused = Pause.allPaused(medicines.map { it.pauses }, now)
        pausedIds = medicines.map { it.medicine.id }
        TodayUiState(
            loaded = true,
            allPaused = allPaused,
            lowStock = medicines
                .filter { Pause.activeAt(it.pauses, now) == null }
                .mapNotNull { m ->
                    val info = StockMath.info(m.medicine, m.schedule, now.toLocalDate())
                    if (info.level == StockLevel.ENOUGH) null else LowStock(m.medicine.name, m.medicine.unit, info.daysLeft, info.level)
                }
                .sortedBy { if (it.level == StockLevel.OUT) -1 else it.daysLeft ?: Int.MAX_VALUE },
            hasMedicines = medicines.isNotEmpty(),
            model = TodayBuilder.build(items, events, day, now),
            selectedDate = day, now = now, sheet = sheet, toast = toast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** "Resume now" on the all-paused screen. */
    fun resumeAll() {
        val ids = pausedIds
        viewModelScope.launch {
            val now = LocalDateTime.now()
            ids.forEach { repo.resume(it, now) }
            onChanged()
        }
    }

    fun selectDate(day: LocalDate) { selected.value = day }
    fun backToToday() { selected.value = LocalDate.now() }
    fun dismissSheet() { sheet.value = null }
    fun toastShown() { toast.value = null }

    // ---- take ----
    fun take(rows: List<DoseRow>) {
        val pending = rows.filter { it.status == RowStatus.PENDING }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            val now = LocalDateTime.now()
            for (row in pending) {
                val f = row.schedule.frequency as? Frequency.EveryNHours ?: continue
                val last = repo.takenTimesSince(row.medicine.id, now.minusHours(48)).firstOrNull() ?: continue
                if (LateRules.hoursCheck(now, last, f.hours) is TakeCheck.TooSoon) {
                    sheet.value = TodaySheet.TooSoon(pending, row.medicine.name, last, f.hours, setOf(row.medicine.id))
                    return@launch
                }
            }
            checkStockThenCommit(pending, emptySet())
        }
    }

    fun takeAnyway() {
        val s = sheet.value as? TodaySheet.TooSoon ?: return
        sheet.value = null
        checkStockThenCommit(s.rows, s.earlyMedicineIds)
    }

    private fun checkStockThenCommit(rows: List<DoseRow>, early: Set<Long>) {
        val out = rows.firstOrNull { it.medicine.stock <= 0.0 }
        if (out != null) {
            sheet.value = TodaySheet.OutOfStock(rows, out.medicine, early)
        } else {
            commitTake(rows, early)
        }
    }

    /** "Update my stock" on the out-of-stock sheet. */
    fun updateStock() {
        val s = sheet.value as? TodaySheet.OutOfStock ?: return
        viewModelScope.launch { sheet.value = TodaySheet.AddStock(s.medicine, repo.getSchedule(s.medicine.id)) }
    }

    fun saveStock(boxes: Int, perBox: Int) {
        val s = sheet.value as? TodaySheet.AddStock ?: return
        sheet.value = null
        viewModelScope.launch {
            val amount = boxes.toLong() * perBox
            repo.addStock(s.medicine.id, amount.toDouble(), perBox)
            val total = s.medicine.stock + amount
            val result = StockResult(s.medicine.name, total, StockMath.daysFor(total, s.medicine, s.schedule))
            toast.value = TodayToast(++toastCounter, ToastKind.StockUpdated(s.medicine.unit, result), emptyList())
        }
    }

    fun takeWithoutStock() {
        val s = sheet.value as? TodaySheet.OutOfStock ?: return
        sheet.value = null
        commitTake(s.rows, s.earlyMedicineIds)
    }

    private fun commitTake(rows: List<DoseRow>, early: Set<Long>) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val undo = rows.map { r ->
                val id = repo.takeDose(
                    r.medicine,
                    DoseEvent(
                        planned = r.planned, taken = now, status = DoseStatus.TAKEN,
                        early = r.medicine.id in early,
                        lateMinutes = LateRules.lateMinutes(r.planned, now, TodayBuilder.DEFAULT_LATE_THRESHOLD),
                    ),
                )
                Undo(r.medicine, id, wasTake = true, restore = minOf(r.medicine.doseAmount, r.medicine.stock))
            }
            toast.value = TodayToast(++toastCounter, ToastKind.Taken(rows.map { it.medicine.name }), undo)
        }
    }

    // ---- skip ----
    fun skip(rows: List<DoseRow>) {
        val pending = rows.filter { it.status == RowStatus.PENDING }
        if (pending.isNotEmpty()) sheet.value = TodaySheet.Skip(pending)
    }

    fun confirmSkip(reason: SkipReason?) {
        val s = sheet.value as? TodaySheet.Skip ?: return
        sheet.value = null
        viewModelScope.launch {
            val undo = s.rows.map { r ->
                val id = repo.recordDose(
                    DoseEvent(medicineId = r.medicine.id, planned = r.planned, status = DoseStatus.SKIPPED, skipReason = reason?.code),
                )
                Undo(r.medicine, id, wasTake = false)
            }
            toast.value = TodayToast(++toastCounter, ToastKind.Skipped(s.rows.map { it.medicine.name }), undo)
        }
    }

    // ---- undo ----
    fun undo() {
        val t = toast.value ?: return
        toast.value = null
        viewModelScope.launch {
            t.undo.forEach { u -> if (u.wasTake) repo.undoTake(u.medicine, u.eventId, u.restore) else repo.deleteDose(u.eventId) }
        }
    }
}
