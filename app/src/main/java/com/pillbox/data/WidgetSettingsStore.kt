package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.WidgetSettings
import com.pillbox.domain.WidgetSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetPrefs by preferencesDataStore(name = "widget_prefs")

/** What the home screen widget shows. On this phone only. */
class WidgetSettingsStore(private val context: Context) {
    private val size = stringPreferencesKey("size")
    private val person = longPreferencesKey("person")
    private val names = booleanPreferencesKey("names")
    private val takeAll = booleanPreferencesKey("take_all")

    val flow: Flow<WidgetSettings> = context.widgetPrefs.data.map { p ->
        WidgetSettings(
            size = p[size]?.let { s -> WidgetSize.entries.firstOrNull { it.name == s } } ?: WidgetSize.MEDIUM,
            personId = p[person]?.takeIf { it > 0 },
            showNames = p[names] ?: true,
            takeAll = p[takeAll] ?: true,
        )
    }

    suspend fun current(): WidgetSettings = flow.first()

    suspend fun save(s: WidgetSettings) {
        context.widgetPrefs.edit { p ->
            p[size] = s.size.name
            p[person] = s.personId ?: 0L
            p[names] = s.showNames
            p[takeAll] = s.takeAll
        }
    }

    suspend fun clear() { context.widgetPrefs.edit { it.clear() } }
}
