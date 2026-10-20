package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.DisplaySettings
import com.pillbox.domain.TextSize
import com.pillbox.domain.ThemeChoice
import com.pillbox.domain.TimeFormat
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.displayStore by preferencesDataStore(name = "display")

/** Language, numbers, time format, week start, theme, text size and contrast. On this phone only (DataStore). */
class DisplaySettingsStore(private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val digits = stringPreferencesKey("digits")
    private val timeFormat = stringPreferencesKey("time_format")
    private val firstDay = intPreferencesKey("first_day")
    private val theme = stringPreferencesKey("theme")
    private val textSize = stringPreferencesKey("text_size")
    private val highContrast = booleanPreferencesKey("high_contrast")
    private val simpleMode = booleanPreferencesKey("simple_mode")
    private val simpleOnlyTabs = booleanPreferencesKey("simple_only_tabs")
    private val simpleLargeText = booleanPreferencesKey("simple_large_text")

    val flow: Flow<DisplaySettings> = context.displayStore.data.map(::read)

    suspend fun current(): DisplaySettings = flow.first()

    suspend fun update(change: (DisplaySettings) -> DisplaySettings) {
        context.displayStore.edit { p -> write(p, change(read(p))) }
    }

    private fun read(p: Preferences) = DisplaySettings(
        language = p[language]?.takeIf { it in DisplaySettings.LANGUAGES },
        arabicDigits = when (p[digits]) { "arabic" -> true; "western" -> false; else -> null },
        timeFormat = enumOr(p[timeFormat], TimeFormat.PHONE),
        firstDay = p[firstDay]?.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) },
        theme = enumOr(p[theme], ThemeChoice.SYSTEM),
        textSize = enumOr(p[textSize], TextSize.STANDARD),
        highContrast = p[highContrast] ?: false,
        simpleMode = p[simpleMode] ?: false,
        simpleOnlyTabs = p[simpleOnlyTabs] ?: true,
        simpleLargeText = p[simpleLargeText] ?: true,
    )

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: DisplaySettings) {
        if (s.language == null) p.remove(language) else p[language] = s.language
        when (s.arabicDigits) { true -> p[digits] = "arabic"; false -> p[digits] = "western"; null -> p.remove(digits) }
        p[timeFormat] = s.timeFormat.name
        if (s.firstDay == null) p.remove(firstDay) else p[firstDay] = s.firstDay.value
        p[theme] = s.theme.name
        p[textSize] = s.textSize.name
        p[highContrast] = s.highContrast
        p[simpleMode] = s.simpleMode
        p[simpleOnlyTabs] = s.simpleOnlyTabs
        p[simpleLargeText] = s.simpleLargeText
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
