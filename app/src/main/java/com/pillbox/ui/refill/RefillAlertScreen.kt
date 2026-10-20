package com.pillbox.ui.refill

import com.pillbox.ui.components.stringResourceIso
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.RefillSettings
import com.pillbox.ui.Formatter
import com.pillbox.ui.addmed.PBTimePickerDialog
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.quantityFor
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.PillIcon
import com.pillbox.ui.unitPlural

/** Refill alert: how many days before a medicine runs out, at what time, and for whom. */
@Composable
fun RefillAlertScreen(vm: RefillAlertViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    val leave = { if (vm.back()) onClose() }
    BackHandler(onBack = leave)
    RefillAlertContent(
        state, fmt, vm.firstAlertDate(), onBack = leave, onDays = vm::setDays, onOpenTime = vm::openTimePicker,
        onCloseTime = vm::closeTimePicker, onTime = vm::setTime, onPeople = vm::setIncludePeople,
        onSave = vm::save, onKeepEditing = vm::dismissUnsaved, onLeave = onClose,
    )
}

/** The screen itself, without a ViewModel, so screenshots can draw it. [firstAlert] is the date the preview alert first rings. */
@Composable
fun RefillAlertContent(
    state: RefillUiState,
    fmt: Formatter,
    firstAlert: java.time.LocalDate?,
    onBack: () -> Unit,
    onDays: (Int) -> Unit,
    onOpenTime: () -> Unit,
    onCloseTime: () -> Unit,
    onTime: (java.time.LocalTime) -> Unit,
    onPeople: (Boolean) -> Unit,
    onSave: () -> Unit,
    onKeepEditing: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = PillBox.colors
    val s = state.settings
    val leave = onBack

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = leave),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text) }
            Text(
                stringResource(R.string.refill_title), style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            DaysCard(s.daysBefore, fmt, onDays = onDays)
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S8),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Text(stringResource(R.string.refill_time_label), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                    val timeText = fmt.time(s.time)
                    val desc = stringResourceIso(R.string.refill_time_a11y, timeText)
                    Box(
                        Modifier
                            .heightIn(min = Dimens.TouchTargetMin)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.primaryContainer)
                            .clickable(role = Role.Button, onClick = onOpenTime)
                            .semantics { contentDescription = desc }
                            .padding(horizontal = Space.S16),
                        contentAlignment = Alignment.Center,
                    ) { Text(timeText, style = MaterialTheme.typography.labelLarge, color = c.accent) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.SettingsRowMinHeight)
                        .toggleable(value = s.includePeople, role = Role.Switch, onValueChange = onPeople)
                        .padding(horizontal = Space.S16, vertical = Space.S8),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.refill_people_label), style = MaterialTheme.typography.labelLarge, color = c.text)
                        Text(stringResource(R.string.refill_people_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    Switch(
                        checked = s.includePeople, onCheckedChange = null,
                        thumbContent = if (s.includePeople) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.card, checkedTrackColor = c.primary, checkedIconColor = c.primary,
                            uncheckedThumbColor = c.card, uncheckedTrackColor = c.placeholder, uncheckedBorderColor = c.placeholder,
                        ),
                    )
                }
            }
            state.preview?.let { p ->
                val date = firstAlert
                Text(
                    stringResource(R.string.refill_preview).uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                    modifier = Modifier.padding(start = Space.S8, top = Space.S4).semantics { heading() },
                )
                Row(
                    Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
                    horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
                ) {
                    PillIcon(p.unit, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        val res = androidx.compose.ui.platform.LocalContext.current.resources
                        val amount = fmt.amount(p.stock) + " " + res.getQuantityString(unitPlural(p.unit), quantityFor(p.stock))
                        Text(stringResourceIso(R.string.refill_preview_title, p.name, amount), style = MaterialTheme.typography.labelLarge, color = c.text)
                        val daysText = pluralStringResource(R.plurals.n_days, s.daysBefore, fmt.number(s.daysBefore))
                        val dateText = date?.let { shortDate(it, fmt) } ?: ""
                        Text(
                            if (date == java.time.LocalDate.now()) stringResourceIso(R.string.refill_preview_today, daysText, dateText)
                            else stringResourceIso(R.string.refill_preview_later, daysText, dateText),
                            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.size(Space.S8))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom)) {
            PrimaryButton(stringResource(R.string.btn_save), onSave)
        }
    }

    if (state.showTimePicker) PBTimePickerDialog(s.time, fmt.is24Hour, onDismiss = onCloseTime, onConfirm = onTime)
    if (state.showUnsaved) {
        MessageDialog(DialogKind.Warning, stringResource(R.string.unsaved_title), stringResource(R.string.refill_unsaved_body), onKeepEditing) {
            PrimaryButton(stringResource(R.string.keep_editing), onKeepEditing)
            DangerButton(stringResource(R.string.leave), onLeave)
        }
    }
}

@Composable
private fun DaysCard(days: Int, fmt: Formatter, onDays: (Int) -> Unit) {
    val c = PillBox.colors
    val caption = stringResource(R.string.refill_days_caption)
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
            StepCircle("−", stringResourceIso(R.string.stepper_decrease, caption), days > RefillSettings.MIN_DAYS, filled = false) { onDays(days - 1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt.number(days), style = MaterialTheme.typography.displaySmall, color = c.text, textAlign = TextAlign.Center)
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
            }
            StepCircle("+", stringResourceIso(R.string.stepper_increase, caption), days < RefillSettings.MAX_DAYS, filled = true) { onDays(days + 1) }
        }
        ChipGroup {
            RefillSettings.PRESETS.forEach { n ->
                PBChip(pluralStringResource(R.plurals.n_days, n, fmt.number(n)), days == n, { onDays(n) })
            }
        }
    }
}

@Composable
private fun StepCircle(glyph: String, description: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit) {
    val c = PillBox.colors
    Box(
        Modifier
            .size(Dimens.StepperButton)
            .background(if (filled) c.primary else c.primaryContainer, CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph, style = MaterialTheme.typography.headlineSmall,
            color = if (!enabled) c.placeholder else if (filled) c.onPrimary else c.accent,
        )
    }
}
