package com.pillbox.ui.medicines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.domain.DayMark
import com.pillbox.domain.Frequency
import com.pillbox.domain.MedicineStatus
import com.pillbox.domain.StockLevel
import com.pillbox.domain.WeekSummary
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.DangerOutlineButton
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.dayMonth
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun MedicineDetailScreen(
    vm: MedicineDetailViewModel,
    onBack: () -> Unit,
    /** Stop or Restart finished: go back to the list and show this toast there. */
    onDone: (ListToast, finishedTab: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    val bg = MaterialTheme.colorScheme.background
    BackHandler(onBack = onBack)

    val item = state.item
    if (item == null) {
        if (state.loaded) LaunchedEffect(Unit) { onBack() }
        Box(modifier.fillMaxSize().background(bg))
        return
    }
    val m = item.medicine
    val active = m.status == MedicineStatus.ACTIVE

    Box(modifier.fillMaxSize().background(bg)) {
        MedicineDetailContent(
            state, fmt, onBack, onAddStock = { vm.showStock(true) }, onStop = { vm.showStop(true) },
            onRestart = {
                vm.restart { name ->
                    onDone(ListToast(res.getString(R.string.toast_restarted_title, name), res.getString(R.string.toast_restarted_body)), false)
                }
            },
        )
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }

    if (state.showStop) {
        MessageDialog(DialogKind.Danger, stringResource(R.string.stop_title, m.name), stringResource(R.string.stop_body), onDismiss = { vm.showStop(false) }) {
            DangerButton(stringResource(R.string.stop_confirm), {
                vm.stop { name ->
                    onDone(ListToast(res.getString(R.string.toast_stopped_title, name), res.getString(R.string.toast_stopped_body)), true)
                }
            })
            OutlineButton(stringResource(R.string.stop_keep), { vm.showStop(false) })
        }
    }
    if (state.showStock) {
        AddStockSheet(
            medicine = m, schedule = item.schedule, fmt = fmt,
            onSave = { boxes, perBox ->
                vm.addStock(boxes, perBox) { result ->
                    val (title, body) = stockToastText(res, fmt, m.unit, result)
                    scope.launch { snackbar.showToast(title, body) }
                }
            },
            onDismiss = { vm.showStock(false) },
        )
    }
}

/** The detail screen without its view model, so previews and screenshot tests can draw it. */
@Composable
fun MedicineDetailContent(
    state: DetailUiState,
    fmt: Formatter,
    onBack: () -> Unit = {},
    onAddStock: () -> Unit = {},
    onStop: () -> Unit = {},
    onRestart: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val item = state.item ?: return
    Box(modifier) {
        if (item.medicine.status == MedicineStatus.ACTIVE) ActiveDetail(state, item, fmt, onBack, onAddStock, onStop)
        else FinishedDetail(state, item, fmt, onBack, onRestart)
    }
}

// ---------------------------------------------------------------- active medicine

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val c = PillBox.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text) }
        Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun ActiveDetail(
    state: DetailUiState,
    item: MedicineWithSchedule,
    fmt: Formatter,
    onBack: () -> Unit,
    onAddStock: () -> Unit,
    onStop: () -> Unit,
) {
    val c = PillBox.colors
    val m = item.medicine
    val schedule = item.schedule
    val info = state.info
    val sep = stringResource(R.string.dot_sep)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.detail_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                MedicineBadge(m.unit, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text(
                        listOfNotNull(m.strength, doseText(m, fmt), lengthText(schedule, fmt)).joinToString(sep),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
            }
            StatTiles(item, info?.daysLeft, info?.level ?: StockLevel.ENOUGH, fmt)
            if (schedule != null) ScheduleCard(item, fmt)
            WeekCard(state.week, fmt)
        }
        Column(Modifier.padding(Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Space.S10)) {
            PrimaryButton(stringResource(R.string.btn_add_stock), onAddStock)
            DangerOutlineButton(stringResource(R.string.btn_stop), onStop)
        }
    }
}

@Composable
private fun StatTiles(item: MedicineWithSchedule, daysLeft: Int?, level: StockLevel, fmt: Formatter) {
    val c = PillBox.colors
    val m = item.medicine
    val perDay = item.schedule?.let { com.pillbox.domain.StockMath.averageDosesPerDay(it) }
    val lowTint = level != StockLevel.ENOUGH
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
        Tile(
            Modifier.weight(1f),
            big = if (daysLeft != null) fmt.number(daysLeft) else "—",
            small = if (daysLeft != null) pluralStringResource(R.plurals.tile_days_left, daysLeft.coerceAtLeast(0)) else stringResource(R.string.stock_enough),
            bg = if (lowTint) c.warningContainer else c.card, ink = if (lowTint) c.warning else c.text,
        )
        Tile(
            Modifier.weight(1f), big = fmt.amount(m.stock),
            small = pluralStringResource(com.pillbox.ui.unitPlural(m.unit), com.pillbox.ui.quantityFor(m.stock)), bg = c.card, ink = c.text,
        )
        Tile(
            Modifier.weight(1f),
            big = if (perDay != null) stringResource(R.string.tile_per_day, fmt.amount(Math.round(perDay * 10) / 10.0)) else "—",
            small = if (perDay != null) stringResource(R.string.tile_use) else stringResource(R.string.tile_as_needed), bg = c.card, ink = c.text,
        )
    }
}

@Composable
private fun Tile(modifier: Modifier, big: String, small: String, bg: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    val c = PillBox.colors
    Column(
        modifier
            .heightIn(min = 88.dp)
            .background(bg, MaterialTheme.shapes.large)
            .padding(Space.S10)
            .semantics(mergeDescendants = true) { contentDescription = "$big $small" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(big, style = MaterialTheme.typography.headlineSmall, color = ink, textAlign = TextAlign.Center)
        Text(small, style = MaterialTheme.typography.bodySmall, color = if (bg == c.card) c.textMuted else ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScheduleCard(item: MedicineWithSchedule, fmt: Formatter) {
    val c = PillBox.colors
    val schedule = item.schedule ?: return
    val times: List<LocalTime> = when (val f = schedule.frequency) {
        is Frequency.FixedTimes -> f.times.sorted()
        is Frequency.EveryNHours -> (0 until 24 / f.hours).map { f.first.plusHours((it * f.hours).toLong()) }.sorted()
        is Frequency.AsNeeded -> emptyList()
    }
    val summary = scheduleSummary(schedule, fmt)
    val timesText = times.joinToString(", ") { fmt.time(it) }
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        Text(stringResource(R.string.sched_title), style = MaterialTheme.typography.labelMedium, color = c.textMuted, modifier = Modifier.semantics { heading() })
        if (times.isNotEmpty()) {
            DayBar(times, fmt)
            DayLegend()
        } else {
            Text(stringResource(R.string.sched_prn), style = MaterialTheme.typography.bodyMedium, color = c.text)
        }
        Text(
            summary, style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            modifier = Modifier.semantics { contentDescription = if (timesText.isEmpty()) summary else "$summary. $timesText" },
        )
    }
}

/** The 24-hour bar with the four parts of the day and a ring at each dose time. Mirrors in right-to-left. */
@Composable
private fun DayBar(times: List<LocalTime>, fmt: Formatter) {
    val c = PillBox.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val segments = listOf(0 to c.night, 5 to c.morning, 12 to c.noon, 17 to c.evening, 21 to c.night)
    val ink = c.text
    val white = c.card
    fun frac(t: LocalTime) = (t.hour * 60 + t.minute) / 1440f
    fun at(f: Float) = if (rtl) 1f - f else f
    val label = times.size <= 4
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(if (label) 66.dp else 36.dp)) {
            val w = maxWidth
            if (label) {
                times.forEach { t ->
                    val x = (w * at(frac(t)) - 40.dp).coerceIn(0.dp, w - 80.dp)
                    Text(
                        fmt.time(t), style = MaterialTheme.typography.labelMedium, color = ink, textAlign = TextAlign.Center,
                        modifier = Modifier.offset(x = x).width(80.dp),
                    )
                }
            }
            Canvas(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomStart)) {
                val barH = 18.dp.toPx()
                val top = (size.height - barH) / 2
                fun x(f: Float) = at(f) * size.width
                for (i in segments.indices) {
                    val from = segments[i].first / 24f
                    val to = (segments.getOrNull(i + 1)?.first ?: 24) / 24f
                    val x1 = x(from); val x2 = x(to)
                    drawRect(segments[i].second, Offset(minOf(x1, x2), top), Size(kotlin.math.abs(x2 - x1), barH))
                }
                drawRoundRect(ink.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, barH), androidx.compose.ui.geometry.CornerRadius(barH / 2), Stroke(2.dp.toPx()))
                for (t in times) {
                    val center = Offset(x(frac(t)).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()), size.height / 2)
                    drawCircle(white, 10.dp.toPx(), center)
                    drawCircle(ink, 10.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 0).forEach { h -> Text(fmt.time(LocalTime.of(h, 0)), style = MaterialTheme.typography.labelSmall, color = c.textMuted) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayLegend() {
    val c = PillBox.colors
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        listOf(
            com.pillbox.ui.Block.NIGHT to c.night, com.pillbox.ui.Block.MORNING to c.morning,
            com.pillbox.ui.Block.NOON to c.noon, com.pillbox.ui.Block.EVENING to c.evening,
        ).forEach { (block, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(color, CircleShape).border(1.dp, c.textMuted.copy(alpha = 0.4f), CircleShape))
                Box(Modifier.width(Space.S6))
                Text(com.pillbox.ui.blockName(block), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun WeekCard(week: WeekSummary?, fmt: Formatter) {
    val c = PillBox.colors
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        Text(stringResource(R.string.week_title), style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.semantics { heading() })
        if (week != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { d ->
                    val say = shortDate(d.date, fmt)
                    val desc = when (d.mark) {
                        DayMark.ALL_TAKEN -> stringResource(R.string.week_day_taken, say)
                        DayMark.MISSED -> stringResource(R.string.week_day_missed, say)
                        DayMark.PENDING -> stringResource(R.string.week_day_pending, say)
                        DayMark.NONE -> stringResource(R.string.week_day_none, say)
                    }
                    DayCircle(d.mark, desc)
                }
            }
            val text = if (week.counted == 0) stringResource(R.string.week_none) else buildString {
                append(stringResource(R.string.week_taken, fmt.number(week.taken), fmt.number(week.counted)))
                if (week.missedDays.isNotEmpty()) {
                    val n = week.missedDays.size
                    val dayName = week.missedDays.first().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    append(" ")
                    append(pluralStringResource(R.plurals.week_missed, n, fmt.number(n), dayName))
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

/** Check, cross or dashed ring. The symbol, not just the color, tells them apart. */
@Composable
private fun DayCircle(mark: DayMark, description: String) {
    val c = PillBox.colors
    val dash = c.placeholder
    Box(
        Modifier.size(36.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            DayMark.ALL_TAKEN -> Box(Modifier.size(36.dp).background(c.successContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
            }
            DayMark.MISSED -> Box(Modifier.size(36.dp).background(c.dangerContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
            }
            DayMark.PENDING, DayMark.NONE -> Canvas(Modifier.size(36.dp)) {
                drawCircle(
                    dash, radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(2.dp.toPx(), pathEffect = if (mark == DayMark.PENDING) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null),
                    alpha = if (mark == DayMark.NONE) 0.35f else 1f,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- finished or stopped medicine

@Composable
private fun FinishedDetail(
    state: DetailUiState,
    item: MedicineWithSchedule,
    fmt: Formatter,
    onBack: () -> Unit,
    onRestart: () -> Unit,
) {
    val c = PillBox.colors
    val m = item.medicine
    val course = m.status == MedicineStatus.FINISHED
    val title = listOfNotNull(m.name, m.strength).joinToString(" ")
    val ended = m.endedOn
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.back), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                if (ended != null) {
                    Text(
                        stringResource(if (course) R.string.finished_on else R.string.stopped_on, dayMonth(ended, fmt)),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().background(c.successContainer, MaterialTheme.shapes.medium).padding(Space.S16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(if (course) R.string.fin_banner_course else R.string.fin_banner_stopped),
                    style = MaterialTheme.typography.bodyMedium, color = c.success, modifier = Modifier.weight(1f),
                )
            }
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(horizontal = Space.S16)) {
                FactRow(stringResource(R.string.fin_dose), doseText(m, fmt))
                HorizontalDivider(color = c.divider)
                FactRow(stringResource(R.string.fin_schedule), item.schedule?.let { scheduleSummary(it, fmt) } ?: "")
                HorizontalDivider(color = c.divider)
                FactRow(
                    stringResource(R.string.fin_taken),
                    if (state.courseTotal > 0) stringResource(R.string.fin_taken_value, fmt.number(state.courseTaken), fmt.number(state.courseTotal))
                    else pluralStringResource(R.plurals.n_doses, state.courseTaken, fmt.number(state.courseTaken)),
                )
            }
        }
        Column(Modifier.padding(Dimens.ScreenPaddingH)) { PrimaryButton(stringResource(R.string.btn_restart), onRestart) }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.ListRowMinHeight).padding(vertical = Space.S8)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}
