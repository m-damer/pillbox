package com.pillbox.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.ExpiryInfo
import com.pillbox.domain.ExpiryLevel
import com.pillbox.domain.ExpiryRules
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.DesignIcons
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.stringResourceIso
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** "May 2027" in the app's language and digits. */
@Composable
fun expiryMonthText(ym: YearMonth, fmt: Formatter): String =
    fmt.digits(ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.year)

/** The one line about an expiry date: words and an icon, so color is never the only signal. Null when there is no date. */
@Composable
fun expiryLineText(expiry: YearMonth?, info: ExpiryInfo, fmt: Formatter): String? {
    if (expiry == null) return null
    val month = expiryMonthText(expiry, fmt)
    val left = info.daysLeft ?: 0
    return when (info.level) {
        ExpiryLevel.NONE -> null
        ExpiryLevel.OK -> stringResourceIso(R.string.expiry_ok, month)
        ExpiryLevel.SOON ->
            if (left == 0) stringResourceIso(R.string.expiry_today, month)
            else stringResourceIso(R.string.expiry_soon, pluralStringResource(R.plurals.n_days, left, fmt.number(left)), month)
        ExpiryLevel.EXPIRED -> stringResourceIso(R.string.expiry_expired, month)
    }
}

@Composable
fun expiryColor(level: ExpiryLevel) = when (level) {
    ExpiryLevel.EXPIRED -> PillBox.colors.danger
    ExpiryLevel.SOON -> PillBox.colors.warning
    else -> PillBox.colors.textMuted
}

/** A line with an icon: calendar-like info for a valid date, warning for soon or expired. */
@Composable
fun ExpiryLine(expiry: YearMonth?, today: LocalDate, fmt: Formatter, modifier: Modifier = Modifier) {
    val info = ExpiryRules.info(expiry, today)
    val text = expiryLineText(expiry, info, fmt) ?: return
    val color = expiryColor(info.level)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
        Icon(if (info.level == ExpiryLevel.OK) DesignIcons.Info else DesignIcons.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** On the medicine page: a banner for soon and expired, with advice. A far date is only a quiet line. */
@Composable
fun ExpiryBanner(expiry: YearMonth?, today: LocalDate, fmt: Formatter, modifier: Modifier = Modifier) {
    val info = ExpiryRules.info(expiry, today)
    val text = expiryLineText(expiry, info, fmt) ?: return
    val c = PillBox.colors
    if (info.level == ExpiryLevel.OK) { ExpiryLine(expiry, today, fmt, modifier); return }
    val expired = info.level == ExpiryLevel.EXPIRED
    val bg = if (expired) c.dangerContainer else c.warningContainer
    val fg = if (expired) c.danger else c.warning
    Row(
        modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.medium).padding(Space.S16),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Icon(DesignIcons.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
            Text(stringResource(if (expired) R.string.expiry_expired_note else R.string.expiry_soon_note), style = MaterialTheme.typography.bodyMedium, color = fg)
        }
    }
}

/**
 * Pick the printed month and year. Big buttons for the year, one chip per month. Nothing is saved until Save.
 * Months before this month are still allowed, because a box can already be past its date.
 */
@Composable
fun ExpiryPickerDialog(initial: YearMonth?, today: LocalDate, onDismiss: () -> Unit, onConfirm: (YearMonth) -> Unit) {
    val c = PillBox.colors
    val fmt = com.pillbox.ui.rememberFormatter()
    val range = ExpiryRules.yearRange(today)
    val start = initial ?: YearMonth.from(today).plusMonths(12)
    var year by remember { mutableStateOf(start.year.coerceIn(range.first, range.last)) }
    var month by remember { mutableStateOf(start.monthValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(stringResource(R.string.expiry_dialog_title), style = MaterialTheme.typography.titleMedium, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    YearButton("−", stringResource(R.string.expiry_year_less), enabled = year > range.first) { year-- }
                    Text(fmt.digits(year.toString()), style = MaterialTheme.typography.titleLarge, color = c.text)
                    YearButton("+", stringResource(R.string.expiry_year_more), enabled = year < range.last) { year++ }
                }
                com.pillbox.ui.components.ChipGroup {
                    for (m in 1..12) {
                        PBChip(
                            java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()), month == m, { month = m },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(YearMonth.of(year, month)) }, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) { Text(stringResource(R.string.expiry_save), style = MaterialTheme.typography.labelLarge, color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge, color = c.textMuted) } },
    )
}

@Composable
private fun YearButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = PillBox.colors
    Box(
        Modifier
            .size(Dimens.StepperButton)
            .background(if (enabled) c.primaryContainer else c.divider, Dimens.ButtonShape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.titleLarge, color = if (enabled) c.accent else c.textMuted) }
}

/**
 * The optional expiry of a box, used by Add stock and the Packs card: a button to add it, then the month with Change / Remove.
 * [note] is a short line under it that says why it helps.
 */
@Composable
fun OptionalExpiryField(expiry: YearMonth?, fmt: Formatter, note: String?, onChange: (YearMonth?) -> Unit) {
    val c = PillBox.colors
    var picking by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Text(stringResource(R.string.expiry_label), style = MaterialTheme.typography.labelLarge, color = c.text)
        if (expiry == null) {
            com.pillbox.ui.components.OutlineButton(stringResource(R.string.expiry_add), { picking = true })
        } else {
            Row(
                Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).border(Dimens.ChipBorder, c.border, MaterialTheme.shapes.large).padding(Space.S12),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                Text(expiryMonthText(expiry, fmt), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                    Text(stringResource(R.string.expiry_change), style = MaterialTheme.typography.labelLarge, color = c.accent)
                }
                TextButton(onClick = { onChange(null) }, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                    Text(stringResource(R.string.expiry_remove), style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
        }
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
    if (picking) ExpiryPickerDialog(expiry, today, onDismiss = { picking = false }) { onChange(it); picking = false }
}
