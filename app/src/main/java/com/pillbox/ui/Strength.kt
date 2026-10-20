package com.pillbox.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pillbox.R

private val StrengthPattern = Regex("""^\s*([0-9]+(?:[.,][0-9]+)?)\s*(mg|g|ml)\s*$""", RegexOption.IGNORE_CASE)

/**
 * Strength is stored as text like "850 mg". This shows it in the app language:
 * Arabic digits and unit ("٨٥٠ ملغ") in Arabic, as typed otherwise. Anything that is not "number unit" is shown as it is.
 */
fun localizedStrength(strength: String?, res: Resources): String? {
    if (strength.isNullOrBlank()) return null
    val match = StrengthPattern.matchEntire(strength) ?: return strength
    val number = match.groupValues[1]
    val unit = when (match.groupValues[2].lowercase()) {
        "mg" -> res.getString(R.string.unit_mg)
        "g" -> res.getString(R.string.unit_g)
        else -> res.getString(R.string.unit_ml)
    }
    val arabic = res.configuration.locales[0].language == "ar"
    val digits = if (arabic) number.map { if (it in '0'..'9') '٠' + (it - '0') else if (it == '.' || it == ',') '٫' else it }.joinToString("") else number
    return "$digits $unit"
}

@Composable
fun strengthLabel(strength: String?): String? = localizedStrength(strength, LocalContext.current.resources)
