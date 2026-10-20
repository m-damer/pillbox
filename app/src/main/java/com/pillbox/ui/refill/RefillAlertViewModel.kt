package com.pillbox.ui.refill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.data.RefillSettingsStore
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.RefillSettings
import com.pillbox.domain.StockMath
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The medicine used as the example in the preview card: the one that runs out first. */
data class PreviewMedicine(val name: String, val unit: DoseUnit, val stock: Double, val daysLeft: Int)

data class RefillUiState(
    val loaded: Boolean = false,
    val settings: RefillSettings = RefillSettings(),
    val original: RefillSettings = RefillSettings(),
    val preview: PreviewMedicine? = null,
    val showUnsaved: Boolean = false,
    val showTimePicker: Boolean = false,
    val saved: Boolean = false,
) {
    val dirty get() = settings != original
}

class RefillAlertViewModel(
    private val repo: PillBoxRepository,
    private val store: RefillSettingsStore,
    /** Called after saving so the daily refill alarm is armed again. */
    private val onChanged: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(RefillUiState())
    val state: StateFlow<RefillUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = store.current()
            val preview = repo.allActiveWithSchedules()
                .filter { it.medicine.status == MedicineStatus.ACTIVE && it.medicine.stock > 0.0 }
                .mapNotNull { m ->
                    val days = StockMath.daysLeft(m.medicine, m.schedule) ?: return@mapNotNull null
                    PreviewMedicine(m.medicine.name, m.medicine.unit, m.medicine.stock, days)
                }
                .minByOrNull { it.daysLeft }
            _state.update { it.copy(loaded = true, settings = s, original = s, preview = preview) }
        }
    }

    fun setDays(days: Int) = _state.update { it.copy(settings = it.settings.copy(daysBefore = RefillSettings.clampDays(days))) }
    fun setTime(time: LocalTime) = _state.update { it.copy(settings = it.settings.copy(time = time), showTimePicker = false) }
    fun setIncludePeople(on: Boolean) = _state.update { it.copy(settings = it.settings.copy(includePeople = on)) }
    fun openTimePicker() = _state.update { it.copy(showTimePicker = true) }
    fun closeTimePicker() = _state.update { it.copy(showTimePicker = false) }
    fun dismissUnsaved() = _state.update { it.copy(showUnsaved = false) }

    /** Back arrow: leaves at once, or asks first when something changed. Returns true when it is safe to leave. */
    fun back(): Boolean {
        if (_state.value.dirty) { _state.update { it.copy(showUnsaved = true) }; return false }
        return true
    }

    fun save() {
        viewModelScope.launch {
            store.save(_state.value.settings)
            onChanged()
            _state.update { it.copy(saved = true, original = it.settings, showUnsaved = false) }
        }
    }

    /** First day the preview alert would ring, for the sentence under the example medicine. */
    fun firstAlertDate(today: LocalDate = LocalDate.now()): LocalDate? =
        _state.value.preview?.let { com.pillbox.domain.RefillPlanner.firstAlertDate(today, it.daysLeft, _state.value.settings.daysBefore) }
}
