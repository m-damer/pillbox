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
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.LinkButton
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
            }
            Box(Modifier.size(Space.S12))
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
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

@Composable
private fun OutOfStockSheet(sheet: TodaySheet.OutOfStock, vm: TodayViewModel, fmt: Formatter) {
    val unit = pluralStringResource(unitPlural(sheet.medicine.unit), quantityFor(2.0))
    SheetTitle(stringResource(R.string.out_title, unit), stringResource(R.string.out_body, sheet.medicine.name))
    PrimaryButton(stringResource(R.string.take_anyway), vm::takeWithoutStock)
    LinkButton(stringResource(R.string.cancel), vm::dismissSheet)
}
