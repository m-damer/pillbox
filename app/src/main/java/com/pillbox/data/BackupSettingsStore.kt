package com.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pillbox.domain.BackupFrequency
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.DriveProblem
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupPrefs by preferencesDataStore(name = "backup_prefs")

/** Where the weekly backup goes and how the last try went. On this phone only. */
class BackupSettingsStore(private val context: Context) {
    private val auto = booleanPreferencesKey("auto_folder")
    private val frequency = stringPreferencesKey("frequency")
    private val notify = booleanPreferencesKey("notify")
    private val uri = stringPreferencesKey("folder_uri")
    private val label = stringPreferencesKey("folder_label")
    private val success = stringPreferencesKey("last_success")
    private val problem = stringPreferencesKey("last_problem")
    private val problemAt = stringPreferencesKey("last_problem_at")
    private val driveOn = booleanPreferencesKey("drive_on")
    private val driveEmail = stringPreferencesKey("drive_email")
    private val driveProblem = stringPreferencesKey("drive_problem")
    private val driveProblemAt = stringPreferencesKey("drive_problem_at")
    private val lastFolder = booleanPreferencesKey("last_to_folder")
    private val lastDrive = booleanPreferencesKey("last_to_drive")

    val flow: Flow<BackupSettings> = context.backupPrefs.data.map(::read)

    suspend fun current(): BackupSettings = flow.first()

    suspend fun update(change: (BackupSettings) -> BackupSettings) {
        context.backupPrefs.edit { p -> write(p, change(read(p))) }
    }

    suspend fun clear() { context.backupPrefs.edit { it.clear() } }

    private fun time(s: String?) = s?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    private fun read(p: Preferences) = BackupSettings(
        autoFolder = p[auto] ?: false,
        frequency = BackupFrequency.entries.firstOrNull { it.name == p[frequency] } ?: BackupFrequency.WEEKLY,
        notify = p[notify] ?: true,
        folderUri = p[uri],
        folderLabel = p[label],
        lastSuccessAt = time(p[success]),
        lastProblem = p[problem]?.let { n -> BackupProblem.entries.firstOrNull { it.name == n } },
        lastProblemAt = time(p[problemAt]),
        driveOn = p[driveOn] ?: false,
        driveEmail = p[driveEmail],
        driveProblem = p[driveProblem]?.let { n -> DriveProblem.entries.firstOrNull { it.name == n } },
        driveProblemAt = time(p[driveProblemAt]),
        lastToFolder = p[lastFolder] ?: true,
        lastToDrive = p[lastDrive] ?: false,
    )

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: BackupSettings) {
        p[auto] = s.autoFolder
        p[frequency] = s.frequency.name
        p[notify] = s.notify
        s.folderUri?.let { p[uri] = it } ?: p.remove(uri)
        s.folderLabel?.let { p[label] = it } ?: p.remove(label)
        s.lastSuccessAt?.let { p[success] = it.toString() } ?: p.remove(success)
        s.lastProblem?.let { p[problem] = it.name } ?: p.remove(problem)
        s.lastProblemAt?.let { p[problemAt] = it.toString() } ?: p.remove(problemAt)
        p[driveOn] = s.driveOn
        s.driveEmail?.let { p[driveEmail] = it } ?: p.remove(driveEmail)
        s.driveProblem?.let { p[driveProblem] = it.name } ?: p.remove(driveProblem)
        s.driveProblemAt?.let { p[driveProblemAt] = it.toString() } ?: p.remove(driveProblemAt)
        p[lastFolder] = s.lastToFolder
        p[lastDrive] = s.lastToDrive
    }
}
