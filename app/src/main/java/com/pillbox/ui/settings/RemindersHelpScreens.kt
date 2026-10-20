package com.pillbox.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.DisplaySettings
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Frequency
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.Schedule
import com.pillbox.domain.TodayBuilder
import com.pillbox.domain.WithFood
import com.pillbox.reminder.PermissionState
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.ThemeMode
import com.pillbox.ui.today.RowButton
import com.pillbox.ui.today.TodayContent
import com.pillbox.ui.today.TodayUiState
import java.time.LocalDate
import java.time.LocalTime

/** What a row on "Reminders not ringing?" can send the user to fix. */
enum class RingFix { NOTIFICATIONS, EXACT, BATTERY, DND }

// ---------------------------------------------------------------- Reminders not ringing?

@Composable
fun NotRingingContent(state: PermissionState, onBack: () -> Unit, onFix: (RingFix) -> Unit, onTest: () -> Unit) {
    val c = PillBox.colors
    val issues = state.issues
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.ring_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Row(
                Modifier.fillMaxWidth().background(if (issues > 0) c.warningContainer else c.successContainer, MaterialTheme.shapes.large).padding(Space.S16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                Icon(if (issues > 0) Icons.Filled.Warning else Icons.Filled.Check, contentDescription = null, tint = if (issues > 0) c.warning else c.success, modifier = Modifier.size(28.dp))
                Text(
                    if (issues > 0) pluralStringResource(R.plurals.ring_banner_fix, issues, rememberFormatter().number(issues)) else stringResource(R.string.ring_banner_ok),
                    style = MaterialTheme.typography.bodyLarge, color = if (issues > 0) c.warning else c.success, modifier = Modifier.weight(1f),
                )
            }
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                RingRow(R.string.ring_notif, R.string.ring_notif_sub, state.notifications) { onFix(RingFix.NOTIFICATIONS) }
                RingRow(R.string.ring_exact, R.string.ring_exact_sub, state.exactAlarms) { onFix(RingFix.EXACT) }
                RingRow(R.string.ring_battery, R.string.ring_battery_sub, state.battery) { onFix(RingFix.BATTERY) }
                RingRow(R.string.ring_dnd, R.string.ring_dnd_sub, !state.doNotDisturb) { onFix(RingFix.DND) }
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.ring_test), onTest)
            LinkButton(stringResource(R.string.ring_done), onBack)
        }
    }
}

@Composable
private fun RingRow(title: Int, sub: Int, ok: Boolean, onFix: () -> Unit) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        if (ok) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.perm_allowed), style = MaterialTheme.typography.labelLarge, color = c.success)
            }
        } else {
            RowButton(stringResource(R.string.ring_fix), stringResource(R.string.ring_fix_desc, stringResource(title)), filled = true, onClick = onFix)
        }
    }
}

// ---------------------------------------------------------------- Simple mode

@Composable
fun SimpleModeScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedTitle = stringResource(R.string.toast_setting_saved_title)
    val savedBody = stringResource(R.string.toast_setting_saved_body)
    var changes by remember { mutableStateOf(0) }
    LaunchedEffect(changes) { if (changes > 0) snackbar.showToast(savedTitle, savedBody) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        SimpleModeContent(
            state.display, onBack,
            onTabs = { vm.setSimpleTabs(it); changes++ }, onLarge = { vm.setSimpleLarge(it); changes++ }, onHighContrast = { vm.setHighContrast(it); changes++ },
        )
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun SimpleModeContent(display: DisplaySettings, onBack: () -> Unit, onTabs: (Boolean) -> Unit, onLarge: (Boolean) -> Unit, onHighContrast: (Boolean) -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.simple_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Text(stringResource(R.string.acc_simple_row_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.S8, vertical = Space.S6))
            Card {
                SwitchRow(stringResource(R.string.simple_tabs), display.simpleOnlyTabs, onTabs)
                SwitchRow(stringResource(R.string.simple_large), display.simpleLargeText, onLarge)
                SwitchRow(stringResource(R.string.acc_high_contrast), display.highContrast, onHighContrast, subtitle = stringResource(R.string.simple_hc_sub))
            }
            Note(stringResource(R.string.simple_note))
        }
    }
}

// ---------------------------------------------------------------- High contrast preview

@Composable
fun HighContrastPreviewScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedTitle = stringResource(R.string.toast_setting_saved_title)
    val savedBody = stringResource(R.string.toast_setting_saved_body)
    var changes by remember { mutableStateOf(0) }
    LaunchedEffect(changes) { if (changes > 0) snackbar.showToast(savedTitle, savedBody) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        HighContrastPreviewContent(rememberFormatter(), state.display.highContrast, onBack) { vm.setHighContrast(it); changes++ }
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun HighContrastPreviewContent(fmt: Formatter, on: Boolean, onBack: () -> Unit, onChange: (Boolean) -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.hc_preview_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Text(stringResource(R.string.hc_preview_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.S8, vertical = Space.S6))
            // A real Today with example doses, drawn with the high contrast colors. It is a picture: nothing in it reacts.
            Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(MaterialTheme.shapes.large).border(Dimens.ChipBorder, c.border, MaterialTheme.shapes.large)) {
                PillBoxTheme(mode = ThemeMode.Light, highContrast = true) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).background(MaterialTheme.colorScheme.background).clearAndSetSemantics { }) {
                        TodayContent(sampleToday(), fmt, onSelectDate = {}, onBackToToday = {}, onTake = {}, onSkip = {}, onAddMedicine = {})
                    }
                }
                Box(Modifier.matchParentSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } })
            }
            Card { SwitchRow(stringResource(R.string.hc_preview_use), on, onChange, subtitle = stringResource(R.string.simple_hc_sub)) }
            Box(Modifier.size(Space.S12))
        }
    }
}

/** Example doses for the preview, on today's date. */
internal fun sampleToday(): TodayUiState {
    val day = LocalDate.now()
    fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    val amox = Medicine(id = 1, personId = 1, name = "Amoxicillin", strength = "500 mg", unit = DoseUnit.CAPSULE, stock = 20.0)
    val metf = Medicine(id = 2, personId = 1, name = "Metformin", strength = "850 mg", unit = DoseUnit.TABLET, stock = 20.0)
    val items = listOf(
        Triple(amox, Schedule(day.minusDays(10), null, Frequency.FixedTimes(listOf(t(6), t(14), t(22))), WithFood.WITH_MEAL), emptyList<Pause>()),
        Triple(metf, Schedule(day.minusDays(10), null, Frequency.FixedTimes(listOf(t(14), t(20, 30))), WithFood.AFTER_MEAL), emptyList<Pause>()),
    )
    val now = day.atTime(13, 30)
    val taken = DoseEvent(medicineId = 1, planned = day.atTime(6, 0), taken = day.atTime(6, 5), status = com.pillbox.domain.DoseStatus.TAKEN)
    return TodayUiState(loaded = true, hasMedicines = true, model = TodayBuilder.build(items, listOf(taken), day, now), selectedDate = day, now = now)
}
