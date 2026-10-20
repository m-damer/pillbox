package com.pillbox

import android.app.Application
import com.pillbox.data.PillBoxDatabase
import com.pillbox.data.PillBoxRepository
import com.pillbox.data.RefillSettingsStore
import com.pillbox.reminder.Reminders

/** Tiny manual "container": one database, one repository and one reminder engine for the whole app. */
class PillBoxApp : Application() {
    val repository: PillBoxRepository by lazy { PillBoxRepository(PillBoxDatabase.create(this)) }
    val refillSettings: RefillSettingsStore by lazy { RefillSettingsStore(this) }
    val reminders: Reminders by lazy { Reminders(this, repository, refillSettings) }

    override fun onCreate() {
        super.onCreate()
        reminders.createChannels()
    }
}
