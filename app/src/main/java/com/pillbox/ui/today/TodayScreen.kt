package com.pillbox.ui.today

import com.pillbox.ui.components.stringResourceIso
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.DoseGroup
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.GroupState
import com.pillbox.domain.RowStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.StockLevel
import com.pillbox.domain.TodayModel
import com.pillbox.domain.WithFood
import com.pillbox.ui.Formatter
import com.pillbox.ui.blockColors
import com.pillbox.ui.blockName
import com.pillbox.ui.blockOf
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.medicines.stockToastText
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.quantityFor
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.illustration
import com.pillbox.ui.theme.Space
import com.pillbox.ui.unitPlural
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    vm: TodayViewModel, onAddMedicine: () -> Unit, modifier: Modifier = Modifier,
    remindersOff: Boolean = false, onFixReminders: () -> Unit = {}, onGoToMeds: () -> Unit = {},
    people: com.pillbox.ui.people.PeopleBar? = null,
    onTakeAsNeeded: (Long) -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val snackbar = remember { SnackbarHostState() }

    val toast = state.toast
    val toastText = toast?.let { toastMessage(it.kind) }
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(toast?.id) {
        if (toast == null || toastText == null) return@LaunchedEffect
        val job = launch { delay(5_000); snackbar.currentSnackbarData?.dismiss() }
        val result = snackbar.showSnackbar(
            toastText, actionLabel = if (toast.kind is ToastKind.StockUpdated) null else undoLabel, duration = SnackbarDuration.Indefinite,
        )
        job.cancel()
        if (result == SnackbarResult.ActionPerformed) vm.undo() else vm.toastShown()
    }

    Box(modifier.fillMaxSize()) {
        TodayContent(
            state = state, fmt = fmt, onSelectDate = vm::selectDate, onBackToToday = vm::backToToday,
            onTake = vm::take, onSkip = vm::skip, onAddMedicine = onAddMedicine,
            remindersOff = remindersOff, onFixReminders = onFixReminders,
            onResumeAll = vm::resumeAll, onGoToMeds = onGoToMeds, people = people, onTakeAsNeeded = onTakeAsNeeded,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) { data ->
            val c = PillBox.colors
            Snackbar(
                snackbarData = data, containerColor = c.text, contentColor = c.background, actionColor = c.background,
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
    TodaySheets(state.sheet, vm, fmt)
}

@Composable
private fun toastMessage(kind: ToastKind): String = when (kind) {
    is ToastKind.Taken ->
        if (kind.names.size == 1) stringResourceIso(R.string.toast_taken, kind.names[0])
        else pluralStringResource(R.plurals.toast_taken_n, kind.names.size, kind.names.size)
    is ToastKind.Skipped ->
        if (kind.names.size == 1) stringResourceIso(R.string.toast_skipped, kind.names[0])
        else pluralStringResource(R.plurals.toast_skipped_n, kind.names.size, kind.names.size)
    is ToastKind.StockUpdated -> {
        val (title, body) = stockToastText(LocalContext.current.resources, rememberFormatter(), kind.unit, kind.result)
        "$title. $body"
    }
}

private fun locale(): Locale = Locale.getDefault()
private fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale()))

@Composable
fun TodayContent(
    state: TodayUiState,
    fmt: Formatter,
    onSelectDate: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    onTake: (List<DoseRow>) -> Unit,
    onSkip: (List<DoseRow>) -> Unit,
    onAddMedicine: () -> Unit,
    modifier: Modifier = Modifier,
    remindersOff: Boolean = false,
    onFixReminders: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onGoToMeds: () -> Unit = {},
    people: com.pillbox.ui.people.PeopleBar? = null,
    onTakeAsNeeded: (Long) -> Unit = {},
) {
    val today = state.now.toLocalDate()
    val model = state.model
    if (state.loaded && state.allPaused != null) {
        Column(modifier.fillMaxSize()) {
            PeopleRow(people)
            AllPausedToday(today, state.allPaused, fmt, onResumeAll, onGoToMeds, Modifier.weight(1f))
        }
        return
    }
    if (state.loaded && !state.hasMedicines) {
        Column(modifier.fillMaxSize()) {
            PeopleRow(people)
            EmptyToday(today, onAddMedicine, Modifier.weight(1f))
        }
        return
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { Header(state.selectedDate, today, model, fmt) }
                com.pillbox.ui.people.PeopleBarSwitcher(people)
            }
        }
        if (people?.active?.isMe == false) item { com.pillbox.ui.people.ViewingBanner(people) }
        if (remindersOff) item { RemindersOffBanner(onFixReminders) }
        item { WeekStrip(state.selectedDate, today, fmt, onSelectDate) }
        if (state.selectedDate != today) item { BackToToday(onBackToToday) }
        if (state.selectedDate == today && model != null && model.groups.isNotEmpty()) item { DayTimeline(model, fmt) }
        if (model != null) {
            if (model.groups.isEmpty() && state.loaded) {
                item { InfoNote(stringResource(R.string.nothing_planned)) }
            }
            items(model.groups, key = { it.time.toString() }) { group ->
                GroupCard(group, fmt, onTake, onSkip)
            }
            if (state.selectedDate > today && model.groups.isNotEmpty()) item { InfoNote(stringResource(R.string.future_note)) }
        }
        if (state.selectedDate == today && state.asNeeded.isNotEmpty()) item { AsNeededCard(state.asNeeded, onTakeAsNeeded) }
    }
    if (state.lowStock.isNotEmpty()) {
        Column(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8),
            verticalArrangement = Arrangement.spacedBy(Space.S8),
        ) { state.lowStock.take(2).forEach { LowStockStrip(it, onGoToMeds) } }
    }
    }
}

/** "As needed": each as-needed medicine with a Take now button that opens its log. */
@Composable
private fun AsNeededCard(items: List<AsNeededItem>, onTake: (Long) -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(vertical = Space.S4)) {
        Text(
            stringResource(R.string.today_as_needed).uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
            modifier = Modifier.padding(horizontal = Space.S16, vertical = Space.S8).semantics { heading() },
        )
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            val label = stringResource(R.string.prn_take_now)
            val a11y = stringResourceIso(R.string.prn_take_now_a11y, item.name)
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin).padding(horizontal = Space.S16, vertical = Space.S8),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIcon(item.unit, null, 26.dp)
                Text(item.name, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                Box(Modifier.width(IntrinsicSize.Max).semantics { contentDescription = a11y }) {
                    com.pillbox.ui.components.OutlineButton(label, { onTake(item.id) })
                }
            }
        }
    }
}

/** "Metformin · 4 days left · Buy soon". Opens the Medicines tab. */
@Composable
private fun LowStockStrip(item: LowStock, onClick: () -> Unit) {
    val c = PillBox.colors
    val what = if (item.level == StockLevel.BUY_SOON && item.daysLeft != null)
        pluralStringResource(R.plurals.stock_days_left, item.daysLeft.coerceAtLeast(0), rememberFormatter().number(item.daysLeft))
    else stringResource(R.string.stock_out)
    val label = stringResource(if (item.level == StockLevel.OUT) R.string.out_update else R.string.stock_buy_soon)
    val text = stringResourceIso(R.string.low_stock_strip, item.name, what)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .background(c.warningContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$text. $label" }
            .padding(horizontal = Space.S12, vertical = Space.S10),
        horizontalArrangement = Arrangement.spacedBy(Space.S10), verticalAlignment = Alignment.CenterVertically,
    ) {
        PillIcon(item.unit, null, 26.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.warning)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- header, week, timeline

/** Switcher and "you are viewing" note for the screens that replace the whole list (nothing planned, all paused). */
@Composable
private fun PeopleRow(people: com.pillbox.ui.people.PeopleBar?) {
    if (people == null || people.people.size < 2) return
    Column(Modifier.fillMaxWidth().padding(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { com.pillbox.ui.people.PeopleBarSwitcher(people) }
        com.pillbox.ui.people.ViewingBanner(people)
    }
}

@Composable
private fun Header(selected: LocalDate, today: LocalDate, model: TodayModel?, fmt: Formatter) {
    val c = PillBox.colors
    val title = when (selected) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> selected.dayOfWeek.getDisplayName(TextStyle.FULL, locale())
    }
    val date = fmt.digits(shortDate(selected))
    val sub = when {
        model == null -> date
        selected > today -> stringResourceIso(R.string.sub_planned, date, pluralStringResource(R.plurals.n_doses, model.total, fmt.number(model.total)))
        else -> stringResourceIso(R.string.sub_taken, date, fmt.number(model.taken), fmt.number(model.total))
    }
    Column(Modifier.fillMaxWidth().padding(top = Space.S4)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, fmt: Formatter, onSelect: (LocalDate) -> Unit) {
    val c = PillBox.colors
    val first = com.pillbox.ui.firstDayOfWeek()
    val start = today.minusDays(((today.dayOfWeek.value - first.value + 7) % 7).toLong())
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
        for (i in 0..6) {
            val day = start.plusDays(i.toLong())
            val on = day == selected
            val full = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale()) + " " + fmt.number(day.dayOfMonth)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .background(if (on) c.primary else Color.Transparent, MaterialTheme.shapes.medium)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(day) })
                    .semantics { contentDescription = full }
                    .padding(vertical = Space.S8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale()),
                    style = MaterialTheme.typography.labelSmall, color = if (on) c.onPrimary else c.textMuted, maxLines = 1,
                )
                Text(fmt.number(day.dayOfMonth), style = MaterialTheme.typography.labelLarge, color = if (on) c.onPrimary else c.text)
            }
        }
    }
}

@Composable
private fun RemindersOffBanner(onFix: () -> Unit) {
    val c = PillBox.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .background(c.warningContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onFix)
            .padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.banner_off_title), style = MaterialTheme.typography.labelLarge, color = c.warning)
            Text(stringResource(R.string.banner_off_body), style = MaterialTheme.typography.bodySmall, color = c.warning)
        }
    }
}

@Composable
private fun BackToToday(onClick: () -> Unit) {
    val c = PillBox.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .background(c.primaryContainer, Dimens.ButtonShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(R.string.back_to_today), style = MaterialTheme.typography.labelLarge, color = c.accent) }
}

// ---------------------------------------------------------------- groups and rows

@Composable
private fun GroupCard(group: DoseGroup, fmt: Formatter, onTake: (List<DoseRow>) -> Unit, onSkip: (List<DoseRow>) -> Unit) {
    val c = PillBox.colors
    val block = blockOf(group.time.toLocalTime())
    val (bg, fg) = groupColors(group)
    val outline = when (group.state) {
        GroupState.NEXT -> c.primary
        GroupState.LATE -> c.warning
        else -> Color.Transparent
    }
    val timeText = fmt.time(group.time.toLocalTime())
    val suffix = when (group.state) {
        GroupState.NEXT -> " · " + stringResource(R.string.group_next)
        GroupState.LATE ->
            " · " + if (group.minutesLate >= 60) stringResourceIso(R.string.late_h, fmt.number(group.minutesLate / 60))
            else stringResourceIso(R.string.late_min, fmt.number(group.minutesLate))
        else -> ""
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, MaterialTheme.shapes.large)
            .then(if (outline != Color.Transparent) Modifier.border(Dimens.ChipBorder, outline, MaterialTheme.shapes.large) else Modifier)
            .padding(Space.S12),
        verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            BlockIcon(block, fg, Modifier.padding(end = Space.S8))
            Text(blockName(block, afternoon = true), style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.weight(1f))
            Text(timeText + suffix, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.End)
        }
        if (group.state == GroupState.LATE) {
            Row(
                Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(20.dp))
                Text(stringResourceIso(R.string.late_banner, timeText), style = MaterialTheme.typography.labelMedium, color = c.warning)
            }
        }
        group.rows.forEach { row -> DoseRowItem(row, group, fmt, fg, onTake, onSkip) }
        if (group.actionable && group.pending.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                PrimaryButton(stringResource(R.string.take_all), { onTake(group.pending) }, Modifier.weight(1f))
                OutlineButton(stringResource(R.string.skip_all), { onSkip(group.pending) }, Modifier.weight(1f))
            }
        }
    }
}

/** The medicine's shape as the design draws it: a blue round tablet with a dash, an orange diagonal capsule. */
@Composable
internal fun PillIcon(unit: DoseUnit, tint: Color? = null, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val ill = PillBox.illustration
    val white = PillBox.colors.card
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        when (unit) {
            DoseUnit.CAPSULE -> {
                val color = tint ?: ill.capsule
                rotate(-45f, center) {
                    val left = androidx.compose.ui.graphics.Path().apply {
                        moveTo(12 * u, 7 * u); lineTo(6.5f * u, 7 * u)
                        arcTo(androidx.compose.ui.geometry.Rect(1.5f * u, 7 * u, 11.5f * u, 17 * u), 270f, -180f, false)
                        lineTo(12 * u, 17 * u); close()
                    }
                    drawPath(left, color)
                    drawRoundRect(color, Offset(1.5f * u, 7 * u), Size(21 * u, 10 * u), CornerRadius(5 * u), Stroke(1.6f * u))
                }
            }
            else -> {
                drawCircle(tint ?: ill.tablet, 9.5f * u, center)
                drawLine(white, Offset(5.5f * u, 12 * u), Offset(18.5f * u, 12 * u), 2 * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun foodText(f: WithFood): String? = when (f) {
    WithFood.ANY -> null
    WithFood.BEFORE_MEAL -> stringResource(R.string.food_line_before)
    WithFood.WITH_MEAL -> stringResource(R.string.food_line_with)
    WithFood.AFTER_MEAL -> stringResource(R.string.food_line_after)
}

@Composable
private fun reasonText(code: String?): String? = when (SkipReason.fromCode(code)) {
    SkipReason.NOT_FEELING_WELL -> stringResource(R.string.reason_not_well)
    SkipReason.SIDE_EFFECTS -> stringResource(R.string.reason_side_effects)
    SkipReason.RAN_OUT -> stringResource(R.string.reason_ran_out)
    SkipReason.DOCTOR_SAID -> stringResource(R.string.reason_doctor)
    SkipReason.OTHER -> stringResource(R.string.reason_other)
    null -> null
}

@Composable
private fun DoseRowItem(
    row: DoseRow, group: DoseGroup, fmt: Formatter, groupInk: Color, onTake: (List<DoseRow>) -> Unit, onSkip: (List<DoseRow>) -> Unit,
) {
    val c = PillBox.colors
    val med = row.medicine
    val amount = fmt.amount(med.doseAmount) + " " + pluralStringResource(unitPlural(med.unit), quantityFor(med.doseAmount))
    val sep = stringResource(R.string.dot_sep)
    val status = row.status
    val skipped = status == RowStatus.SKIPPED
    val subline = buildList {
        if (skipped) {
            add(stringResource(R.string.skipped))
            reasonText(row.event?.skipReason)?.let { add(it) }
        } else {
            add(amount)
            foodText(row.schedule.withFood)?.let { add(it) }
        }
    }.joinToString(sep)

    val plannedText = fmt.time(row.planned.toLocalTime())
    val statusText = when (status) {
        RowStatus.TAKEN -> stringResourceIso(R.string.said_taken_at, fmt.time(row.event?.taken?.toLocalTime() ?: row.planned.toLocalTime()))
        RowStatus.SKIPPED -> stringResource(R.string.skipped)
        RowStatus.PENDING -> if (row.missed) stringResource(R.string.missed) else stringResource(R.string.said_not_taken)
    }
    val sentence = listOf(med.name, amount, foodText(row.schedule.withFood), plannedText, statusText).filterNotNull().joinToString(", ")
    val actionable = group.actionable && status == RowStatus.PENDING
    val big = LocalDensity.current.fontScale > 1.3f

    val text: @Composable (Modifier) -> Unit = { m ->
        Row(m.semantics(mergeDescendants = true) { contentDescription = sentence }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S10)) {
            PillIcon(med.unit, if (status == RowStatus.TAKEN) c.textMuted else null, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(subline, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
    val trailing: @Composable () -> Unit = {
        when {
            status == RowStatus.TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S4))
                Text(fmt.time(row.event?.taken?.toLocalTime() ?: row.planned.toLocalTime()), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            }
            skipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S4))
                Text(stringResource(R.string.skipped), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
            row.missed && !actionable -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S4))
                Text(stringResource(R.string.missed), style = MaterialTheme.typography.labelLarge, color = c.warning)
            }
            actionable -> Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                RowButton(stringResource(R.string.take), stringResourceIso(R.string.take_med, med.name), filled = true) { onTake(listOf(row)) }
                RowButton(stringResource(R.string.skip), stringResourceIso(R.string.skip_med, med.name), filled = false) { onSkip(listOf(row)) }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().background(groupInk.copy(alpha = 0.08f), MaterialTheme.shapes.medium).padding(Space.S10),
        verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        if (big) {
            text(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { trailing() }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                text(Modifier.weight(1f))
                trailing()
            }
        }
    }
}

@Composable
internal fun RowButton(label: String, description: String, filled: Boolean, onClick: () -> Unit) {
    val c = PillBox.colors
    Box(
        Modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .widthIn(min = 64.dp)
            .background(if (filled) c.primary else Color.Transparent, Dimens.ButtonShape)
            .border(Dimens.ChipBorder, if (filled) c.primary else c.border, Dimens.ButtonShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = Space.S12, vertical = Space.S8),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) c.onPrimary else c.text, maxLines = 1) }
}

@Composable
private fun InfoNote(text: String) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- all medicines paused

@Composable
private fun AllPausedToday(
    today: LocalDate, paused: com.pillbox.domain.AllPaused, fmt: Formatter,
    onResume: () -> Unit, onGoToMeds: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = PillBox.colors
    val until = paused.until
    val sub = if (until == null) stringResource(R.string.today_paused_sub_open)
    else stringResource(
        R.string.today_paused_sub, com.pillbox.ui.shortDate(until.toLocalDate(), fmt), fmt.time(java.time.LocalTime.of(Pause.AUTO_RESUME_HOUR, 0)),
    )
    val banner = stringResource(R.string.today_paused_banner)
    val link = stringResource(R.string.today_paused_link)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.ScreenPaddingH)) {
        Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = Space.S4).semantics { heading() })
        Text(fmt.digits(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale()))), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Spacer(Modifier.size(Space.S12))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.TouchTargetMin)
                .background(c.warningContainer, MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onGoToMeds)
                .semantics(mergeDescendants = true) { contentDescription = "$banner $link" }
                .padding(Space.S12),
            horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
        ) {
            com.pillbox.ui.medicines.PauseGlyph(c.warning, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(banner, style = MaterialTheme.typography.labelLarge, color = c.warning)
                Text(link, style = MaterialTheme.typography.bodyMedium, color = c.warning, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(80.dp).background(c.warningContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                com.pillbox.ui.medicines.PauseGlyph(c.warning, Modifier.size(40.dp))
            }
            Spacer(Modifier.size(Space.S16))
            Text(stringResource(R.string.today_paused_title), style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.size(Space.S8))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
        }
        PrimaryButton(stringResource(R.string.btn_resume), onResume)
        Spacer(Modifier.size(Space.S12))
    }
}

// ---------------------------------------------------------------- empty

@Composable
private fun EmptyToday(today: LocalDate, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.ScreenPaddingH)) {
        Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = Space.S4).semantics { heading() })
        Text(
            rememberFormatter().digits(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale()))),
            style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
        )
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val dash = c.border
            Canvas(Modifier.size(width = 140.dp, height = 80.dp)) {
                drawRoundRect(dash, Offset.Zero, size, CornerRadius(14.dp.toPx()), Stroke(2.dp.toPx()))
                val slot = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                for (i in 0..3) {
                    drawRoundRect(dash, Offset(12.dp.toPx() + i * 30.dp.toPx(), 12.dp.toPx()), Size(24.dp.toPx(), size.height - 24.dp.toPx()), CornerRadius(8.dp.toPx()), slot)
                }
            }
            Spacer(Modifier.size(Space.S20))
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.size(Space.S8))
            Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.size(Space.S16))
            PrimaryButton(stringResource(R.string.add_medicine), onAdd, Modifier.width(220.dp))
        }
    }
}

