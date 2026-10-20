package com.pillbox.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.PeopleRules
import com.pillbox.domain.Person
import com.pillbox.domain.Relation
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add a person, edit one, or edit my own profile (name and birth year, for the doctor report). */
enum class PersonMode { ADD, EDIT, PROFILE }

/** What happened when the screen closed, so the People tab can show the right toast. */
sealed interface PersonResult {
    data class Added(val name: String) : PersonResult
    data object Saved : PersonResult
    data class Removed(val name: String) : PersonResult
}

data class PersonEditState(
    val loaded: Boolean = false,
    val mode: PersonMode = PersonMode.ADD,
    val isMe: Boolean = false,
    val name: String = "",
    val relation: Relation = Relation.PARENT,
    val colorIndex: Int = 0,
    val ring: Boolean = true,
    val birthYear: String = "",
    val birthYearError: Boolean = false,
    val showRemove: Boolean = false,
    val saving: Boolean = false,
    val done: PersonResult? = null,
) {
    val canSave: Boolean get() = PeopleRules.canSave(name) && !saving
}

class PersonEditViewModel(
    private val repo: PillBoxRepository,
    private val meName: String,
    private val mode: PersonMode,
    private val personId: Long,
    private val onChanged: suspend () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(PersonEditState(mode = mode, isMe = mode == PersonMode.PROFILE))
    val state: StateFlow<PersonEditState> = _state.asStateFlow()
    private var original: Person? = null

    init {
        viewModelScope.launch {
            when (mode) {
                PersonMode.ADD -> {
                    val used = repo.observePeople().first().map { it.colorIndex }
                    _state.update { it.copy(loaded = true, colorIndex = PeopleRules.nextColor(used)) }
                }
                PersonMode.EDIT, PersonMode.PROFILE -> {
                    val p = if (mode == PersonMode.PROFILE) repo.ensureMe(meName) else repo.getPerson(personId)
                    if (p == null) {
                        _state.update { it.copy(loaded = true, done = PersonResult.Saved) }
                    } else {
                        original = p
                        _state.update {
                            it.copy(
                                loaded = true, isMe = p.isMe, name = p.name, relation = p.relation, colorIndex = p.colorIndex, ring = p.ring,
                                birthYear = p.birthYear?.toString().orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(PeopleRules.MAX_NAME)) }
    fun setRelation(r: Relation) = _state.update { it.copy(relation = r) }
    fun setColor(i: Int) = _state.update { it.copy(colorIndex = i) }
    fun setRing(on: Boolean) = _state.update { it.copy(ring = on) }
    fun setBirthYear(v: String) = _state.update { it.copy(birthYear = v.filter { ch -> ch.isDigit() }.take(4), birthYearError = false) }
    fun askRemove() = _state.update { it.copy(showRemove = true) }
    fun keep() = _state.update { it.copy(showRemove = false) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val name = PeopleRules.cleanName(s.name)
        val year = PeopleRules.parseBirthYear(s.birthYear, LocalDate.now().year)
        if (s.isMe && year.isFailure) { _state.update { it.copy(birthYearError = true) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result: PersonResult
            when (mode) {
                PersonMode.ADD -> {
                    repo.addPerson(Person(name = name, relation = s.relation, colorIndex = s.colorIndex, ring = s.ring))
                    result = PersonResult.Added(name)
                }
                PersonMode.EDIT -> {
                    original?.let { repo.updatePerson(it.copy(name = name, relation = s.relation, colorIndex = s.colorIndex, ring = s.ring)) }
                    result = PersonResult.Saved
                }
                PersonMode.PROFILE -> {
                    original?.let { repo.updatePerson(it.copy(name = name, birthYear = year.getOrNull())) }
                    result = PersonResult.Saved
                }
            }
            runCatching { onChanged() }
            _state.update { it.copy(saving = false, done = result) }
        }
    }

    fun remove() {
        val p = original ?: return
        _state.update { it.copy(showRemove = false, saving = true) }
        viewModelScope.launch {
            repo.removePerson(p)
            runCatching { onChanged() }
            _state.update { it.copy(saving = false, done = PersonResult.Removed(p.name)) }
        }
    }
}
