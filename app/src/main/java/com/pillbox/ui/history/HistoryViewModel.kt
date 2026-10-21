package com.pillbox.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.History
import com.pillbox.domain.HistoryBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val loaded: Boolean = false,
    val personName: String = "",
    val today: LocalDate = LocalDate.now(),
    val history: History? = null,
)

/** The History screen: the last 30 days of the person on screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(repo: PillBoxRepository, meName: String) : ViewModel() {
    val state: StateFlow<HistoryUiState> = flow { emit(repo.activePerson(meName)) }
        .flatMapLatest { me ->
            val now = LocalDateTime.now()
            val start = now.toLocalDate().minusDays((HistoryBuilder.DEFAULT_DAYS - 1).toLong())
            combine(
                repo.observeMedicines(me.id),
                repo.observeFinished(me.id),
                repo.observeHistoryEvents(me.id, start.atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay()),
            ) { active, finished, events ->
                val items = (active + finished).mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
                val at = LocalDateTime.now()
                HistoryUiState(true, me.name, at.toLocalDate(), HistoryBuilder.build(items, events, at))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
