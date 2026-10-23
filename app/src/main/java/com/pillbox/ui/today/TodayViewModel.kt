package com.pillbox.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.HistoryBuilder
import com.pillbox.domain.AllPaused
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.LateAction
import com.pillbox.domain.LateLog
import com.pillbox.domain.LateChoice
import com.pillbox.domain.LateRules
import com.pillbox.domain.LateSetting
import com.pillbox.domain.ScheduleEngine
import com.pillbox.ui.ReminderState
import java.time.Duration
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    /**
     * Taken late: Move the next doses or Keep the times. [rows] are everything being taken; [askRows] are the late ones that
     * need an answer. Times are worked out when the sheet opens so the text matches what Move will do.
     */
    data class Late(
        val rows: List<DoseRow>, val early: Set<Long>, val askRows: List<DoseRow>, val takenAt: LocalDateTime,
        val minutesLate: Int, val duePlanned: LocalDateTime,
        /** Every few hours: the gap in hours. Null for fixed times. */
        val gapHours: Int?,
        /** What Move would ring at next (first two). */
        val moveTimes: List<LocalDateTime>,
        /** Delay moved, for fixed times. */
        val delayMinutes: Int,
        /** Where the next dose stays if kept, and the real gap to it. */
        val keepNext: LocalDateTime?, val keepGapMinutes: Int?,
    ) : TodaySheet
    data class OutOfStock(val rows: List<DoseRow>, val medicine: Medicine, val earlyMedicineIds: Set<Long>) : TodaySheet
    /**
     * "I took it" on a missed dose, or a change of time on a dose already marked taken ([edit]). Only the last 7 days.
     * Nothing is saved until the person picks the time and presses Save.
     */
    data class LogTaken(val row: DoseRow, val edit: Boolean) : TodaySheet
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

data class Undo(
    val medicine: Medicine, val eventId: Long, val wasTake: Boolean, val restore: Double = 0.0,
    /** Set when the take also moved the next doses: the moves to put back on Undo. */
    val restoreShifts: Map<LocalDateTime, LocalDateTime?>? = null,
)

/** A medicine that is running low, for the strip at the bottom of Today. */
data class LowStock(val name: String, val unit: DoseUnit, val daysLeft: Int?, val level: StockLevel)

/** An as-needed medicine for the "As needed" card on Today. */
data class AsNeededItem(val id: Long, val name: String, val unit: DoseUnit)

/** The last 7 days (today included) of the person on screen: doses taken on time or late, out of all doses counted. */
data class WeekSummary(val taken: Int, val total: Int)

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
    val asNeeded: List<AsNeededItem> = emptyList(),
    val week: WeekSummary? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: PillBoxRepository,
    meName: String,
    /** Called after anything that changes reminders. */
    private val onChanged: suspend () -> Unit = {},
    remindDays: kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(com.pillbox.domain.RefillSettings.DEFAULT_DAYS),
    /** Saves "Always do this" from the Taken late sheet into the Late setting. */
    private val saveLateSetting: suspend (LateSetting) -> Unit = {},
) : ViewModel() {
    private var pausedIds: List<Long> = emptyList()
    private val selected = MutableStateFlow(LocalDate.now())
    private val sheet = MutableStateFlow<TodaySheet?>(null)
    private val toast = MutableStateFlow<TodayToast?>(null)
    private var toastCounter = 0

    private val tick = flow { while (true) { emit(LocalDateTime.now()); delay(30_000) } }

    private class TodayData(
        val medicines: List<MedicineWithSchedule>, val events: List<DoseEvent>, val remind: Int,
        val finished: List<MedicineWithSchedule>, val weekEvents: List<DoseEvent>,
    )

    private val data = repo.observeActivePerson(meName).map { it.id }.distinctUntilChanged().flatMapLatest { personId ->
        selected.flatMapLatest { day ->
            val weekStart = LocalDate.now().minusDays(6).atStartOfDay()
            combine(
                repo.observeMedicines(personId),
                repo.observeDoseEvents(personId, day.atStartOfDay(), day.plusDays(1).atStartOfDay()),
                remindDays,
                repo.observeFinished(personId),
                repo.observeHistoryEvents(personId, weekStart, LocalDate.now().plusDays(1).atStartOfDay()),
            ) { medicines, events, remind, finished, weekEvents -> TodayData(medicines, events, remind, finished, weekEvents) }
        }
    }

    val state: StateFlow<TodayUiState> = combine(data, selected, tick, sheet, toast) { d, day, now, sheet, toast ->
        val medicines = d.medicines; val events = d.events; val remind = d.remind
        val items = medicines.mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
        val allPaused = Pause.allPaused(medicines.map { it.pauses }, now)
        pausedIds = medicines.map { it.medicine.id }
        TodayUiState(
            loaded = true,
            week = HistoryBuilder.build((medicines + d.finished).mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }, d.weekEvents, now, days = 7)
                .let { h -> if (h.total == 0) null else WeekSummary(h.onTime + h.late, h.total) },
            allPaused = allPaused,
            lowStock = medicines
                .filter { Pause.activeAt(it.pauses, now) == null }
                .mapNotNull { m ->
                    val info = StockMath.info(m.medicine, m.schedule, now.toLocalDate(), remind)
                    if (info.level == StockLevel.ENOUGH) null else LowStock(m.medicine.name, m.medicine.unit, info.daysLeft, info.level)
                }
                .sortedBy { if (it.level == StockLevel.OUT) -1 else it.daysLeft ?: Int.MAX_VALUE },
            asNeeded = medicines
                .filter { it.schedule?.frequency is Frequency.AsNeeded && Pause.activeAt(it.pauses, now) == null }
                .map { AsNeededItem(it.medicine.id, it.medicine.name, it.medicine.unit) }
                .sortedBy { it.name.lowercase() },
            hasMedicines = medicines.isNotEmpty(),
            model = TodayBuilder.build(items, events, day, now, ReminderState.current.lateThreshold),
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
            askLateThenCommit(rows, early)
        }
    }

    /** Taken late: when a late dose needs an answer, ask before committing. */
    private fun askLateThenCommit(rows: List<DoseRow>, early: Set<Long>) {
        val settings = ReminderState.current
        val takenAt = LocalDateTime.now()
        val ask = rows.filter {
            val late = LateRules.lateMinutes(it.planned, takenAt, settings.lateThreshold)
            late != null && LateRules.lateAction(it.schedule.frequency, settings.late) == LateAction.ShowSheet
        }
        val first = ask.firstOrNull()
        if (first == null) { commitTake(rows, early, null, takenAt); return }
        val f = first.schedule.frequency
        val entries = LateRules.moveEntries(first.schedule, first.planned, takenAt)
        val moveTimes = if (f is Frequency.EveryNHours) entries.values.filterNotNull().sorted().take(2)
        else entries.values.filterNotNull().sorted().take(1)
        val today = ScheduleEngine.plannedDoses(first.schedule, first.planned.toLocalDate())
        val next = (today + ScheduleEngine.plannedDoses(first.schedule, first.planned.toLocalDate().plusDays(1))).firstOrNull { it.isAfter(first.planned) }
        sheet.value = TodaySheet.Late(
            rows = rows, early = early, askRows = ask, takenAt = takenAt,
            minutesLate = Duration.between(first.planned, takenAt).toMinutes().toInt(), duePlanned = first.planned,
            gapHours = (f as? Frequency.EveryNHours)?.hours, moveTimes = moveTimes,
            delayMinutes = Duration.between(first.planned, takenAt).toMinutes().toInt(),
            keepNext = next, keepGapMinutes = next?.let { Duration.between(takenAt, it).toMinutes().toInt() },
        )
    }

    /** "Continue" on the Taken late sheet. [always] saves the choice into the Late setting (fixed-time medicines). */
    fun confirmLate(choice: LateChoice, always: Boolean) {
        val s = sheet.value as? TodaySheet.Late ?: return
        sheet.value = null
        if (always) viewModelScope.launch { saveLateSetting(if (choice == LateChoice.MOVE) LateSetting.MOVE_NEXT else LateSetting.KEEP) }
        commitTake(s.rows, s.early, choice, s.takenAt)
    }

    /** "Update my stock" on the out-of-stock sheet. */
    fun updateStock() {
        val s = sheet.value as? TodaySheet.OutOfStock ?: return
        viewModelScope.launch { sheet.value = TodaySheet.AddStock(s.medicine, repo.getSchedule(s.medicine.id)) }
    }

    fun saveStock(boxes: Int, perBox: Int, expiry: java.time.YearMonth?) {
        val s = sheet.value as? TodaySheet.AddStock ?: return
        sheet.value = null
        viewModelScope.launch {
            val amount = boxes.toLong() * perBox
            repo.addStock(s.medicine.id, amount.toDouble(), perBox, expiry)
            val total = s.medicine.stock + amount
            val result = StockResult(s.medicine.name, total, StockMath.daysFor(total, s.medicine, s.schedule))
            toast.value = TodayToast(++toastCounter, ToastKind.StockUpdated(s.medicine.unit, result), emptyList())
        }
    }

    fun takeWithoutStock() {
        val s = sheet.value as? TodaySheet.OutOfStock ?: return
        sheet.value = null
        askLateThenCommit(s.rows, s.earlyMedicineIds)
    }

    private fun commitTake(rows: List<DoseRow>, early: Set<Long>, asked: LateChoice?, takenAt: LocalDateTime) {
        viewModelScope.launch {
            val settings = ReminderState.current
            var moved = false
            val undo = rows.map { r ->
                val late = LateRules.lateMinutes(r.planned, takenAt, settings.lateThreshold)
                val id = repo.takeDose(
                    r.medicine,
                    DoseEvent(planned = r.planned, taken = takenAt, status = DoseStatus.TAKEN, early = r.medicine.id in early, lateMinutes = late),
                )
                var before: Map<LocalDateTime, LocalDateTime?>? = null
                if (LateRules.effectiveChoice(r.schedule.frequency, settings.late, late, asked) == LateChoice.MOVE) {
                    val entries = LateRules.moveEntries(r.schedule, r.planned, takenAt)
                    if (entries.isNotEmpty()) { before = repo.moveDoses(r.medicine.id, entries); moved = true }
                }
                Undo(r.medicine, id, wasTake = true, restore = minOf(r.medicine.doseAmount, r.medicine.stock), restoreShifts = before)
            }
            if (moved) onChanged()
            toast.value = TodayToast(++toastCounter, ToastKind.Taken(rows.map { it.medicine.name }), undo)
        }
    }

    // ---- log a dose taken earlier ----
    fun logTaken(row: DoseRow) {
        if (!LateLog.canLog(row.planned, LocalDateTime.now())) return
        sheet.value = TodaySheet.LogTaken(row, edit = row.status == RowStatus.TAKEN)
    }

    /** Save on the log sheet. [picked] is the clock time chosen; the sheet only enables Save for a time that has already passed. */
    fun confirmLog(picked: java.time.LocalTime) {
        val s = sheet.value as? TodaySheet.LogTaken ?: return
        val now = LocalDateTime.now()
        val takenAt = LateLog.takenAt(s.row.planned, picked, now) ?: return
        sheet.value = null
        val r = s.row
        viewModelScope.launch {
            val late = LateRules.lateMinutes(r.planned, takenAt, ReminderState.current.lateThreshold)
            val existing = r.event
            if (s.edit && existing != null) {
                repo.updateDose(existing.copy(taken = takenAt, lateMinutes = late, early = false))
                toast.value = TodayToast(++toastCounter, ToastKind.Taken(listOf(r.medicine.name)), emptyList())
            } else {
                val id = repo.takeDose(r.medicine, DoseEvent(planned = r.planned, taken = takenAt, status = DoseStatus.TAKEN, lateMinutes = late, loggedLater = true))
                toast.value = TodayToast(++toastCounter, ToastKind.Taken(listOf(r.medicine.name)), listOf(Undo(r.medicine, id, wasTake = true, restore = minOf(r.medicine.doseAmount, r.medicine.stock))))
            }
            onChanged()
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
            t.undo.forEach { u ->
                if (u.wasTake) repo.undoTake(u.medicine, u.eventId, u.restore) else repo.deleteDose(u.eventId)
                u.restoreShifts?.let { repo.setShifts(u.medicine.id, it) }
            }
            if (t.undo.any { it.restoreShifts != null }) onChanged()
        }
    }
}
