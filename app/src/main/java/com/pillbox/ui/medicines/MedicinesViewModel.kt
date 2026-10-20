package com.pillbox.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.Pause
import com.pillbox.domain.RefillSettings
import com.pillbox.domain.StockInfo
import com.pillbox.domain.StockMath
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedRow(val item: MedicineWithSchedule, val info: StockInfo, val pause: Pause? = null)

data class MedicinesUiState(
    val loaded: Boolean = false,
    val active: List<MedRow> = emptyList(),
    val pausedCount: Int = 0,
    /** Days before running out that Refill alerts start, for the note at the bottom. */
    val remindDays: Int = RefillSettings.DEFAULT_DAYS,
    val finished: List<MedicineWithSchedule> = emptyList(),
)

/** The Medicines tab: active medicines with their stock, and the Finished tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModel(
    private val repo: PillBoxRepository,
    meName: String,
    remindDays: Flow<Int> = flowOf(RefillSettings.DEFAULT_DAYS),
) : ViewModel() {

    init {
        // Courses whose last day has passed move to Finished.
        viewModelScope.launch { repo.finishEndedCourses(LocalDate.now()) }
    }

    val state: StateFlow<MedicinesUiState> =
        repo.observeActivePerson(meName).map { it.id }.distinctUntilChanged()
            .flatMapLatest { personId ->
                combine(repo.observeMedicines(personId), repo.observeFinished(personId), remindDays) { active, finished, remind ->
                    val today = LocalDate.now()
                    val now = LocalDateTime.now()
                    val rows = active.map { MedRow(it, StockMath.info(it.medicine, it.schedule, today, remind), Pause.activeAt(it.pauses, now)) }
                    MedicinesUiState(
                        loaded = true,
                        active = rows,
                        pausedCount = rows.count { it.pause != null },
                        remindDays = remind,
                        finished = finished,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesUiState())
}
