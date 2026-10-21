package com.pillbox.ui.history

import com.pillbox.ui.components.stringResourceIso
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.DoseOutcome
import com.pillbox.domain.DoseRecord
import com.pillbox.domain.HistoryDay
import com.pillbox.domain.HistoryMark
import com.pillbox.ui.Formatter
import com.pillbox.ui.medicines.PauseGlyph
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** History: the 30-day percentage, a calendar of the last 30 days, and the doses that need attention. */
@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onGoToToday: () -> Unit, onShareReport: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    HistoryContent(state, rememberFormatter(), com.pillbox.ui.firstDayOfWeek(), onBack, onGoToToday, onShareReport)
}

@Composable
fun HistoryContent(
    state: HistoryUiState, fmt: Formatter, firstDay: DayOfWeek, onBack: () -> Unit, onGoToToday: () -> Unit, onShareReport: () -> Unit,
) {
    val c = PillBox.colors
    val history = state.history
    val empty = state.loaded && (history == null || (history.percent == null && history.asNeeded.isEmpty()))
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(com.pillbox.ui.components.DesignIcons.Back, contentDescription = stringResource(R.string.back), tint = c.text)
            }
            Column(Modifier.weight(1f).padding(vertical = Space.S8)) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                if (!empty && state.loaded) {
                    val month = state.today.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                    Text(stringResourceIso(R.string.history_sub, state.personName, month), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }
        }
        if (empty) {
            EmptyHistory(onGoToToday, Modifier.weight(1f))
        } else if (history != null) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                if (history.percent != null) PercentCard(history.percent!!, history.missed, fmt)
                CalendarCard(history.weeks(firstDay), firstDay, fmt, history.marks.values.any { it == HistoryMark.PAUSED })
                val attention = history.attention
                if (attention.isNotEmpty()) {
                    Text(
                        stringResource(R.string.history_attention).uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                        modifier = Modifier.padding(start = Space.S8, top = Space.S4).semantics { heading() },
                    )
                    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                        attention.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                            AttentionRow(r, fmt)
                        }
                    }
                }
                if (history.asNeeded.isNotEmpty()) {
                    Text(
                        stringResource(R.string.history_as_needed).uppercase(), style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                        modifier = Modifier.padding(start = Space.S8, top = Space.S4).semantics { heading() },
                    )
                    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                        history.asNeeded.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                            Text(
                                com.pillbox.ui.components.isolate(a.medicine.name).let { n ->
                                    pluralStringResource(R.plurals.history_as_needed_row, a.count, n, fmt.number(a.count))
                                },
                                style = MaterialTheme.typography.bodyLarge, color = c.text,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.ListRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S12),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(Space.S16))
            }
            Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom)) {
                com.pillbox.ui.components.OutlineButton(stringResource(R.string.history_share), onShareReport)
            }
        }
    }
}

@Composable
private fun PercentCard(percent: Int, missed: Int, fmt: Formatter) {
    val c = PillBox.colors
    val pct = stringResourceIso(R.string.history_percent, fmt.number(percent))
    val sub = if (missed == 0) stringResource(R.string.history_sub_none) else pluralStringResource(R.plurals.history_sub_missed, missed, fmt.number(missed))
    Row(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S16),
    ) {
        Text(pct, style = MaterialTheme.typography.displayMedium, color = c.success)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.history_percent_title), style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
    }
}

@Composable
private fun CalendarCard(weeks: List<List<HistoryDay>>, firstDay: DayOfWeek, fmt: Formatter, showPaused: Boolean) {
    val c = PillBox.colors
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        // The weekday letters are decoration: each day below says its own full date.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) {
                val d = firstDay.plus(i.toLong())
                Text(
                    d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
                    color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DayCell(day, fmt) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.S4), horizontalArrangement = Arrangement.spacedBy(Space.S16, Alignment.CenterHorizontally)) {
            Legend(HistoryMark.ALL_TAKEN, R.string.history_legend_all)
            Legend(HistoryMark.SOME_MISSED, R.string.history_legend_some)
            Legend(HistoryMark.MISSED_DAY, R.string.history_legend_missed)
            if (showPaused) Legend(HistoryMark.PAUSED, R.string.history_legend_paused)
        }
    }
}

@Composable
private fun Legend(mark: HistoryMark, label: Int) {
    val c = PillBox.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S6)) {
        MarkCircle(mark, 20.dp)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = c.textMuted)
    }
}

@Composable
private fun DayCell(day: HistoryDay, fmt: Formatter) {
    val date = day.date ?: run { Box(Modifier.size(36.dp)); return }
    val say = shortDate(date, fmt)
    val desc = when (day.mark) {
        HistoryMark.ALL_TAKEN -> stringResourceIso(R.string.week_day_taken, say)
        HistoryMark.SOME_MISSED -> stringResourceIso(R.string.week_day_missed, say)
        HistoryMark.MISSED_DAY -> stringResourceIso(R.string.history_day_all_missed, say)
        HistoryMark.PAUSED -> stringResourceIso(R.string.history_day_paused, say)
        HistoryMark.PENDING -> stringResourceIso(R.string.week_day_pending, say)
        HistoryMark.NONE -> stringResourceIso(R.string.week_day_none, say)
        HistoryMark.FUTURE -> stringResourceIso(R.string.history_day_future, say)
    }
    Box(Modifier.semantics { contentDescription = desc }, contentAlignment = Alignment.Center) { MarkCircle(day.mark, 36.dp) }
}

/** The symbol, not just the color, tells the marks apart: check, "!", cross, pause bars, ring, dot. */
@Composable
private fun MarkCircle(mark: HistoryMark, size: androidx.compose.ui.unit.Dp) {
    val c = PillBox.colors
    val icon = size * 0.55f
    when (mark) {
        HistoryMark.ALL_TAKEN -> Box(Modifier.size(size).background(c.success, CircleShape), contentAlignment = Alignment.Center) {
            Icon(com.pillbox.ui.components.DesignIcons.Check, null, tint = c.onPrimary, modifier = Modifier.size(icon))
        }
        HistoryMark.SOME_MISSED -> Box(Modifier.size(size).background(c.warningContainer, CircleShape).border(2.dp, PillBox.illustration.stockLow, CircleShape), contentAlignment = Alignment.Center) {
            Text("!", style = if (size > 24.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, color = c.warning)
        }
        HistoryMark.MISSED_DAY -> Box(Modifier.size(size).background(c.dangerContainer, CircleShape).border(2.dp, c.danger, CircleShape), contentAlignment = Alignment.Center) {
            Icon(com.pillbox.ui.components.DesignIcons.Close, null, tint = c.danger, modifier = Modifier.size(icon))
        }
        HistoryMark.PAUSED -> Box(Modifier.size(size).background(PillBox.illustration.paused.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            PauseGlyph(PillBox.illustration.paused, Modifier.size(icon))
        }
        HistoryMark.PENDING -> Box(Modifier.size(size).border(2.dp, c.accent, CircleShape))
        HistoryMark.NONE, HistoryMark.FUTURE -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Box(Modifier.size(4.dp).background(c.placeholder, CircleShape))
        }
    }
}

@Composable
private fun AttentionRow(r: DoseRecord, fmt: Formatter) {
    val c = PillBox.colors
    val whenText = stringResourceIso(R.string.history_when, shortDate(r.planned.toLocalDate(), fmt), fmt.time(r.planned.toLocalTime()))
    val name = r.medicine.name
    val what = when (r.outcome) {
        DoseOutcome.SKIPPED -> stringResourceIso(R.string.history_skipped, name)
        DoseOutcome.MISSED -> stringResourceIso(R.string.history_not_taken, name)
        DoseOutcome.LATE -> stringResource(
            R.string.history_late_by, name,
            if (r.lateMinutes >= 60) pluralStringResource(R.plurals.n_hours, r.lateMinutes / 60, fmt.number(r.lateMinutes / 60))
            else pluralStringResource(R.plurals.n_minutes, r.lateMinutes, fmt.number(r.lateMinutes)),
        )
        DoseOutcome.ON_TIME -> name
    }
    val late = r.outcome == DoseOutcome.LATE
    val badge = stringResource(if (late) R.string.history_badge_late else R.string.history_badge_missed)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.ListRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S12)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(whenText, style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(what, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Text(badge, style = MaterialTheme.typography.labelLarge, color = if (late) c.warning else c.danger)
    }
}

@Composable
private fun EmptyHistory(onGoToToday: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dimens.ScreenPaddingH + Space.S16),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(80.dp).background(c.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(com.pillbox.ui.components.DesignIcons.Calendar, null, tint = c.accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.size(Space.S16))
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.size(Space.S8))
            Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom)) {
            com.pillbox.ui.components.PrimaryButton(stringResource(R.string.history_go_today), onGoToToday)
        }
    }
}
