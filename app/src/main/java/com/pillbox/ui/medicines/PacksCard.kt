package com.pillbox.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.Medicine
import com.pillbox.domain.PackMath
import com.pillbox.domain.PackState
import com.pillbox.domain.Schedule
import com.pillbox.domain.StockMath
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.DesignIcons
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.stringResourceIso
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth

/**
 * The boxes of a medicine, in the order they are used, each with its own expiry month. The person chooses which one is in use.
 * Shown only when the medicine tracks boxes (an expiry date was given when stock was added).
 */
@Composable
fun PacksCard(
    medicine: Medicine,
    schedule: Schedule?,
    fmt: Formatter,
    onUse: (Int) -> Unit,
    onExpiry: (Int, YearMonth?) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    if (medicine.packs.isEmpty()) return
    val c = PillBox.colors
    val all = PackMath.states(medicine.packs, medicine.stock)
    // Finished boxes are hidden; if every box is finished the last one stays so the card is not empty.
    val states = all.filter { !it.finished }.ifEmpty { all.takeLast(1) }
    val perDay = schedule?.let { s -> StockMath.averageDosesPerDay(s)?.times(medicine.doseAmount) }
    val outlasts = PackMath.currentOutlastsNext(medicine.packs, medicine.stock, perDay, today)
    Column(modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Text(stringResource(R.string.packs_title), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.packs_hint), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        if (outlasts) {
            Row(
                Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                Icon(DesignIcons.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.packs_outlasts), style = MaterialTheme.typography.bodyMedium, color = c.warning, modifier = Modifier.weight(1f))
            }
        }
        states.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = c.divider)
            PackRow(medicine, s, fmt, today, onUse, onExpiry)
        }
    }
}

@Composable
private fun PackRow(m: Medicine, s: PackState, fmt: Formatter, today: LocalDate, onUse: (Int) -> Unit, onExpiry: (Int, YearMonth?) -> Unit) {
    val c = PillBox.colors
    var picking by remember { mutableStateOf(false) }
    val name = stringResourceIso(R.string.packs_name, fmt.number(s.pack.id))
    val date = s.pack.expiry?.let { expiryMonthText(it, fmt) }
    val dateLine = if (date != null) stringResourceIso(R.string.packs_expires, date) else stringResource(R.string.packs_no_date)
    val left = stringResourceIso(R.string.packs_left, fmt.amount(s.remaining))
    val info = com.pillbox.domain.ExpiryRules.info(s.pack.expiry, today)
    val late = info.level == com.pillbox.domain.ExpiryLevel.EXPIRED || info.level == com.pillbox.domain.ExpiryLevel.SOON
    Column(Modifier.fillMaxWidth().padding(vertical = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            if (s.inUse) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
                    Icon(DesignIcons.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.packs_in_use), style = MaterialTheme.typography.labelLarge, color = c.success)
                }
            } else if (s.finished) {
                Text(stringResource(R.string.packs_finished), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            }
        }
        Text(
            listOf(dateLine, left).joinToString(stringResource(R.string.dot_sep)),
            style = MaterialTheme.typography.bodyMedium, color = if (late) c.danger else c.textMuted,
        )
        if (late && !s.finished) {
            Text(expiryLineText(s.pack.expiry, info, fmt) ?: "", style = MaterialTheme.typography.bodySmall, color = c.danger)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(if (date == null) R.string.expiry_add else R.string.expiry_change), style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
            if (date != null) TextButton(onClick = { onExpiry(s.pack.id, null) }, modifier = Modifier.heightIn(min = Dimens.TouchTargetMin)) {
                Text(stringResource(R.string.expiry_remove), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
        }
        if (!s.inUse && !s.finished) OutlineButton(stringResourceIso(R.string.packs_use_now, name), { onUse(s.pack.id) })
    }
    if (picking) ExpiryPickerDialog(s.pack.expiry, today, onDismiss = { picking = false }) { onExpiry(s.pack.id, it); picking = false }
}
