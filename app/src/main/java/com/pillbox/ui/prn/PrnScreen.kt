package com.pillbox.ui.prn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.PrnDay
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.components.stringResourceIso
import com.pillbox.ui.medicines.doseText
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.strengthLabel
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.Medicine
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun PrnScreen(vm: PrnViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val snackbar = remember { SnackbarHostState() }
    BackHandler(onBack = onBack)
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    LaunchedEffect(state.toastId) {
        val at = state.loggedAt
        if (at != null) snackbar.showToast(res.getString(R.string.prn_logged), fmt.time(at.toLocalTime()))
    }
    val m = state.medicine
    val day = state.day
    if (!state.loaded || m == null || day == null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        if (state.loaded) LaunchedEffect(Unit) { onBack() }
        return
    }
    Box(modifier.fillMaxSize()) {
        PrnContent(m, day, state.doses, fmt, state.loggedAt != null, onBack, onTakeNow = vm::takeNow, onUndo = vm::undoLast)
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Dimens.PrimaryButtonHeight + Space.S20))
    }
    state.tooSoon?.let { (last, gap) -> TooSoonSheet(m, last, gap, fmt, onWait = vm::dismissTooSoon, onAnyway = vm::takeAnyway) }
}

/** The as-needed log and the "limit reached" state, without a ViewModel so screenshots can draw them. */
@Composable
fun PrnContent(
    m: Medicine, day: PrnDay, doses: List<DoseEvent>, fmt: Formatter, justLogged: Boolean,
    onBack: () -> Unit, onTakeNow: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = PillBox.colors
    val backLabel = stringResource(R.string.back)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingTop),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4),
        ) {
            Box(
                Modifier.size(Dimens.TouchTargetMin).semantics { contentDescription = backLabel; role = Role.Button }.clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(com.pillbox.ui.components.DesignIcons.Back, contentDescription = null, tint = c.text) }
            Column(Modifier.weight(1f)) {
                Text(stringResourceIso(R.string.prn_title, m.name), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(strengthLabel(m.strength), stringResourceIso(R.string.prn_each_time, doseText(m, fmt))).joinToString(stringResource(R.string.dot_sep)),
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            CountCard(day, fmt)
            if (day.limitReached) {
                Row(
                    Modifier.fillMaxWidth().background(c.dangerContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                    horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top,
                ) {
                    Icon(com.pillbox.ui.components.DesignIcons.Warning, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                    Text(
                        stringResourceIso(R.string.prn_limit_body, pluralStringResource(R.plurals.n_doses, day.max ?: day.count, fmt.number(day.max ?: day.count)), nextText(day, fmt)),
                        style = MaterialTheme.typography.bodyMedium, color = c.danger,
                    )
                }
            } else if (day.nextAllowed != null) {
                Row(
                    Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                    horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.Top,
                ) {
                    Icon(com.pillbox.ui.components.DesignIcons.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text(
                        stringResourceIso(R.string.prn_next_allowed, nextText(day, fmt), hoursText(day.minGapMinutes, fmt)),
                        style = MaterialTheme.typography.bodyMedium, color = c.accent,
                    )
                }
            }
            if (doses.isNotEmpty()) {
                Text(
                    stringResource(R.string.prn_today).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                    modifier = Modifier.padding(start = Space.S8, top = Space.S4).semantics { heading() },
                )
                Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                    doses.forEachIndexed { i, d ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = Space.S16).size(1.dp).background(c.divider))
                        val time = fmt.time(d.taken!!.toLocalTime())
                        val status = stringResource(if (d.early) R.string.prn_taken_early else R.string.prn_taken)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.ListRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S8)
                                .semantics(mergeDescendants = true) { contentDescription = "$time, $status" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S16),
                        ) {
                            Text(time, style = MaterialTheme.typography.labelLarge, color = c.text)
                            Text(status, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                    }
                }
            }
            Box(Modifier.size(Space.S12))
        }
        Column(Modifier.navigationBarsPadding().padding(Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Space.S10)) {
            when {
                day.limitReached -> {
                    PrimaryButton(stringResource(R.string.prn_ok_wait), onBack)
                    OutlineButton(stringResource(R.string.prn_undo_last), onUndo)
                }
                justLogged -> LinkButton(stringResource(R.string.ring_done), onBack, Modifier.fillMaxWidth())
                else -> {
                    PrimaryButton(stringResource(R.string.prn_take_now), onTakeNow)
                    LinkButton(stringResource(R.string.ring_done), onBack, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CountCard(day: PrnDay, fmt: Formatter) {
    val c = PillBox.colors
    val max = day.max
    val said = if (max != null) stringResourceIso(R.string.prn_count_a11y, fmt.number(day.count), fmt.number(max)) else stringResourceIso(R.string.prn_count_a11y_nomax, fmt.number(day.count))
    Column(
        Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16).semantics(mergeDescendants = true) { contentDescription = said },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Text(stringResource(R.string.prn_taken_today), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Text(
            if (max != null) stringResourceIso(R.string.prn_x_of_y, fmt.number(day.count), fmt.number(max)) else fmt.number(day.count),
            style = MaterialTheme.typography.displaySmall, color = c.text, textAlign = TextAlign.Center,
        )
        if (max != null && max <= 12) {
            Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                repeat(max) { i ->
                    // Filled means taken; an empty ring means one more is allowed. Never color alone.
                    if (i < day.count) Box(Modifier.size(20.dp).background(c.primary, CircleShape))
                    else Box(Modifier.size(20.dp).border(2.dp, c.primary, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun nextText(day: PrnDay, fmt: Formatter): String {
    val next = day.nextAllowed ?: return ""
    val today = LocalDateTime.now().toLocalDate()
    return if (next.toLocalDate() == today) fmt.time(next.toLocalTime())
    else if (next.toLocalTime() == java.time.LocalTime.MIDNIGHT && next.toLocalDate() == today.plusDays(1)) stringResource(R.string.prn_tomorrow)
    else stringResourceIso(R.string.prn_tomorrow_at, fmt.time(next.toLocalTime()))
}

@Composable
private fun hoursText(minutes: Int, fmt: Formatter): String =
    if (minutes % 60 == 0) pluralStringResource(R.plurals.n_hours, minutes / 60, fmt.number(minutes / 60))
    else pluralStringResource(R.plurals.n_minutes, minutes, fmt.number(minutes))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooSoonSheet(m: Medicine, last: LocalDateTime, gapMinutes: Int, fmt: Formatter, onWait: () -> Unit, onAnyway: () -> Unit) {
    val ago = Duration.between(last, LocalDateTime.now()).toMinutes().coerceAtLeast(0)
    val agoText = if (ago >= 60) pluralStringResource(R.plurals.n_hours, (ago / 60).toInt(), fmt.number((ago / 60).toInt()))
    else pluralStringResource(R.plurals.n_minutes, ago.toInt(), fmt.number(ago.toInt()))
    ModalBottomSheet(onDismissRequest = onWait, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PillBox.colors.card) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Text(stringResource(R.string.too_soon_title), style = MaterialTheme.typography.titleMedium, color = PillBox.colors.text, modifier = Modifier.semantics { heading() })
            Text(stringResourceIso(R.string.too_soon_body, m.name, agoText, hoursText(gapMinutes, fmt)), style = MaterialTheme.typography.bodyMedium, color = PillBox.colors.textMuted)
            PrimaryButton(stringResource(R.string.wait_dont_take), onWait)
            OutlineButton(stringResource(R.string.take_anyway_long), onAnyway)
            Box(Modifier.size(Space.S12))
        }
    }
}
