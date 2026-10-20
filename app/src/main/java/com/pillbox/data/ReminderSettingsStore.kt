package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.LateSetting
import com.pillbox.domain.LateThreshold
import com.pillbox.domain.MealTimes
import com.pillbox.domain.QuietHours
import com.pillbox.domain.ReminderSettings
import com.pillbox.domain.Tone
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderPrefs by preferencesDataStore(name = "reminder_prefs")

/** Sound, repeat, quiet hours and meal times. On this phone only (DataStore). */
class ReminderSettingsStore(private val context: Context) {
    private val tone = stringPreferencesKey("tone")
    private val vibration = booleanPreferencesKey("vibration")
    private val repeat = intPreferencesKey("repeat_minutes")
    private val keepRinging = booleanPreferencesKey("keep_ringing")
    private val quietOn = booleanPreferencesKey("quiet_on")
    private val quietFrom = intPreferencesKey("quiet_from")
    private val quietUntil = intPreferencesKey("quiet_until")
    private val quietDoseRings = booleanPreferencesKey("quiet_dose_rings")
    private val quietMuteRefill = booleanPreferencesKey("quiet_mute_refill")
    private val lateSetting = stringPreferencesKey("late_setting")
    private val lateThreshold = intPreferencesKey("late_threshold")
    private val mealKeys = MealTimes.Meal.entries.associateWith { intPreferencesKey("meal_" + it.name.lowercase()) }

    val flow: Flow<ReminderSettings> = context.reminderPrefs.data.map(::read)

    suspend fun current(): ReminderSettings = flow.first()

    suspend fun update(change: (ReminderSettings) -> ReminderSettings) {
        context.reminderPrefs.edit { p -> write(p, change(read(p))) }
    }

    private fun minute(t: LocalTime) = t.hour * 60 + t.minute
    private fun time(m: Int): LocalTime = m.coerceIn(0, 24 * 60 - 1).let { LocalTime.of(it / 60, it % 60) }

    private fun read(p: Preferences): ReminderSettings {
        val d = ReminderSettings()
        val meals = MealTimes.Meal.entries.fold(d.meals) { acc, m -> p[mealKeys.getValue(m)]?.let { acc.with(m, time(it)) } ?: acc }
        return ReminderSettings(
            tone = p[tone]?.let { n -> Tone.entries.firstOrNull { it.name == n } } ?: d.tone,
            vibration = p[vibration] ?: d.vibration,
            repeatMinutes = (p[repeat] ?: d.repeatMinutes).takeIf { it in ReminderSettings.REPEAT_CHOICES } ?: d.repeatMinutes,
            keepRinging = p[keepRinging] ?: d.keepRinging,
            quiet = QuietHours(
                enabled = p[quietOn] ?: d.quiet.enabled,
                from = p[quietFrom]?.let(::time) ?: d.quiet.from,
                until = p[quietUntil]?.let(::time) ?: d.quiet.until,
                doseRings = p[quietDoseRings] ?: d.quiet.doseRings,
                muteRefill = p[quietMuteRefill] ?: d.quiet.muteRefill,
            ),
            meals = meals,
            late = p[lateSetting]?.let { n -> LateSetting.entries.firstOrNull { it.name == n } } ?: d.late,
            lateThreshold = LateThreshold.entries.firstOrNull { it.minutes == p[lateThreshold] } ?: d.lateThreshold,
        )
    }

    private fun write(p: MutablePreferences, s: ReminderSettings) {
        p[tone] = s.tone.name
        p[vibration] = s.vibration
        p[repeat] = s.repeatMinutes
        p[keepRinging] = s.keepRinging
        p[quietOn] = s.quiet.enabled
        p[quietFrom] = minute(s.quiet.from)
        p[quietUntil] = minute(s.quiet.until)
        p[quietDoseRings] = s.quiet.doseRings
        p[quietMuteRefill] = s.quiet.muteRefill
        p[lateSetting] = s.late.name
        p[lateThreshold] = s.lateThreshold.minutes
        MealTimes.Meal.entries.forEach { p[mealKeys.getValue(it)] = minute(s.meals[it]) }
    }
}
