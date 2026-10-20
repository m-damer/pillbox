package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.RefillSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Refill alert settings, kept on the phone only (DataStore). */
class RefillSettingsStore(private val context: Context) {
    private val days = intPreferencesKey("refill_days")
    private val minuteOfDay = intPreferencesKey("refill_minute_of_day")
    private val people = booleanPreferencesKey("refill_include_people")

    val flow: Flow<RefillSettings> = context.settingsStore.data.map { p ->
        val default = RefillSettings()
        RefillSettings(
            daysBefore = RefillSettings.clampDays(p[days] ?: default.daysBefore),
            time = (p[minuteOfDay] ?: (default.time.hour * 60 + default.time.minute)).coerceIn(0, 24 * 60 - 1).let { LocalTime.of(it / 60, it % 60) },
            includePeople = p[people] ?: default.includePeople,
        )
    }

    suspend fun current(): RefillSettings = flow.first()

    suspend fun save(s: RefillSettings) {
        context.settingsStore.edit { p ->
            p[days] = RefillSettings.clampDays(s.daysBefore)
            p[minuteOfDay] = s.time.hour * 60 + s.time.minute
            p[people] = s.includePeople
        }
    }
}
