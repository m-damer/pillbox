package com.pillbox.ui.addmed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.AddMedicineForm
import com.pillbox.domain.FreqChoice
import com.pillbox.domain.ScheduleEngine
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AddMedDialog {
    data class Duplicate(val name: String) : AddMedDialog
    data object Unsaved : AddMedDialog
    data object SaveFailed : AddMedDialog
    /** [firstDose] is null for as-needed medicines or when no dose is left to ring. */
    data class Added(val name: String, val firstDose: LocalDateTime?, val asNeeded: Boolean) : AddMedDialog
}

data class AddMedUiState(
    val form: AddMedicineForm = AddMedicineForm(),
    val step: Int = 1,
    val showErrors: Boolean = false,
    val dialog: AddMedDialog? = null,
    val saving: Boolean = false,
    /** At least one medicine was saved in this session of the flow. */
    val savedAny: Boolean = false,
    /** Edit mode: the name shown in the header, and the form as it was when opened. */
    val editingName: String? = null,
    val original: AddMedicineForm? = null,
    /** Edit mode: the changes were saved. The screen closes. */
    val saved: Boolean = false,
) {
    val isEdit get() = editingName != null
}

class AddMedicineViewModel(
    private val repo: PillBoxRepository,
    private val meName: String,
    private val onChanged: suspend () -> Unit = {},
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    /** Set to edit this medicine instead of adding a new one. */
    private val editing: MedicineWithSchedule? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        if (editing?.schedule != null) {
            val form = AddMedicineForm.fromExisting(editing.medicine, editing.schedule)
            AddMedUiState(form = form, editingName = editing.medicine.name, original = form)
        } else AddMedUiState(),
    )
    val state: StateFlow<AddMedUiState> = _state.asStateFlow()

    /** Set when the user chose "Add anyway", so the duplicate prompt is not shown twice. */
    private var duplicateAccepted = false

    fun edit(change: (AddMedicineForm) -> AddMedicineForm) = _state.update { it.copy(form = change(it.form)) }

    fun next() {
        val s = _state.value
        when (s.step) {
            1 -> {
                if (s.form.step1Errors().isNotEmpty()) { _state.update { it.copy(showErrors = true) }; return }
                viewModelScope.launch {
                    val me = repo.ensureMe(meName)
                    val renamed = editing == null || !editing.medicine.name.equals(s.form.name.trim(), ignoreCase = true)
                    if (renamed && !duplicateAccepted && repo.isDuplicateName(me.id, s.form.name)) {
                        _state.update { it.copy(dialog = AddMedDialog.Duplicate(s.form.name.trim())) }
                    } else {
                        goTo(2)
                    }
                }
            }
            2 -> goTo(3)
        }
    }

    private fun goTo(step: Int) = _state.update { it.copy(step = step, showErrors = false, dialog = null) }

    fun addAnyway() { duplicateAccepted = true; goTo(2) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.form.step3Errors().isNotEmpty()) { _state.update { it.copy(showErrors = true) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val at = now()
                val schedule = s.form.schedule(at.toLocalDate())
                if (editing != null) {
                    repo.updateMedicine(s.form.updated(editing.medicine), schedule)
                    runCatching { onChanged() }
                    _state.update { it.copy(saving = false, saved = true) }
                    return@launch
                }
                val me = repo.ensureMe(meName)
                repo.addMedicine(s.form.medicine(me.id), schedule)
                runCatching { onChanged() } // arm the next reminder; a failure here must not undo the save
                val first = ScheduleEngine.nextDose(schedule, emptyList(), at)
                _state.update {
                    it.copy(saving = false, savedAny = true, dialog = AddMedDialog.Added(s.form.name.trim(), first, s.form.freq == FreqChoice.AS_NEEDED))
                }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, dialog = AddMedDialog.SaveFailed) }
            }
        }
    }

    /** Back arrow or system back. Returns true when the flow should close. */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.step > 1 -> { _state.update { it.copy(step = s.step - 1, showErrors = false) }; false }
            (if (s.isEdit) s.form != s.original else s.form.isDirty()) -> { _state.update { it.copy(dialog = AddMedDialog.Unsaved) }; false }
            else -> true
        }
    }

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    /** "Add another medicine" after a success. */
    fun startOver() {
        duplicateAccepted = false
        _state.value = AddMedUiState(savedAny = _state.value.savedAny)
    }
}
