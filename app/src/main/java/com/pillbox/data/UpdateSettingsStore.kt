package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.UpdateInfo
import com.pillbox.domain.UpdateSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updatePrefs by preferencesDataStore(name = "update_prefs")

/** The "tell me about new versions" choice and the last version found. On this phone only. */
class UpdateSettingsStore(private val context: Context) {
    private val enabled = booleanPreferencesKey("enabled")
    private val lastCheck = longPreferencesKey("last_check")
    private val lastNotified = intPreferencesKey("last_notified")
    private val name = stringPreferencesKey("v_name")
    private val code = intPreferencesKey("v_code")
    private val notes = stringPreferencesKey("v_notes")
    private val apk = stringPreferencesKey("v_apk")
    private val sha = stringPreferencesKey("v_sha")
    private val size = longPreferencesKey("v_size")

    val flow: Flow<UpdateSettings> = context.updatePrefs.data.map(::read)

    suspend fun current(): UpdateSettings = flow.first()

    suspend fun update(change: (UpdateSettings) -> UpdateSettings) {
        context.updatePrefs.edit { p -> write(p, change(read(p))) }
    }

    suspend fun clear() { context.updatePrefs.edit { it.clear() } }

    private fun read(p: Preferences): UpdateSettings {
        val v = p[code]?.let { c ->
            UpdateInfo(p[name] ?: "", c, p[notes] ?: "", p[apk] ?: return@let null, p[sha], p[size] ?: 0L)
        }
        return UpdateSettings(enabled = p[enabled] ?: true, lastCheckMillis = p[lastCheck] ?: 0L, lastNotifiedCode = p[lastNotified] ?: 0, available = v)
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: UpdateSettings) {
        p[enabled] = s.enabled
        p[lastCheck] = s.lastCheckMillis
        p[lastNotified] = s.lastNotifiedCode
        val v = s.available
        if (v == null) { p.remove(name); p.remove(code); p.remove(notes); p.remove(apk); p.remove(sha); p.remove(size) } else {
            p[name] = v.versionName; p[code] = v.versionCode; p[notes] = v.notes; p[apk] = v.apkUrl
            v.sha256Url?.let { p[sha] = it } ?: p.remove(sha)
            p[size] = v.sizeBytes
        }
    }
}
