package com.pillbox.ui.medicines

import com.pillbox.ui.components.stringResourceIso
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.StockLevel
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.dayMonth
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.illustration
import com.pillbox.ui.theme.Space
import androidx.compose.ui.res.pluralStringResource

/** A toast to show when this screen appears (after Stop or Restart on the detail screen). */
data class ListToast(val title: String, val body: String?)

@Composable
fun MedicinesScreen(
    vm: MedicinesViewModel,
    showFinished: Boolean,
    onShowFinished: (Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onAddMedicine: () -> Unit,
    toast: ListToast?,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
    onRefillAlert: () -> Unit = {},
    people: com.pillbox.ui.people.PeopleBar? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        if (toast != null) {
            snackbar.showToast(toast.title, toast.body)
            onToastShown()
        }
    }
    Box(modifier.fillMaxSize()) {
        MedicinesContent(state, fmt, showFinished, onShowFinished, onOpen, onAddMedicine, onRefillAlert = onRefillAlert, people = people)
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun MedicinesContent(
    state: MedicinesUiState,
    fmt: Formatter,
    showFinished: Boolean,
    onShowFinished: (Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onAddMedicine: () -> Unit,
    modifier: Modifier = Modifier,
    onRefillAlert: () -> Unit = {},
    people: com.pillbox.ui.people.PeopleBar? = null,
) {
    val c = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.medicines_title), style = MaterialTheme.typography.titleLarge, color = c.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    if (state.pausedCount > 0) stringResourceIso(R.string.meds_sub_paused, fmt.number(state.active.size - state.pausedCount), fmt.number(state.pausedCount))
                    else stringResourceIso(R.string.meds_sub, fmt.number(state.active.size)),
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            com.pillbox.ui.people.PeopleBarSwitcher(people)
        }
        Segmented(
            options = listOf(stringResource(R.string.tab_active), stringResource(R.string.tab_finished)),
            selectedIndex = if (showFinished) 1 else 0,
            onSelect = { onShowFinished(it == 1) },
            modifier = Modifier.padding(horizontal = Dimens.ScreenPaddingH),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Space.S12, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            if (!showFinished) {
                items(state.active, key = { it.item.medicine.id }) { row ->
                    if (row.pause != null) PausedCard(row, fmt) { onOpen(row.item.medicine.id) }
                    else ActiveCard(row, fmt) { onOpen(row.item.medicine.id) }
                }
                item { AddMedicineButton(onAddMedicine) }
                item {
                    InfoNote(
                        pluralStringResource(R.plurals.meds_remind_note, state.remindDays, fmt.number(state.remindDays)),
                        com.pillbox.ui.components.DesignIcons.Bell,
                        onClick = onRefillAlert,
                    )
                }
            } else {
                if (state.loaded && state.finished.isEmpty()) {
                    item { Text(stringResource(R.string.meds_finished_empty), style = MaterialTheme.typography.bodyLarge, color = c.textMuted) }
                }
                items(state.finished, key = { it.medicine.id }) { m -> FinishedCard(m, fmt) { onOpen(m.medicine.id) } }
                if (state.finished.isNotEmpty()) item { InfoNote(stringResource(R.string.finished_info)) }
            }
        }
    }
}

@Composable
private fun ActiveCard(row: MedRow, fmt: Formatter, onClick: () -> Unit) {
    val c = PillBox.colors
    val m = row.item.medicine
    val schedule = row.item.schedule
    val info = row.info
    val sep = stringResource(R.string.dot_sep)
    val sub = listOfNotNull(com.pillbox.ui.strengthLabel(m.strength), lengthText(schedule, fmt)).joinToString(sep)
    val label = levelLabel(info.level)
    val color = levelColor(info.level)
    val barColor = if (info.level == StockLevel.BUY_SOON) PillBox.illustration.stockLow else color
    val end = schedule?.end
    val left = when {
        info.level == StockLevel.OUT -> label
        end != null -> stringResourceIso(R.string.stock_left, fmt.amount(m.stock)) + sep + label
        info.daysLeft != null -> pluralStringResource(R.plurals.stock_days_left, info.daysLeft.coerceAtLeast(0), com.pillbox.ui.components.isolate(fmt.number(info.daysLeft))) + sep + label
        else -> label
    }
    val right = if (end != null && info.level != StockLevel.OUT) stringResourceIso(R.string.ends_on, shortDate(end, fmt)) else stockAmountText(m, fmt)
    val fraction = com.pillbox.domain.StockMath.barFraction(m)
    val sentence = listOf(m.name, sub, left, right).joinToString(", ")

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = sentence }
            .padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
            MedicineBadge(m.unit)
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Icon(com.pillbox.ui.components.DesignIcons.Chevron, contentDescription = null, tint = c.textMuted)
        }
        // Stock bar: the words under it carry the meaning, the color only supports them.
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.divider, CircleShape)) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(8.dp).background(barColor, CircleShape))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f))
            Spacer4()
            Text(right, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.End)
        }
    }
}

/** A paused medicine: dashed card, no stock bar. Opens the detail screen where Resume now is the main button. */
@Composable
private fun PausedCard(row: MedRow, fmt: Formatter, onClick: () -> Unit) {
    val c = PillBox.colors
    val m = row.item.medicine
    val sep = stringResource(R.string.dot_sep)
    val sub = listOfNotNull(com.pillbox.ui.strengthLabel(m.strength), lengthText(row.item.schedule, fmt)).joinToString(sep)
    val until = row.pause?.until
    val status = if (until == null) stringResource(R.string.paused_until_resume)
    else stringResourceIso(R.string.paused_until, shortDate(until.toLocalDate(), fmt))
    val tap = stringResource(R.string.paused_tap)
    val tag = stringResource(R.string.tag_paused)
    val dash = c.placeholder
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.background, MaterialTheme.shapes.large)
            .drawBehind {
                drawRoundRect(
                    dash, size = Size(size.width, size.height), cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${m.name}, $sub, $tag, $status, $tap" }
            .padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
            Box(Modifier.size(36.dp).background(PillBox.illustration.paused, CircleShape), contentAlignment = Alignment.Center) {
                PauseGlyph(c.card, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Text(
                tag, style = MaterialTheme.typography.labelMedium, color = c.text,
                modifier = Modifier.background(c.divider, MaterialTheme.shapes.small).padding(horizontal = Space.S10, vertical = Space.S6),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(status, style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.weight(1f))
            Spacer4()
            Text(tap, style = MaterialTheme.typography.labelLarge, color = c.accent, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Spacer4() = Box(Modifier.width(Space.S8))

@Composable
private fun FinishedCard(item: MedicineWithSchedule, fmt: Formatter, onClick: () -> Unit) {
    val c = PillBox.colors
    val m = item.medicine
    val sep = stringResource(R.string.dot_sep)
    val title = listOfNotNull(m.name, com.pillbox.ui.strengthLabel(m.strength)).joinToString(" ")
    val ended = m.endedOn
    val course = m.status == MedicineStatus.FINISHED
    val sub = buildList {
        if (ended != null) add(stringResource(if (course) R.string.finished_on else R.string.stopped_on, dayMonth(ended, fmt)))
        if (course) add(lengthText(item.schedule, fmt))
    }.joinToString(sep)
    val tag = stringResource(if (course) R.string.tag_course_done else R.string.tag_stopped)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowMinHeight)
            .background(c.card, MaterialTheme.shapes.large)
            .border(Dimens.ChipBorder, c.border, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $sub, $tag" }
            .padding(Space.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        MedicineBadge(m.unit, muted = true)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Text(
            tag, style = MaterialTheme.typography.labelMedium, color = c.textMuted,
            modifier = Modifier.background(c.divider, MaterialTheme.shapes.small).padding(horizontal = Space.S10, vertical = Space.S6),
        )
    }
}

@Composable
private fun AddMedicineButton(onClick: () -> Unit) {
    val c = PillBox.colors
    val dash = c.placeholder
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowMinHeight)
            .drawBehind {
                drawRoundRect(
                    dash, size = Size(size.width, size.height), cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(com.pillbox.ui.components.DesignIcons.Plus, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Box(Modifier.width(Space.S8))
        Text(stringResource(R.string.add_medicine), style = MaterialTheme.typography.labelLarge, color = c.accent)
    }
}

@Composable
internal fun InfoNote(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = com.pillbox.ui.components.DesignIcons.Info,
    /** When set, the whole note is a button (the refill note opens the Refill alert screen). */
    onClick: (() -> Unit)? = null,
) {
    val c = PillBox.colors
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = stringResource(R.string.refill_change_a11y), role = Role.Button, onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).then(tap).padding(Space.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(com.pillbox.ui.components.DesignIcons.Chevron, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
    }
}
