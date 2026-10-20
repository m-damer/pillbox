package com.pillbox

import android.content.Context
import androidx.activity.ComponentActivity
import com.pillbox.ui.AppLocale

/** Base for every screen of the app: applies the language chosen in Settings (on phones older than Android 13). */
open class PillBoxActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
}
