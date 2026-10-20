package com.pillbox.ui.prn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.PrnDay
import com.pillbox.domain.PrnRules
import com.pillbox.domain.TakeCheck
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrnUiState(
    val loaded: Boolean = false,
    val medicine: Medicine? = null,
    val day: PrnDay? = null,
    /** Today's taken doses, newest first, with the Early mark. */
    val doses: List<DoseEvent> = emptyList(),
    /** "Too soon for this dose?" is open: the last dose time and the gap in minutes. */
    val tooSoon: Pair<LocalDateTime, Int>? = null,
    /** Set when a dose was just logged by this screen; the time shown in the toast. [toastId] changes for every toast. */
    val loggedAt: LocalDateTime? = null,
    val toastId: Int = 0,
)

/**
 * The as-needed log: "Take now" checks the least time between doses and the most in one day (the rules in [PrnRules]),
 * logs the dose, and shows today's list. With [autoLog] the screen logs a dose as soon as it opens.
 */
class PrnViewModel(
    private val repo: PillBoxRepository,
    private val medicineId: Long,
    private val autoLog: Boolean,
    private val onChanged: suspend () -> Unit = {},
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {
    private val _state = MutableStateFlow(PrnUiState())
    val state: StateFlow<PrnUiState> = _state.asStateFlow()
    private var started = false

    init {
        viewModelScope.launch {
            refresh()
            if (autoLog && !started) { started = true; takeNow() }
        }
    }

    private suspend fun refresh() {
        val m = repo.getMedicine(medicineId)
        val f = repo.getSchedule(medicineId)?.frequency as? Frequency.AsNeeded
        if (m == null || f == null) { _state.update { it.copy(loaded = true, medicine = null) }; return }
        val at = now()
        val events = repo.takenEventsSince(medicineId, at.toLocalDate().minusDays(1).atStartOfDay())
        val day = PrnRules.day(at, events.mapNotNull { it.taken }, f.minGapMinutes, f.maxPerDay)
        val today = events.filter { it.taken?.toLocalDate() == at.toLocalDate() }
        _state.update { it.copy(loaded = true, medicine = m, day = day, doses = today) }
    }

    /** The Take now button. */
    fun takeNow() {
        viewModelScope.launch {
            refresh()
            val day = _state.value.day ?: return@launch
            when (val c = day.check) {
                TakeCheck.Allowed -> log(early = false)
                is TakeCheck.TooSoon -> {
                    val last = day.takenToday.firstOrNull() ?: _state.value.doses.firstNotNullOfOrNull { it.taken } ?: c.nextAllowed.minusMinutes(day.minGapMinutes.toLong())
                    _state.update { it.copy(tooSoon = last to day.minGapMinutes) }
                }
                is TakeCheck.DailyMaxReached -> Unit // no Take button, no override
            }
        }
    }

    fun takeAnyway() { _state.update { it.copy(tooSoon = null) }; viewModelScope.launch { log(early = true) } }

    fun dismissTooSoon() = _state.update { it.copy(tooSoon = null) }

    private suspend fun log(early: Boolean) {
        val m = _state.value.medicine ?: return
        val at = now()
        repo.takeDose(m, DoseEvent(planned = null, taken = at, status = DoseStatus.TAKEN, early = early))
        runCatching { onChanged() }
        refresh()
        _state.update { it.copy(loggedAt = at, toastId = it.toastId + 1) }
    }

    /** "Undo last dose" on the limit screen: removes the newest dose of today and gives the tablet back. */
    fun undoLast() {
        viewModelScope.launch {
            val m = _state.value.medicine ?: return@launch
            val last = _state.value.doses.firstOrNull() ?: return@launch
            repo.undoTake(m, last.id)
            runCatching { onChanged() }
            refresh()
            _state.update { it.copy(loggedAt = null) }
        }
    }
}
