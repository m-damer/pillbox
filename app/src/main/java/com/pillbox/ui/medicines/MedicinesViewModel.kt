package com.pillbox.ui.medicines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.Pause
import com.pillbox.domain.StockInfo
import com.pillbox.domain.StockMath
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedRow(val item: MedicineWithSchedule, val info: StockInfo, val pause: Pause? = null)

data class MedicinesUiState(
    val loaded: Boolean = false,
    val active: List<MedRow> = emptyList(),
    val pausedCount: Int = 0,
    val finished: List<MedicineWithSchedule> = emptyList(),
)

/** The Medicines tab: active medicines with their stock, and the Finished tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicinesViewModel(private val repo: PillBoxRepository, meName: String) : ViewModel() {

    init {
        // Courses whose last day has passed move to Finished.
        viewModelScope.launch { repo.finishEndedCourses(LocalDate.now()) }
    }

    val state: StateFlow<MedicinesUiState> =
        flow { emit(repo.ensureMe(meName)) }
            .flatMapLatest { me ->
                combine(repo.observeMedicines(me.id), repo.observeFinished(me.id)) { active, finished ->
                    val today = LocalDate.now()
                    val now = LocalDateTime.now()
                    val rows = active.map { MedRow(it, StockMath.info(it.medicine, it.schedule, today), Pause.activeAt(it.pauses, now)) }
                    MedicinesUiState(
                        loaded = true,
                        active = rows,
                        pausedCount = rows.count { it.pause != null },
                        finished = finished,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicinesUiState())
}
