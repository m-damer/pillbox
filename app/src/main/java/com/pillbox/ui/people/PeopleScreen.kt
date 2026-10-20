package com.pillbox.ui.people

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.medicines.ListToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

@Composable
fun PeopleScreen(
    vm: PeopleViewModel,
    onOpenPerson: (Long) -> Unit,
    onEditPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    toast: ListToast?,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
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
        PeopleContent(
            state, fmt,
            onOpen = { id -> vm.select(id); onOpenPerson(id) },
            onEdit = onEditPerson, onAdd = onAddPerson, onRingOthers = vm::setRingOthers,
        )
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** The screen itself, without a ViewModel, so screenshots can draw it. */
@Composable
fun PeopleContent(
    state: PeopleUiState,
    fmt: Formatter,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onRingOthers: (Boolean) -> Unit,
) {
    val c = PillBox.colors
    val chips = rememberChips(state.rows.map { it.person })
    val others = chips.filter { !it.person.isMe }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop)) {
            Text(stringResource(R.string.people_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Text(
                stringResource(if (others.isEmpty()) R.string.people_sub_empty else R.string.people_sub),
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Space.S12, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            items(state.rows, key = { it.person.id }) { row ->
                val chip = chips.first { it.person.id == row.person.id }
                PersonCard(row, chip, row.person.id == state.activeId, fmt, onOpen = { onOpen(row.person.id) }, onEdit = { onEdit(row.person.id) })
            }
            item { AddPersonCard(onAdd) }
            if (others.isEmpty()) {
                item { Note(stringResource(R.string.people_note)) }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                        SwitchRow(
                            stringResourceIso(R.string.people_ring_all, joinNames(others.map { it.label })), state.ringOthers, onRingOthers,
                            subtitle = stringResource(R.string.people_ring_sub),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun joinNames(names: List<String>): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    return names.dropLast(1).joinToString(", ") + stringResource(R.string.list_and) + names.last()
}

@Composable
private fun PersonCard(row: PersonRow, chip: PersonChip, active: Boolean, fmt: Formatter, onOpen: () -> Unit, onEdit: () -> Unit) {
    val c = PillBox.colors
    val d = row.today
    val (status, statusColor) = when {
        d.missed > 0 -> pluralStringResource(R.plurals.people_missed_at, d.missed, fmt.number(d.missed), d.firstMissed?.let { fmt.time(it.toLocalTime()) } ?: "") to c.danger
        d.total == 0 -> stringResource(R.string.people_no_doses) to c.textMuted
        d.taken == d.total -> stringResource(R.string.people_all_taken) to c.success
        else -> stringResourceIso(R.string.people_taken, fmt.number(d.taken), fmt.number(d.total)) to c.textMuted
    }
    val open = stringResourceIso(R.string.people_open_a11y, chip.label, status)
    val editDesc = stringResourceIso(R.string.people_edit_a11y, chip.label)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(c.card)
            .then(if (active) Modifier.border(2.dp, c.primary, MaterialTheme.shapes.large) else Modifier)
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = open }
                .padding(start = Space.S12, top = Space.S8, bottom = Space.S8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Avatar(chip.initials, row.person.colorIndex, row.person.isMe, modifier = Modifier.clearAndSetSemantics { })
            Column(Modifier.weight(1f)) {
                Text(chip.label, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
            }
        }
        Box(
            Modifier
                .padding(horizontal = Space.S8)
                .size(Dimens.TouchTargetMin)
                .clip(CircleShape)
                .background(c.primaryContainer)
                .clickable(role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = editDesc },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Edit, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun AddPersonCard(onAdd: () -> Unit) {
    val c = PillBox.colors
    val dash = c.border
    val radius = 22.dp
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onAdd)
            .drawBehind {
                drawRoundRect(
                    color = dash, size = Size(size.width, size.height), cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
                )
            }
            .padding(Space.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(Modifier.size(48.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.accent)
        }
        Text(stringResource(R.string.people_add), style = MaterialTheme.typography.labelLarge, color = c.accent)
    }
}

@Composable
private fun Note(text: String) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S16),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
}
