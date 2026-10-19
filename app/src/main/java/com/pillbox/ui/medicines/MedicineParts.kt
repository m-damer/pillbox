package com.pillbox.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.DayPattern
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Schedule
import com.pillbox.domain.StockLevel
import com.pillbox.ui.Formatter
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.today.PillIcon
import com.pillbox.ui.today.foodText
import com.pillbox.ui.unitPlural
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

/** The round badge with the medicine's shape. [muted] is for finished medicines. */
@Composable
fun MedicineBadge(unit: DoseUnit, modifier: Modifier = Modifier, size: Dp = 48.dp, muted: Boolean = false) {
    val c = PillBox.colors
    Box(
        modifier.size(size).background(if (muted) c.divider else c.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) { PillIcon(unit, if (muted) c.textMuted else c.accent) }
}

/** "850 mg" style strength is stored as text; this is "1 tablet", "0.5 tablets". */
@Composable
fun doseText(m: Medicine, fmt: Formatter): String =
    fmt.amount(m.doseAmount) + " " + pluralStringResource(unitPlural(m.unit), quantityFor(m.doseAmount))

/** "8 tablets" for a stock amount. */
@Composable
fun stockAmountText(m: Medicine, fmt: Formatter): String =
    fmt.amount(m.stock) + " " + pluralStringResource(unitPlural(m.unit), quantityFor(m.stock))

/** "ongoing" or "7 days". */
@Composable
fun lengthText(schedule: Schedule?, fmt: Formatter): String {
    val end = schedule?.end ?: return stringResource(R.string.meds_ongoing)
    val days = (ChronoUnit.DAYS.between(schedule.start, end) + 1).toInt().coerceAtLeast(1)
    return pluralStringResource(R.plurals.n_days, days, fmt.number(days))
}

@Composable
fun levelLabel(level: StockLevel): String = stringResource(
    when (level) {
        StockLevel.ENOUGH -> R.string.stock_enough
        StockLevel.BUY_SOON -> R.string.stock_buy_soon
        StockLevel.OUT -> R.string.stock_out
    },
)

@Composable
fun levelColor(level: StockLevel) = when (level) {
    StockLevel.ENOUGH -> PillBox.colors.success
    StockLevel.BUY_SOON -> PillBox.colors.warning
    StockLevel.OUT -> PillBox.colors.danger
}

/** "Twice a day", "Every 6 hours", "Only when needed", plus the days and the food rule. */
@Composable
fun scheduleSummary(schedule: Schedule, fmt: Formatter): String {
    val sep = stringResource(R.string.dot_sep)
    val parts = mutableListOf<String>()
    val days: DayPattern? = when (val f = schedule.frequency) {
        is Frequency.FixedTimes -> {
            parts += stringResource(
                when (f.times.size) { 1 -> R.string.freq_once; 2 -> R.string.freq_twice; 3 -> R.string.freq_three; else -> R.string.freq_hours },
            )
            f.days
        }
        is Frequency.EveryNHours -> {
            parts += stringResource(R.string.sched_every_hours, pluralStringResource(R.plurals.n_hours, f.hours, fmt.number(f.hours)))
            f.days
        }
        is Frequency.AsNeeded -> { parts += stringResource(R.string.freq_prn); null }
    }
    when (days) {
        is DayPattern.DaysOfWeek -> parts += DayOfWeek.values().filter { it in days.days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        is DayPattern.EveryNDays -> parts += pluralStringResource(R.plurals.every_n_days, days.n, fmt.number(days.n))
        else -> Unit
    }
    foodText(schedule.withFood)?.let { parts += it }
    return parts.joinToString(sep)
}
