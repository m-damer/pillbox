package com.pillbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.DisplaySettingsStore
import com.pillbox.data.PillBoxRepository
import com.pillbox.data.RefillSettingsStore
import com.pillbox.data.ReminderSettingsStore
import com.pillbox.domain.MealTimes
import com.pillbox.domain.QuietHours
import com.pillbox.domain.ReminderSettings
import com.pillbox.domain.Tone
import com.pillbox.domain.DisplaySettings
import com.pillbox.domain.TextSize
import com.pillbox.domain.ThemeChoice
import com.pillbox.domain.TimeFormat
import com.pillbox.ui.DisplayState
import java.time.DayOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loaded: Boolean = false,
    val display: DisplaySettings = DisplaySettings(),
    val refillDays: Int = com.pillbox.domain.RefillSettings.DEFAULT_DAYS,
    val people: Int = 1,
    val reminder: ReminderSettings = ReminderSettings(),
)

/** What the Language and numbers screen saves in one go. */
data class LanguageChoice(val language: String, val arabicDigits: Boolean, val timeFormat: TimeFormat, val firstDay: DayOfWeek)

class SettingsViewModel(
    private val display: DisplaySettingsStore,
    refill: RefillSettingsStore,
    repo: PillBoxRepository,
    private val reminder: ReminderSettingsStore,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(display.flow, refill.flow, repo.observePeople(), reminder.flow) { d, r, people, rem ->
        SettingsUiState(true, d, r.daysBefore, people.size.coerceAtLeast(1), rem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTextSize(size: TextSize) = change { it.copy(textSize = size) }
    fun setHighContrast(on: Boolean) = change { it.copy(highContrast = on) }
    fun setTheme(theme: ThemeChoice) = change { it.copy(theme = theme) }

    fun setTone(tone: Tone) = remind { it.copy(tone = tone) }
    fun setVibration(on: Boolean) = remind { it.copy(vibration = on) }
    fun setRepeat(minutes: Int) = remind { it.copy(repeatMinutes = minutes) }
    fun setKeepRinging(on: Boolean) = remind { it.copy(keepRinging = on) }
    fun saveQuiet(q: QuietHours, onSaved: () -> Unit) = remind({ it.copy(quiet = q) }, onSaved)
    fun saveMeals(m: MealTimes, onSaved: () -> Unit) = remind({ it.copy(meals = m) }, onSaved)

    private fun remind(f: (ReminderSettings) -> ReminderSettings, onDone: () -> Unit = {}) {
        viewModelScope.launch { reminder.update(f); onDone() }
    }

    private fun change(f: (DisplaySettings) -> DisplaySettings) {
        viewModelScope.launch { display.update(f) }
    }

    /**
     * Saves language, numbers, time format and week start. Digits are only stored when they differ from the language's own
     * (so choosing Arabic later brings Arabic-Indic digits along). [onSaved] runs once the new values are readable everywhere.
     */
    fun saveLanguage(choice: LanguageChoice, onSaved: () -> Unit) {
        viewModelScope.launch {
            display.update {
                it.copy(
                    language = choice.language,
                    arabicDigits = if (choice.arabicDigits == (choice.language == "ar")) null else choice.arabicDigits,
                    timeFormat = choice.timeFormat,
                    firstDay = choice.firstDay,
                )
            }
            DisplayState.current = display.current()
            onSaved()
        }
    }
}
