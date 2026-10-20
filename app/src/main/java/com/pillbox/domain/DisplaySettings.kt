package com.pillbox.domain

import java.time.DayOfWeek

enum class TimeFormat { PHONE, H12, H24 }

/** Text size on top of the phone's own font size. The app never goes past twice normal size. */
enum class TextSize(val scale: Float) { STANDARD(1f), LARGE(1.15f), LARGEST(1.3f) }

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * How the app looks and reads, chosen in Settings. Null means "follow the language or the phone".
 * Pure Kotlin so the rules can be unit tested without Android.
 */
data class DisplaySettings(
    /** "en" or "ar"; null follows the phone. */
    val language: String? = null,
    /** True for Arabic-Indic digits, false for 0-9; null picks by language. */
    val arabicDigits: Boolean? = null,
    val timeFormat: TimeFormat = TimeFormat.PHONE,
    /** First day of the week; null follows the language. */
    val firstDay: DayOfWeek? = null,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    /** Simple mode: fewer places to go and bigger text. [simpleOnlyTabs] and [simpleLargeText] are its two parts. */
    val simpleMode: Boolean = false,
    val simpleOnlyTabs: Boolean = true,
    val simpleLargeText: Boolean = true,
) {
    /** Text size in use: Simple mode with extra large text overrides the chosen size. */
    val effectiveTextSize: TextSize get() = if (simpleMode && simpleLargeText) TextSize.LARGEST else textSize

    /** True when the bottom bar shows only Today and Settings. */
    val onlyTodayAndSettings: Boolean get() = simpleMode && simpleOnlyTabs

    fun useArabicDigits(activeLanguage: String): Boolean = arabicDigits ?: (activeLanguage == "ar")

    fun is24Hour(phoneIs24: Boolean): Boolean = when (timeFormat) {
        TimeFormat.PHONE -> phoneIs24
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    fun firstDay(languageDefault: DayOfWeek): DayOfWeek = firstDay ?: languageDefault

    /** The font scale to use: the phone's scale times the app's own, never above [MAX_FONT_SCALE]. */
    fun fontScale(systemScale: Float): Float = maxOf(systemScale, minOf(systemScale * effectiveTextSize.scale, MAX_FONT_SCALE))

    companion object {
        const val MAX_FONT_SCALE = 2f
        val LANGUAGES = listOf("en", "ar")
        /** Only these three make sense as the first day of the week in the languages the app speaks. */
        val WEEK_STARTS = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

        /** The language to show for a phone language: Arabic or English. */
        fun supported(code: String?): String = if (code == "ar") "ar" else "en"
    }
}
