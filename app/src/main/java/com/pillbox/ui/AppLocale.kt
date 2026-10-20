package com.pillbox.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language. On Android 13 and newer the system keeps it (so it also shows in the phone's per-app language
 * settings) and recreates the screens. On older phones the choice is saved by the app and applied here.
 */
object AppLocale {
    /** Wraps [base] so strings and layout direction use the chosen language. Does nothing when none is chosen or the system holds it. */
    fun wrap(base: Context): Context {
        val code = DisplayState.current.language ?: return base
        if (Build.VERSION.SDK_INT >= 33) return base
        val locale = Locale.forLanguageTag(code)
        if (base.resources.configuration.locales[0].language == code) return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Applies [code] to the whole app. The caller recreates the visible screen on phones older than Android 13. */
    fun set(context: Context, code: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(code)
        }
    }

    /** The language the app is showing now ("en" or "ar"). */
    fun active(context: Context): String = com.pillbox.domain.DisplaySettings.supported(context.resources.configuration.locales[0].language)
}
