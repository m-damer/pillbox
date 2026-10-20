package com.pillbox.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.util.Locale
import kotlin.math.floor

/**
 * The one place that turns numbers and times into text, so digits and 12/24-hour follow the settings.
 * Settings screen (M8) will feed [arabicDigits] and [is24Hour]; until then Arabic UI uses Arabic-Indic digits
 * and the time format follows the phone.
 */
class Formatter(val arabicDigits: Boolean, val is24Hour: Boolean, private val am: String, private val pm: String, private val rtl: Boolean) {

    fun digits(text: String): String {
        if (!arabicDigits) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '0'..'9' -> '٠' + (ch - '0')
                    '.' -> '٫'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    fun number(n: Int): String = digits(n.toString())

    /** Reads digits typed in either numeral system. Empty or too long gives null. */
    fun parseInt(text: String): Int? {
        val ascii = text.map { if (it in '\u0660'..'\u0669') '0' + (it - '\u0660') else it }.filter { it in '0'..'9' }
        return ascii.joinToString("").take(5).toIntOrNull()
    }

    /** "a, b and c" (or the Arabic equivalent). */
    fun list(items: List<String>, separator: String, and: String): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(separator) + and + items.last()
    }

    /** 1, 0.5, 2.5: no trailing ".0". */
    fun amount(v: Double): String =
        digits(if (v == floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v))

    /** Whole hours for the day bar: "12 AM", "6 PM", or "06:00" in 24-hour time. */
    fun hour(h: Int): String {
        val hh = h % 24
        val text = if (is24Hour) digits(hh.toString().padStart(2, '0') + ":00")
        else digits((if (hh % 12 == 0) 12 else hh % 12).toString()) + " " + (if (hh < 12) am else pm)
        return if (rtl) "⁧$text⁩" else "⁦$text⁩"
    }

    /** "8:00 AM", "20:00", and in Arabic digits then ص or م (the letter ends up on the left in RTL). */
    fun time(t: LocalTime): String {
        val minute = t.minute.toString().padStart(2, '0')
        val text = if (is24Hour) {
            digits("${t.hour.toString().padStart(2, '0')}:$minute")
        } else {
            val h = if (t.hour % 12 == 0) 12 else t.hour % 12
            digits("$h:$minute") + " " + (if (t.hour < 12) am else pm)
        }
        // Isolate so the AM/PM mark stays next to its time inside any surrounding sentence.
        return if (rtl) "⁧$text⁩" else "⁦$text⁩"
    }

    companion object {
        /** The formatter for the current language and clock setting, for code outside Compose (notifications). */
        fun from(context: android.content.Context): Formatter {
            val locale = context.resources.configuration.locales[0]
            val res = context.resources
            return Formatter(
                arabicDigits = locale.language == "ar",
                is24Hour = DateFormat.is24HourFormat(context),
                am = res.getString(com.pillbox.R.string.time_am),
                pm = res.getString(com.pillbox.R.string.time_pm),
                rtl = android.text.TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL,
            )
        }
    }
}

@Composable
fun rememberFormatter(): Formatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val arabic = locale.language == "ar"
    val is24 = DateFormat.is24HourFormat(context)
    val res = context.resources
    return remember(arabic, is24, locale) {
        Formatter(
            arabicDigits = arabic,
            is24Hour = is24,
            am = res.getString(com.pillbox.R.string.time_am),
            pm = res.getString(com.pillbox.R.string.time_pm),
            rtl = android.text.TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL,
        )
    }
}
