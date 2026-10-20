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
import com.pillbox.domain.WithFood
import com.pillbox.ui.Formatter
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.illustration
import com.pillbox.ui.today.PillIcon
import com.pillbox.ui.today.foodText
import com.pillbox.ui.unitPlural
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

/** The medicine's shape. [muted] is for finished medicines. */
@Composable
fun MedicineBadge(unit: DoseUnit, modifier: Modifier = Modifier, size: Dp = 36.dp, muted: Boolean = false) {
    Box(modifier.size(maxOf(size, 36.dp)), contentAlignment = Alignment.Center) {
        PillIcon(unit, if (muted) PillBox.illustration.paused else null, size)
    }
}

/** Two bars. The core icon set has no pause symbol. */
@Composable
fun PauseGlyph(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.width / 8f
        for (x in listOf(0.375f, 0.625f)) {
            drawLine(
                color, androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.2f),
                androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.8f),
                strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
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
fun scheduleSummary(schedule: Schedule, fmt: Formatter): String =
    scheduleSummaryText(androidx.compose.ui.platform.LocalContext.current.resources, schedule, fmt)

/** Same text outside Compose, for the doctor report. */
fun scheduleSummaryText(res: android.content.res.Resources, schedule: Schedule, fmt: Formatter): String {
    val sep = res.getString(R.string.dot_sep)
    val parts = mutableListOf<String>()
    val days: DayPattern? = when (val f = schedule.frequency) {
        is Frequency.FixedTimes -> {
            parts += res.getString(
                when (f.times.size) { 1 -> R.string.freq_once; 2 -> R.string.freq_twice; 3 -> R.string.freq_three; else -> R.string.freq_hours },
            )
            f.days
        }
        is Frequency.EveryNHours -> {
            parts += res.getString(R.string.sched_every_hours, res.getQuantityString(R.plurals.n_hours, f.hours, fmt.number(f.hours)))
            f.days
        }
        is Frequency.AsNeeded -> { parts += res.getString(R.string.freq_prn); null }
    }
    when (days) {
        is DayPattern.DaysOfWeek -> parts += DayOfWeek.values().filter { it in days.days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        is DayPattern.EveryNDays -> parts += res.getQuantityString(R.plurals.every_n_days, days.n, fmt.number(days.n))
        else -> Unit
    }
    when (schedule.withFood) {
        WithFood.ANY -> Unit
        WithFood.BEFORE_MEAL -> parts += res.getString(R.string.food_line_before)
        WithFood.WITH_MEAL -> parts += res.getString(R.string.food_line_with)
        WithFood.AFTER_MEAL -> parts += res.getString(R.string.food_line_after)
    }
    return parts.joinToString(sep)
}
