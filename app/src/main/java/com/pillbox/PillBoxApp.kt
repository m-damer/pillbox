package com.pillbox

import android.app.Application
import com.pillbox.backup.BackupManager
import com.pillbox.data.BackupSettingsStore
import com.pillbox.data.DisplaySettingsStore
import com.pillbox.data.PillBoxDatabase
import com.pillbox.data.PillBoxRepository
import com.pillbox.data.ReminderSettingsStore
import com.pillbox.data.RefillSettingsStore
import com.pillbox.reminder.Reminders
import com.pillbox.ui.DisplayState
import com.pillbox.ui.ReminderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Tiny manual "container": one database, one repository and one reminder engine for the whole app. */
class PillBoxApp : Application() {
    /** Saves a line for the notifications screen. Never throws: the notification itself matters more. */
    fun recordNotice(n: com.pillbox.domain.Notice) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { notices.record(n) } }
    }

    val repository: PillBoxRepository by lazy { PillBoxRepository(PillBoxDatabase.create(this)) }
    val refillSettings: RefillSettingsStore by lazy { RefillSettingsStore(this) }
    val displaySettings: DisplaySettingsStore by lazy { DisplaySettingsStore(this) }
    val reminderSettings: ReminderSettingsStore by lazy { ReminderSettingsStore(this) }
    val widgetSettings: com.pillbox.data.WidgetSettingsStore by lazy { com.pillbox.data.WidgetSettingsStore(this) }
    val backups: BackupManager by lazy { BackupManager(this, repository, BackupSettingsStore(this)) }
    val updates: com.pillbox.update.UpdateManager by lazy { com.pillbox.update.UpdateManager(this, com.pillbox.data.UpdateSettingsStore(this)) }
    val notices: com.pillbox.data.NoticeStore by lazy { com.pillbox.data.NoticeStore(this) }
    val reminders: Reminders by lazy { Reminders(this, repository, refillSettings) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // A small read, done once before any screen exists, so the first frame already has the right language and digits.
        DisplayState.current = runBlocking { displaySettings.flow.first() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { displaySettings.flow.collect { DisplayState.current = it } }
        ReminderState.current = runBlocking { reminderSettings.flow.first() }
        reminders.createChannels()
        // The weekly job survives restarts; this only makes sure it exists (KEEP leaves a running one alone).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { if (backups.wantsAutomatic()) backups.schedule() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { updates.reconcile() } }
        // A new tone or vibration choice needs its own channel; everything else is read from ReminderState when it rings.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            reminderSettings.flow.collect { ReminderState.current = it; reminders.createChannels() }
        }
    }
}
