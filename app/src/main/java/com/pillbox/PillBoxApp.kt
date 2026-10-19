package com.pillbox

import android.app.Application
import com.pillbox.data.PillBoxDatabase
import com.pillbox.data.PillBoxRepository

/** Tiny manual "container": one database and one repository for the whole app. */
class PillBoxApp : Application() {
    val repository: PillBoxRepository by lazy { PillBoxRepository(PillBoxDatabase.create(this)) }
}
