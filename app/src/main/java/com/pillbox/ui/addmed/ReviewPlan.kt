package com.pillbox.ui.addmed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.AddMedicineForm
import com.pillbox.domain.ComingUp
import com.pillbox.domain.FreqChoice
import com.pillbox.domain.PlanBuilder
import com.pillbox.domain.PlanEntry
import com.pillbox.domain.PlanGroup
import com.pillbox.ui.Formatter
import com.pillbox.ui.blockColors
import com.pillbox.ui.blockName
import com.pillbox.ui.blockOf
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.stringResourceIso
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.PillIcon
import com.pillbox.ui.today.foodText
import java.time.LocalDate

/**
 * Step 4 of adding a medicine: the day as it will ring, every medicine together, before reminders start.
 * The new medicine's own times can be changed here (once, twice and 3 times a day); the others are shown as they are.
 */
@Composable
fun ReviewPlanContent(
    state: AddMedUiState,
    fmt: Formatter,
    today: LocalDate,
    edit: ((AddMedicineForm) -> AddMedicineForm) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onEditMeals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PillBox.colors
    val form = state.form
    val draftTimes = if (form.freq in setOf(FreqChoice.ONCE, FreqChoice.TWICE, FreqChoice.THREE)) form.times() else emptyList()
    val plan = remember(state.others, form, today) {
        PlanBuilder.build(state.others, form.medicine(0), form.schedule(today), draftTimes, today)
    }
    var picking by remember { mutableStateOf<Int?>(null) }
    val canChange = plan.groups.any { it.changeable != null }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(
            Modifier.padding(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4),
        ) {
            val backLabel = stringResource(R.string.back)
            Box(
                Modifier.size(Dimens.TouchTargetMin).semantics { contentDescription = backLabel; role = Role.Button }.clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = c.text) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(if (canChange) R.string.plan_sub else R.string.plan_sub_fixed),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            plan.groups.forEach { g -> PlanGroupCard(g, fmt, onChange = { picking = it }) }
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                Column(Modifier.fillMaxWidth().padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                    Text(stringResource(R.string.plan_coming_up), style = MaterialTheme.typography.labelLarge, color = c.textMuted, modifier = Modifier.semantics { heading() })
                    plan.comingUp.forEach { ComingUpRow(it, fmt) }
                    Box(Modifier.fillMaxWidth().size(1.dp).background(c.divider))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                        Text(stringResource(R.string.plan_meal_timing), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                        val edit = stringResource(R.string.plan_edit)
                        val editDesc = stringResource(R.string.plan_meal_edit_a11y)
                        Box(
                            Modifier.heightIn(min = Dimens.TouchTargetMin).background(c.primaryContainer, MaterialTheme.shapes.medium)
                                .semantics { contentDescription = editDesc; role = Role.Button }.clickable(onClick = onEditMeals).padding(horizontal = Space.S16),
                            contentAlignment = Alignment.Center,
                        ) { Text(edit, style = MaterialTheme.typography.labelLarge, color = c.accent) }
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
            PrimaryButton(stringResource(R.string.plan_start), onStart, enabled = !state.saving)
        }
    }

    picking?.let { index ->
        PBTimePickerDialog(draftTimes.getOrElse(index) { form.firstDose }, fmt.is24Hour, onDismiss = { picking = null }) { picked ->
            edit { f -> f.withDoseTime(index, picked) }
            picking = null
        }
    }
}

@Composable
private fun PlanGroupCard(group: PlanGroup, fmt: Formatter, onChange: (Int) -> Unit) {
    val c = PillBox.colors
    val block = blockOf(group.time)
    val (bg, fg) = blockColors(block)
    val time = fmt.time(group.time)
    val name = blockName(block, afternoon = true)
    val change = group.changeable
    Column(Modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.large).padding(Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.weight(1f).semantics { heading() })
            if (change != null) {
                val desc = stringResourceIso(R.string.plan_change_time_a11y, name, time)
                Row(
                    Modifier.heightIn(min = Dimens.TouchTargetMin).background(c.card.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                        .semantics { contentDescription = desc; role = Role.Button }.clickable { onChange(change) }.padding(horizontal = Space.S12),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(time, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold) }
            } else Text(time, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.End)
        }
        group.entries.forEach { PlanEntryRow(it, fg) }
    }
}

@Composable
private fun PlanEntryRow(e: PlanEntry, fg: androidx.compose.ui.graphics.Color) {
    val c = PillBox.colors
    val food = foodText(e.withFood)
    val newLabel = stringResource(R.string.plan_new)
    val said = listOfNotNull(e.name, food, if (e.isNew) newLabel else null).joinToString(", ")
    Row(
        Modifier.fillMaxWidth()
            .background(c.card.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .then(if (e.isNew) Modifier.border(Dimens.ChipBorder, c.primary, MaterialTheme.shapes.medium) else Modifier)
            .heightIn(min = Dimens.TouchTargetMin).padding(horizontal = Space.S12, vertical = Space.S8)
            .semantics(mergeDescendants = true) { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        PillIcon(e.unit, size = 24.dp)
        Text(e.name, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.weight(1f))
        if (food != null) Text(food, style = MaterialTheme.typography.bodyMedium, color = fg, textAlign = TextAlign.End)
    }
}

@Composable
private fun ComingUpRow(item: ComingUp, fmt: Formatter) {
    val c = PillBox.colors
    val value = when {
        item.asNeeded -> stringResource(R.string.plan_as_needed)
        item.lastDose != null -> stringResourceIso(R.string.plan_last_dose, shortDate(item.lastDose, fmt))
        item.refillBy != null -> stringResourceIso(R.string.plan_refill_by, fmt.digits(item.refillBy.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())))
        else -> stringResource(R.string.plan_ongoing)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics(mergeDescendants = true) { contentDescription = "${item.name}, $value" }, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.End)
    }
}
