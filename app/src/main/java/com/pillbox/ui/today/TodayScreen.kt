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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            onTake = vm::take, onSkip = vm::skip, onLog = vm::logTaken, onAddMedicine = onAddMedicine,
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
    onLog: (DoseRow) -> Unit = {},
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
    var showLow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showLow && state.lowStock.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLow = false }) {
            LowStockCard(state.lowStock, onPick = { showLow = false; onGoToMeds() }, onClose = { showLow = false })
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize()) {
        // The title and the week strip stay in place while the list scrolls.
        Column(
            Modifier.fillMaxWidth().padding(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { Header(state.selectedDate, today, model, fmt) }
                com.pillbox.ui.people.PeopleBarSwitcher(people)
            }
            if (state.lowStock.isNotEmpty()) LowStockBar(state.lowStock.size) { showLow = true }
            WeekStrip(state.selectedDate, today, fmt, onSelectDate)
        }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.CardGap, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
    ) {
        if (people?.active?.isMe == false) item { com.pillbox.ui.people.ViewingBanner(people) }
        if (remindersOff) item { RemindersOffBanner(onFixReminders) }
        if (state.selectedDate != today) item { BackToToday(onBackToToday) }
        if (state.selectedDate == today && model != null && model.groups.isNotEmpty()) item { DayTimeline(model, fmt) }
        if (model != null) {
            if (model.groups.isEmpty() && state.loaded) {
                item { InfoNote(stringResource(R.string.nothing_planned)) }
            }
            items(model.groups, key = { it.time.toString() }) { group ->
                GroupCard(group, fmt, state.now, onTake, onSkip, onLog)
            }
            if (state.selectedDate > today && model.groups.isNotEmpty()) item { InfoNote(stringResource(R.string.future_note)) }
        }
        if (state.selectedDate == today && state.asNeeded.isNotEmpty()) item { AsNeededCard(state.asNeeded, onTakeAsNeeded) }
    }
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

/** One strip above the week days: "2 medicines are running low". Opens the list. */
@Composable
internal fun LowStockBar(count: Int, onClick: () -> Unit) {
    val c = PillBox.colors
    val text = pluralStringResource(R.plurals.low_stock_count, count, com.pillbox.ui.components.isolate(rememberFormatter().number(count)))
    val hint = stringResource(R.string.low_stock_see_list)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTargetMin)
            .background(c.warningContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$text. $hint" }
            .padding(horizontal = Space.S12, vertical = Space.S6),
        horizontalArrangement = Arrangement.spacedBy(Space.S10), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(com.pillbox.ui.components.DesignIcons.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        Icon(com.pillbox.ui.components.DesignIcons.Chevron, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
    }
}

/** The popup with every medicine that is running low: "Metformin · 4 days left" and "Buy soon" or "Update my stock". Tapping a row opens Medicines. */
@Composable
internal fun LowStockCard(items: List<LowStock>, onPick: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val fmt = rememberFormatter()
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Column(modifier.fillMaxWidth().heightIn(max = maxHeight).background(c.card, MaterialTheme.shapes.extraLarge).padding(vertical = Space.S16)) {
        Text(
            stringResource(R.string.low_stock_title), style = MaterialTheme.typography.titleMedium, color = c.text,
            modifier = Modifier.padding(horizontal = Space.S16).padding(bottom = Space.S8).semantics { heading() },
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Space.S8)) {
            items.forEach { item ->
                val what = if (item.level == StockLevel.BUY_SOON && item.daysLeft != null)
                    pluralStringResource(R.plurals.stock_days_left, item.daysLeft.coerceAtLeast(0), com.pillbox.ui.components.isolate(fmt.number(item.daysLeft)))
                else stringResource(R.string.stock_out)
                val label = stringResource(if (item.level == StockLevel.OUT) R.string.out_update else R.string.stock_buy_soon)
                val text = stringResourceIso(R.string.low_stock_strip, item.name, what)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ListRowMinHeight)
                        .clickable(role = Role.Button, onClick = onPick)
                        .semantics(mergeDescendants = true) { contentDescription = "$text. $label" }
                        .padding(horizontal = Space.S8, vertical = Space.S6),
                    horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillIcon(item.unit, null, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = c.warning)
                    }
                    Icon(com.pillbox.ui.components.DesignIcons.Chevron, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.S16).padding(top = Space.S8)) { com.pillbox.ui.components.LinkButton(stringResource(R.string.low_stock_close), onClose) }
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
    val subText = when {
        model == null -> date
        selected > today -> stringResourceIso(R.string.sub_planned, date, pluralStringResource(R.plurals.n_doses, model.total, fmt.number(model.total)))
        else -> stringResourceIso(R.string.sub_taken, date, fmt.number(model.taken), fmt.number(model.total))
    }
    // "Wed 21 Oct - (0 of 2 taken)": the part in brackets is bold.
    val sub = androidx.compose.ui.text.buildAnnotatedString {
        append(subText)
        val open = subText.indexOf('(')
        val close = subText.lastIndexOf(')')
        if (open >= 0 && close > open) addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), open, close + 1)
    }
    Column(Modifier.fillMaxWidth().padding(top = Space.S4)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

/** Days shown in the strip: two weeks back to about two months ahead. Swipe sideways to see more. */
private const val STRIP_DAYS_BACK = 14L
private const val STRIP_DAYS_AHEAD = 62L

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, fmt: Formatter, onSelect: (LocalDate) -> Unit) {
    val c = PillBox.colors
    val first = com.pillbox.ui.firstDayOfWeek()
    val weekStart = today.minusDays(((today.dayOfWeek.value - first.value + 7) % 7).toLong())
    val begin = weekStart.minusDays(STRIP_DAYS_BACK)
    val count = (STRIP_DAYS_BACK + 7 + STRIP_DAYS_AHEAD).toInt()
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = STRIP_DAYS_BACK.toInt())
    // Picking a day outside what is on screen (Back to today, a date from elsewhere) brings it into view.
    LaunchedEffect(selected) {
        val index = java.time.temporal.ChronoUnit.DAYS.between(begin, selected).toInt()
        val visible = state.layoutInfo.visibleItemsInfo
        if (index in 0 until count && (visible.isEmpty() || index < visible.first().index || index > visible.last().index)) {
            state.animateScrollToItem((index - 3).coerceAtLeast(0))
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Seven days fit across the screen, like a week; the rest is a swipe away.
        val gap = Space.S4
        val itemWidth = (maxWidth - gap * 6) / 7
        androidx.compose.foundation.lazy.LazyRow(
            state = state, modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(count) { i ->
                val day = begin.plusDays(i.toLong())
                val on = day == selected
                val full = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale()) + " " + fmt.number(day.dayOfMonth)
                Column(
                    Modifier
                        .width(itemWidth)
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
        Icon(com.pillbox.ui.components.DesignIcons.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(24.dp))
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
private fun GroupCard(group: DoseGroup, fmt: Formatter, now: java.time.LocalDateTime, onTake: (List<DoseRow>) -> Unit, onSkip: (List<DoseRow>) -> Unit, onLog: (DoseRow) -> Unit) {
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
                Icon(com.pillbox.ui.components.DesignIcons.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(20.dp))
                Text(stringResourceIso(R.string.late_banner, timeText), style = MaterialTheme.typography.labelMedium, color = c.warning)
            }
        }
        group.rows.forEach { row -> DoseRowItem(row, group, fmt, fg, now, onTake, onSkip, onLog) }
        if (group.actionable && group.pending.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                PrimaryButton(stringResource(R.string.take_all), { onTake(group.pending) }, Modifier.weight(1f))
                OutlineButton(stringResource(R.string.skip_all), { onSkip(group.pending) }, Modifier.weight(1f))
            }
        }
    }
}

/** The medicine's shape, one per type, each with its own color from the illustration tokens. */
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
            DoseUnit.ML -> {
                // Syrup: a bottle with a cap and a white label band.
                val color = tint ?: ill.syrup
                drawRoundRect(color, Offset(8 * u, 2 * u), Size(8 * u, 3.5f * u), CornerRadius(1.2f * u))
                drawRect(color, Offset(9.5f * u, 5 * u), Size(5 * u, 4.5f * u))
                drawRoundRect(color, Offset(5.5f * u, 9 * u), Size(13 * u, 13 * u), CornerRadius(3.5f * u))
                drawRoundRect(white, Offset(8 * u, 13.5f * u), Size(8 * u, 5 * u), CornerRadius(1f * u))
            }
            DoseUnit.INJECTION -> {
                // Syringe: barrel, plunger and needle, tilted.
                val color = tint ?: ill.injection
                rotate(-45f, center) {
                    drawRoundRect(color, Offset(6 * u, 8.5f * u), Size(11 * u, 7 * u), CornerRadius(1.5f * u))
                    drawLine(color, Offset(17 * u, 7 * u), Offset(17 * u, 17 * u), 1.8f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(color, Offset(17 * u, 12 * u), Offset(23 * u, 12 * u), 1.2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(color, Offset(1.8f * u, 12 * u), Offset(6 * u, 12 * u), 1.8f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(color, Offset(1.8f * u, 9 * u), Offset(1.8f * u, 15 * u), 1.8f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(white, Offset(9 * u, 10.5f * u), Offset(9 * u, 13.5f * u), 1.2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(white, Offset(12 * u, 10.5f * u), Offset(12 * u, 13.5f * u), 1.2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(white, Offset(15 * u, 10.5f * u), Offset(15 * u, 13.5f * u), 1.2f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            DoseUnit.DROPS -> {
                // Drops: one drop with a white highlight.
                val color = tint ?: ill.drops
                val drop = androidx.compose.ui.graphics.Path().apply {
                    moveTo(12 * u, 2.5f * u)
                    cubicTo(12 * u, 2.5f * u, 5 * u, 10.5f * u, 5 * u, 15 * u)
                    arcTo(androidx.compose.ui.geometry.Rect(5 * u, 8 * u, 19 * u, 22 * u), 180f, -180f, false)
                    cubicTo(19 * u, 10.5f * u, 12 * u, 2.5f * u, 12 * u, 2.5f * u)
                    close()
                }
                drawPath(drop, color)
                drawLine(white, Offset(8.8f * u, 14.5f * u), Offset(8.8f * u, 17.5f * u), 1.6f * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            DoseUnit.OTHER -> {
                // Other: a rounded square with a plus.
                val color = tint ?: ill.other
                drawRoundRect(color, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), CornerRadius(5 * u))
                drawLine(white, Offset(12 * u, 7.5f * u), Offset(12 * u, 16.5f * u), 2 * u, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(white, Offset(7.5f * u, 12 * u), Offset(16.5f * u, 12 * u), 2 * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            DoseUnit.POWDER -> {
                // Powder or granules: a sachet with a serrated top and a few grains.
                val color = tint ?: ill.powder
                val sachet = androidx.compose.ui.graphics.Path().apply {
                    moveTo(5 * u, 5 * u); lineTo(7 * u, 3 * u); lineTo(9 * u, 5 * u); lineTo(11 * u, 3 * u); lineTo(13 * u, 5 * u)
                    lineTo(15 * u, 3 * u); lineTo(17 * u, 5 * u); lineTo(19 * u, 3 * u); lineTo(19 * u, 20 * u)
                    lineTo(5 * u, 20 * u); close()
                }
                drawPath(sachet, color)
                drawCircle(white, 1.3f * u, Offset(9 * u, 11 * u)); drawCircle(white, 1.3f * u, Offset(14.5f * u, 12.5f * u))
                drawCircle(white, 1.3f * u, Offset(11 * u, 16 * u)); drawCircle(white, 1.1f * u, Offset(15.5f * u, 17 * u))
            }
            DoseUnit.SOLUTION -> {
                // Solution: a flask with a wave of liquid.
                val color = tint ?: ill.solution
                val flask = androidx.compose.ui.graphics.Path().apply {
                    moveTo(9 * u, 2.5f * u); lineTo(15 * u, 2.5f * u); lineTo(15 * u, 9 * u); lineTo(20.5f * u, 19 * u)
                    lineTo(20.5f * u, 20.5f * u); lineTo(3.5f * u, 20.5f * u); lineTo(3.5f * u, 19 * u); lineTo(9 * u, 9 * u); close()
                }
                drawPath(flask, color)
                drawLine(white, Offset(6.5f * u, 15.5f * u), Offset(17.5f * u, 15.5f * u), 1.6f * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            DoseUnit.SPRAY -> {
                // Spray: a bottle with a nozzle and three puffs.
                val color = tint ?: ill.spray
                drawRoundRect(color, Offset(11 * u, 8 * u), Size(9 * u, 14 * u), CornerRadius(2.5f * u))
                drawRoundRect(color, Offset(13 * u, 3.5f * u), Size(5 * u, 4 * u), CornerRadius(1f * u))
                drawRect(color, Offset(9.5f * u, 4 * u), Size(4 * u, 2 * u))
                drawCircle(color, 1.1f * u, Offset(6 * u, 4.5f * u)); drawCircle(color, 1.1f * u, Offset(4 * u, 7 * u)); drawCircle(color, 1.1f * u, Offset(6 * u, 9.5f * u))
                drawRoundRect(white, Offset(13 * u, 13 * u), Size(5 * u, 5 * u), CornerRadius(1f * u))
            }
            DoseUnit.INHALER -> {
                // Inhaler: the L shape of a puffer, a body and a mouthpiece.
                val color = tint ?: ill.inhaler
                drawRoundRect(color, Offset(6 * u, 2 * u), Size(9 * u, 14 * u), CornerRadius(2f * u))
                drawRoundRect(color, Offset(6 * u, 13 * u), Size(15 * u, 8 * u), CornerRadius(2.5f * u))
                drawCircle(white, 1.6f * u, Offset(10.5f * u, 17 * u))
                drawLine(white, Offset(9 * u, 6 * u), Offset(12 * u, 6 * u), 1.4f * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            DoseUnit.SUPPOSITORY -> {
                // Suppository: a bullet shape, tilted.
                val color = tint ?: ill.suppository
                rotate(-45f, center) {
                    val bullet = androidx.compose.ui.graphics.Path().apply {
                        moveTo(3 * u, 9 * u); lineTo(13 * u, 9 * u)
                        cubicTo(18 * u, 9 * u, 21.5f * u, 11 * u, 21.5f * u, 12 * u)
                        cubicTo(21.5f * u, 13 * u, 18 * u, 15 * u, 13 * u, 15 * u)
                        lineTo(3 * u, 15 * u); close()
                    }
                    drawPath(bullet, color)
                    drawLine(white, Offset(8 * u, 10.8f * u), Offset(8 * u, 13.2f * u), 1.3f * u, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            DoseUnit.CREAM -> {
                // Cream: a squeeze tube with a cap and a crimped end.
                val color = tint ?: ill.cream
                drawRoundRect(color, Offset(9 * u, 2 * u), Size(6 * u, 4.5f * u), CornerRadius(1f * u))
                val tube = androidx.compose.ui.graphics.Path().apply {
                    moveTo(7 * u, 6.5f * u); lineTo(17 * u, 6.5f * u); lineTo(15.5f * u, 19 * u); lineTo(8.5f * u, 19 * u); close()
                }
                drawPath(tube, color)
                drawRect(color, Offset(7.5f * u, 19 * u), Size(9 * u, 3 * u))
                drawLine(white, Offset(8 * u, 12 * u), Offset(16 * u, 12 * u), 1.6f * u, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            DoseUnit.OINTMENT -> {
                // Ointment: a jar with a lid and a label band.
                val color = tint ?: ill.ointment
                drawRoundRect(color, Offset(4 * u, 4 * u), Size(16 * u, 5 * u), CornerRadius(1.5f * u))
                drawRoundRect(color, Offset(5 * u, 9 * u), Size(14 * u, 12 * u), CornerRadius(3f * u))
                drawRoundRect(white, Offset(8 * u, 12.5f * u), Size(8 * u, 5 * u), CornerRadius(1f * u))
            }
            DoseUnit.GEL -> {
                // Gel or lotion: a pump bottle.
                val color = tint ?: ill.gel
                drawRoundRect(color, Offset(8 * u, 2 * u), Size(9 * u, 3 * u), CornerRadius(1.2f * u))
                drawRect(color, Offset(11 * u, 4.5f * u), Size(3 * u, 3 * u))
                drawRoundRect(color, Offset(6 * u, 7 * u), Size(13 * u, 15 * u), CornerRadius(3.5f * u))
                val drop = androidx.compose.ui.graphics.Path().apply {
                    moveTo(12.5f * u, 11 * u); cubicTo(12.5f * u, 11 * u, 9.5f * u, 14.5f * u, 9.5f * u, 16.5f * u)
                    arcTo(androidx.compose.ui.geometry.Rect(9.5f * u, 14 * u, 15.5f * u, 20 * u), 180f, -180f, false)
                    cubicTo(15.5f * u, 14.5f * u, 12.5f * u, 11 * u, 12.5f * u, 11 * u); close()
                }
                drawPath(drop, white)
            }
            DoseUnit.PATCH -> {
                // Medicated patch: a bandage shape with a white pad.
                val color = tint ?: ill.patch
                rotate(-35f, center) {
                    drawRoundRect(color, Offset(2 * u, 6.5f * u), Size(20 * u, 11 * u), CornerRadius(5 * u))
                    drawRoundRect(white, Offset(8.5f * u, 8.5f * u), Size(7 * u, 7 * u), CornerRadius(1.5f * u))
                    drawCircle(color, 0.8f * u, Offset(10.5f * u, 10.5f * u)); drawCircle(color, 0.8f * u, Offset(13.5f * u, 13.5f * u))
                    drawCircle(color, 0.8f * u, Offset(13.5f * u, 10.5f * u)); drawCircle(color, 0.8f * u, Offset(10.5f * u, 13.5f * u))
                }
            }
            DoseUnit.TABLET -> {
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

/** "After breakfast" for the daily plan: the food rule worded with the meal nearest [time]; the plain wording when no meal is near. */
@Composable
internal fun foodTextFor(f: WithFood, time: java.time.LocalTime, meals: com.pillbox.domain.MealTimes): String? {
    val meal = meals.mealFor(f, time) ?: return foodText(f)
    val id = if (meal == com.pillbox.domain.MealTimes.Meal.BEDTIME) R.string.food_snack else when (f) {
        WithFood.BEFORE_MEAL -> when (meal) { com.pillbox.domain.MealTimes.Meal.BREAKFAST -> R.string.food_before_breakfast; com.pillbox.domain.MealTimes.Meal.LUNCH -> R.string.food_before_lunch; else -> R.string.food_before_dinner }
        WithFood.WITH_MEAL -> when (meal) { com.pillbox.domain.MealTimes.Meal.BREAKFAST -> R.string.food_with_breakfast; com.pillbox.domain.MealTimes.Meal.LUNCH -> R.string.food_with_lunch; else -> R.string.food_with_dinner }
        else -> when (meal) { com.pillbox.domain.MealTimes.Meal.BREAKFAST -> R.string.food_after_breakfast; com.pillbox.domain.MealTimes.Meal.LUNCH -> R.string.food_after_lunch; else -> R.string.food_after_dinner }
    }
    return stringResource(id)
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
    row: DoseRow, group: DoseGroup, fmt: Formatter, groupInk: Color, now: java.time.LocalDateTime,
    onTake: (List<DoseRow>) -> Unit, onSkip: (List<DoseRow>) -> Unit, onLog: (DoseRow) -> Unit,
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
            if (row.event?.loggedLater == true) add(stringResource(R.string.logged_later))
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
    val canLog = com.pillbox.domain.LateLog.canLog(row.planned, now)
    val changeTime = status == RowStatus.TAKEN && canLog
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
                Icon(com.pillbox.ui.components.DesignIcons.Check, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S4))
                Text(fmt.time(row.event?.taken?.toLocalTime() ?: row.planned.toLocalTime()), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                if (changeTime) {
                    Spacer(Modifier.width(Space.S8))
                    Icon(com.pillbox.ui.components.DesignIcons.Edit, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                }
            }
            skipped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.pillbox.ui.components.DesignIcons.Close, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S4))
                Text(stringResource(R.string.skipped), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
            row.missed && !actionable -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Icon(com.pillbox.ui.components.DesignIcons.Info, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.missed), style = MaterialTheme.typography.labelLarge, color = c.warning)
                if (canLog) RowButton(stringResource(R.string.i_took_it), stringResourceIso(R.string.i_took_it_med, med.name), filled = false) { onLog(row) }
            }
            actionable -> Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                RowButton(stringResource(R.string.take), stringResourceIso(R.string.take_med, med.name), filled = true) { onTake(listOf(row)) }
                RowButton(stringResource(R.string.skip), stringResourceIso(R.string.skip_med, med.name), filled = false) { onSkip(listOf(row)) }
            }
        }
    }
    val stack = big || (row.missed && !actionable && canLog && status == RowStatus.PENDING)
    Column(
        Modifier.fillMaxWidth().background(groupInk.copy(alpha = 0.08f), MaterialTheme.shapes.medium)
            .then(if (changeTime) Modifier.clickable(onClickLabel = stringResourceIso(R.string.change_time_med, med.name), role = Role.Button) { onLog(row) } else Modifier)
            .padding(Space.S10),
        verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        if (stack) {
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
        Icon(com.pillbox.ui.components.DesignIcons.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
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

