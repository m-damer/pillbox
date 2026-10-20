package com.pillbox.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DaySummary
import com.pillbox.domain.PeopleRules
import com.pillbox.domain.Person
import com.pillbox.domain.TodayBuilder
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonRow(val person: Person, val today: DaySummary)

data class PeopleUiState(
    val loaded: Boolean = false,
    val rows: List<PersonRow> = emptyList(),
    val activeId: Long? = null,
    /** True when every person but me rings. Only meaningful when there is someone besides me. */
    val ringOthers: Boolean = true,
)

/** The People tab: everyone, how today is going for each, and the switch that turns ringing for others on or off. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(
    private val repo: PillBoxRepository,
    meName: String,
    private val onChanged: suspend () -> Unit = {},
) : ViewModel() {

    val state: StateFlow<PeopleUiState> = repo.observePeople()
        .flatMapLatest { people ->
            if (people.isEmpty()) return@flatMapLatest flowOf(PeopleUiState(loaded = true))
            val perPerson = people.map { p ->
                val today = LocalDateTime.now().toLocalDate()
                combine(
                    repo.observeMedicines(p.id),
                    repo.observeDoseEvents(p.id, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                ) { meds, events ->
                    val now = LocalDateTime.now()
                    val items = meds.mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
                    PersonRow(p, PeopleRules.summarize(TodayBuilder.build(items, events, now.toLocalDate(), now)))
                }
            }
            combine(perPerson) { it.toList() }.combine(repo.activePersonId) { rows, active ->
                val others = rows.filter { !it.person.isMe }
                PeopleUiState(
                    loaded = true, rows = rows,
                    activeId = active?.takeIf { id -> rows.any { it.person.id == id } } ?: rows.firstOrNull { it.person.isMe }?.person?.id,
                    ringOthers = others.isEmpty() || others.all { it.person.ring },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    init { viewModelScope.launch { repo.ensureMe(meName) } }

    fun select(id: Long) = repo.selectPerson(id)

    fun setRingOthers(on: Boolean) {
        viewModelScope.launch {
            repo.setRingForOthers(on)
            runCatching { onChanged() }
        }
    }
}
