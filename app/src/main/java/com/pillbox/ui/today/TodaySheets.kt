package com.pillbox.ui.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.LateChoice
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.medicines.AddStockSheet
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.unitPlural
import java.time.Duration
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaySheets(sheet: TodaySheet?, vm: TodayViewModel, fmt: Formatter) {
    if (sheet == null) return
    if (sheet is TodaySheet.AddStock) {
        AddStockSheet(sheet.medicine, sheet.schedule, fmt, onSave = vm::saveStock, onDismiss = vm::dismissSheet)
        return
    }
    ModalBottomSheet(
        onDismissRequest = vm::dismissSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PillBox.colors.card,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            when (sheet) {
                is TodaySheet.Skip -> SkipSheet(sheet, vm)
                is TodaySheet.TooSoon -> TooSoonSheet(sheet, vm, fmt)
                is TodaySheet.OutOfStock -> OutOfStockSheet(sheet, vm, fmt)
                is TodaySheet.Late -> LateSheet(sheet, fmt, vm::confirmLate)
                is TodaySheet.AddStock -> Unit
            }
            Box(Modifier.size(Space.S12))
        }
    }
}

@Composable
internal fun SheetTitle(title: String, subtitle: String? = null) {
    val c = PillBox.colors
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
}

@Composable
private fun SkipSheet(sheet: TodaySheet.Skip, vm: TodayViewModel) {
    val c = PillBox.colors
    var reason by remember { mutableStateOf<SkipReason?>(null) }
    val title = if (sheet.rows.size == 1) stringResource(R.string.skip_title, sheet.rows[0].medicine.name) else stringResource(R.string.skip_title_many)
    SheetTitle(title, stringResource(R.string.skip_sub))
    val options = listOf(
        SkipReason.NOT_FEELING_WELL to R.string.reason_not_well, SkipReason.SIDE_EFFECTS to R.string.reason_side_effects,
        SkipReason.RAN_OUT to R.string.reason_ran_out, SkipReason.DOCTOR_SAID to R.string.reason_doctor, SkipReason.OTHER to R.string.reason_other,
    )
    Column(Modifier.selectableGroup()) {
        options.forEach { (r, label) ->
            val on = reason == r
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ListRowMinHeight)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { reason = if (on) null else r })
                    .padding(vertical = Space.S8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (on) c.primary else c.card, CircleShape)
                        .border(Dimens.ChipBorder, if (on) c.primary else c.placeholder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
            }
        }
    }
    PrimaryButton(stringResource(R.string.skip_dose), { vm.confirmSkip(reason) })
    LinkButton(stringResource(R.string.cancel), vm::dismissSheet)
}

/** "2 hours", "45 minutes" for the gap text. */
@Composable
private fun durationText(minutes: Long, fmt: Formatter): String =
    if (minutes >= 60) pluralStringResource(R.plurals.n_hours, (minutes / 60).toInt(), fmt.number((minutes / 60).toInt()))
    else pluralStringResource(R.plurals.n_minutes, minutes.toInt().coerceAtLeast(0), fmt.number(minutes.toInt().coerceAtLeast(0)))

@Composable
private fun TooSoonSheet(sheet: TodaySheet.TooSoon, vm: TodayViewModel, fmt: Formatter) {
    val ago = Duration.between(sheet.lastTaken, LocalDateTime.now()).toMinutes()
    SheetTitle(
        stringResource(R.string.too_soon_title),
        stringResource(
            R.string.too_soon_body, sheet.medicine, durationText(ago, fmt),
            pluralStringResource(R.plurals.n_hours, sheet.minGapHours, fmt.number(sheet.minGapHours)),
        ),
    )
    PrimaryButton(stringResource(R.string.wait_dont_take), vm::dismissSheet)
    OutlineButton(stringResource(R.string.take_anyway_long), vm::takeAnyway)
}

/** "40 min", "1 h 20 min" for the late and gap lines. */
@Composable
private fun gapText(minutes: Int, fmt: Formatter): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> stringResource(R.string.min_short, fmt.number(m))
        m % 60 == 0 -> stringResource(R.string.gap_h, fmt.number(m / 60))
        else -> stringResource(R.string.gap_h_m, fmt.number(m / 60), fmt.number(m % 60))
    }
}

@Composable
internal fun LateSheet(sheet: TodaySheet.Late, fmt: Formatter, onConfirm: (LateChoice, Boolean) -> Unit) {
    val c = PillBox.colors
    var choice by remember { mutableStateOf<LateChoice?>(null) }
    var always by remember { mutableStateOf(false) }
    val late = gapText(sheet.minutesLate, fmt)
    SheetTitle(
        stringResource(if (sheet.askRows.size == 1) R.string.late_title_one else R.string.late_title_many, late),
        stringResource(R.string.late_sub, fmt.time(sheet.duePlanned), fmt.time(sheet.takenAt), sheet.askRows.joinToString(", ") { it.medicine.name }),
    )
    val moveText = when {
        sheet.gapHours != null && sheet.moveTimes.size >= 2 -> stringResource(
            R.string.late_move_gap_two, pluralStringResource(R.plurals.n_hours, sheet.gapHours, fmt.number(sheet.gapHours)),
            fmt.time(sheet.moveTimes[0]), fmt.time(sheet.moveTimes[1]),
        )
        sheet.gapHours != null && sheet.moveTimes.size == 1 -> stringResource(
            R.string.late_move_gap_one, pluralStringResource(R.plurals.n_hours, sheet.gapHours, fmt.number(sheet.gapHours)), fmt.time(sheet.moveTimes[0]),
        )
        sheet.moveTimes.isEmpty() -> stringResource(R.string.late_move_none)
        else -> stringResource(R.string.late_move_fixed, gapText(sheet.delayMinutes, fmt), fmt.time(sheet.moveTimes[0]))
    }
    val keepText = if (sheet.gapHours != null && sheet.keepNext != null && sheet.keepGapMinutes != null) {
        stringResource(R.string.late_keep_gap, fmt.time(sheet.keepNext), gapText(sheet.keepGapMinutes, fmt))
    } else stringResource(R.string.late_keep_fixed)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        LateOption(stringResource(R.string.late_move), moveText, choice == LateChoice.MOVE) { choice = LateChoice.MOVE }
        LateOption(stringResource(R.string.late_keep), keepText, choice == LateChoice.KEEP) { choice = LateChoice.KEEP }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .toggleable(value = always, role = Role.Checkbox, onValueChange = { always = it }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (always) c.primary else c.card, MaterialTheme.shapes.small)
                .border(Dimens.ChipBorder, if (always) c.primary else c.placeholder, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) { if (always) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
        Text(stringResource(R.string.late_always), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
    PrimaryButton(
        stringResource(if (choice == null) R.string.late_choose else R.string.late_continue),
        { choice?.let { onConfirm(it, always) } }, enabled = choice != null,
    )
}

@Composable
private fun LateOption(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val c = PillBox.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowMinHeight)
            .background(if (selected) c.primaryContainer else c.card, MaterialTheme.shapes.large)
            .border(if (selected) Dimens.ChipBorder * 2 else Dimens.ChipBorder, if (selected) c.primary else c.border, MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(Space.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (selected) c.primary else c.card, CircleShape)
                .border(Dimens.ChipBorder, if (selected) c.primary else c.placeholder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

@Composable
private fun OutOfStockSheet(sheet: TodaySheet.OutOfStock, vm: TodayViewModel, fmt: Formatter) {
    val unit = pluralStringResource(unitPlural(sheet.medicine.unit), quantityFor(2.0))
    SheetTitle(stringResource(R.string.out_title, unit), stringResource(R.string.out_body, sheet.medicine.name))
    PrimaryButton(stringResource(R.string.take_anyway), vm::takeWithoutStock)
    OutlineButton(stringResource(R.string.out_update), vm::updateStock)
    LinkButton(stringResource(R.string.cancel), vm::dismissSheet)
}
