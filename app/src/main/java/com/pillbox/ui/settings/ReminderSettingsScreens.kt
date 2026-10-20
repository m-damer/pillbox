package com.pillbox.ui.settings

import androidx.compose.ui.platform.LocalDensity
import com.pillbox.ui.components.stringResourceIso
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.MealTimes
import com.pillbox.domain.QuietHours
import com.pillbox.domain.ReminderSettings
import com.pillbox.domain.Tone
import com.pillbox.reminder.TonePlayer
import com.pillbox.ui.Formatter
import com.pillbox.ui.addmed.PBTimePickerDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.RadioRow
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration
import java.time.LocalTime

// ---------------------------------------------------------------- Sound and repeat

/** Tone, vibration and how often to remind again. Every change applies at once and shows a toast. */
@Composable
fun SoundScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = remember { TonePlayer(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    val snackbar = remember { SnackbarHostState() }
    val savedTitle = stringResource(R.string.toast_setting_saved_title)
    val savedBody = stringResource(R.string.toast_setting_saved_body)
    var changes by remember { mutableStateOf(0) }
    LaunchedEffect(changes) { if (changes > 0) snackbar.showToast(savedTitle, savedBody) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        SoundContent(
            state.reminder, rememberFormatter(), onBack,
            onTone = { vm.setTone(it); changes++ }, onVibration = { vm.setVibration(it); changes++ },
            onRepeat = { vm.setRepeat(it); changes++ }, onTest = { player.play(state.reminder.tone) },
        )
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun SoundContent(
    s: ReminderSettings,
    fmt: Formatter,
    onBack: () -> Unit,
    onTone: (Tone) -> Unit,
    onVibration: (Boolean) -> Unit,
    onRepeat: (Int) -> Unit,
    onTest: () -> Unit,
) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.sound_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Caption(stringResource(R.string.sound_tone))
            Card {
                Column(Modifier.selectableGroup()) {
                    Tone.entries.forEach { t -> RadioRow(stringResource(toneName(t)), t == s.tone, { onTone(t) }) }
                }
            }
            Box(Modifier.padding(top = Space.S12)) { OutlineButton(stringResource(R.string.sound_test), onTest) }
            Caption(stringResource(R.string.sound_vibration))
            Card { SwitchRow(stringResource(R.string.sound_vibration), s.vibration, onVibration) }
            Caption(stringResource(R.string.sound_unanswered))
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                Text(stringResource(R.string.sound_repeat_every), style = MaterialTheme.typography.labelLarge, color = c.text)
                val choices = ReminderSettings.REPEAT_CHOICES
                Segmented(
                    choices.map { stringResourceIso(R.string.min_short, fmt.number(it)) },
                    choices.indexOf(s.repeatMinutes).coerceAtLeast(0), { onRepeat(choices[it]) }, stackWhenLarge = true,
                )
            }
            Note(stringResource(R.string.sound_volume_note))
            Box(Modifier.size(96.dp))
        }
    }
}

// ---------------------------------------------------------------- Quiet hours

@Composable
fun QuietHoursScreen(vm: SettingsViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    if (!state.loaded) return
    val q0 = state.reminder.quiet
    var enabled by rememberSaveable { mutableStateOf(q0.enabled) }
    var from by rememberSaveable { mutableStateOf(q0.from.toSecondOfDay()) }
    var until by rememberSaveable { mutableStateOf(q0.until.toSecondOfDay()) }
    var rings by rememberSaveable { mutableStateOf(q0.doseRings) }
    var muteRefill by rememberSaveable { mutableStateOf(q0.muteRefill) }
    QuietHoursContent(
        QuietHours(enabled, LocalTime.ofSecondOfDay(from.toLong()), LocalTime.ofSecondOfDay(until.toLong()), rings, muteRefill),
        rememberFormatter(), onBack,
        onEnabled = { enabled = it },
        onFrom = { from = it.toSecondOfDay() }, onUntil = { until = it.toSecondOfDay() },
        onRings = { rings = it }, onMuteRefill = { muteRefill = it },
        onSave = {
            vm.saveQuiet(QuietHours(enabled, LocalTime.ofSecondOfDay(from.toLong()), LocalTime.ofSecondOfDay(until.toLong()), rings, muteRefill), onSaved)
        },
    )
}

@Composable
fun QuietHoursContent(
    q: QuietHours,
    fmt: Formatter,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onFrom: (LocalTime) -> Unit,
    onUntil: (LocalTime) -> Unit,
    onRings: (Boolean) -> Unit,
    onMuteRefill: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val c = PillBox.colors
    var picking by rememberSaveable { mutableStateOf(0) } // 0 none, 1 from, 2 until
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.quiet_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Card { SwitchRow(stringResource(R.string.quiet_use), q.enabled, onEnabled, subtitle = stringResource(R.string.quiet_use_sub)) }
            if (q.enabled) {
                Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                    DayBar(q, fmt)
                    // At large text two cards side by side are too narrow for a time, so they stack.
                    if (LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                            TimeChoice(stringResource(R.string.quiet_from), fmt.time(q.from), Modifier.fillMaxWidth()) { picking = 1 }
                            TimeChoice(stringResource(R.string.quiet_until), fmt.time(q.until), Modifier.fillMaxWidth()) { picking = 2 }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                        TimeChoice(stringResource(R.string.quiet_from), fmt.time(q.from), Modifier.weight(1f)) { picking = 1 }
                        TimeChoice(stringResource(R.string.quiet_until), fmt.time(q.until), Modifier.weight(1f)) { picking = 2 }
                    }
                }
                Card {
                    SwitchRow(stringResource(R.string.quiet_dose_rings), q.doseRings, onRings, subtitle = stringResource(R.string.quiet_dose_rings_sub))
                    SwitchRow(stringResource(R.string.quiet_mute_refill), q.muteRefill, onMuteRefill, subtitle = stringResource(R.string.quiet_mute_refill_sub))
                }
            }
            Note(stringResource(if (q.doseRings) R.string.quiet_note else R.string.quiet_note_silent))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) { PrimaryButton(stringResource(R.string.save_changes), onSave) }
    }
    if (picking != 0) {
        PBTimePickerDialog(if (picking == 1) q.from else q.until, fmt.is24Hour, onDismiss = { picking = 0 }) {
            if (picking == 1) onFrom(it) else onUntil(it)
            picking = 0
        }
    }
}

/** A label over a big time, the whole card opens the time picker. */
@Composable
private fun TimeChoice(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    val c = PillBox.colors
    val hint = stringResource(R.string.tap_to_change)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .background(c.primaryContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $time. $hint" }
            .padding(Space.S12),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted, textAlign = TextAlign.Center)
        Text(time, style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.Center)
    }
}

/** The 24 hours as one bar, the quiet part filled. Windows that cross midnight fill both ends. */
@Composable
private fun DayBar(q: QuietHours, fmt: Formatter) {
    val c = PillBox.colors
    val desc = stringResourceIso(R.string.quiet_bar_a11y, fmt.time(q.from), fmt.time(q.until))
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = desc }, verticalArrangement = Arrangement.spacedBy(Space.S6)) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val day = 24 * 60f
            val h = size.height
            drawRoundRect(c.night, size = Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            fun seg(a: Float, b: Float) {
                if (b <= a) return
                drawRect(c.primary, Offset(size.width * a / day, 0f), Size(size.width * (b - a) / day, h))
            }
            val f = q.from.hour * 60f + q.from.minute
            val u = q.until.hour * 60f + q.until.minute
            when {
                f == u -> {}
                f < u -> seg(f, u)
                else -> { seg(f, day); seg(0f, u) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (if (LocalDensity.current.fontScale > 1.3f) listOf(0, 12) else listOf(0, 6, 12, 18)).forEach { Text(fmt.hour(it), style = MaterialTheme.typography.labelSmall, color = c.textMuted) }
        }
    }
}

// ---------------------------------------------------------------- Meal times

@Composable
fun MealTimesScreen(vm: SettingsViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    if (!state.loaded) return
    val m0 = state.reminder.meals
    var secs by rememberSaveable { mutableStateOf(MealTimes.Meal.entries.map { m0[it].toSecondOfDay() }) }
    fun build() = MealTimes.Meal.entries.foldIndexed(MealTimes()) { i, acc, meal -> acc.with(meal, LocalTime.ofSecondOfDay(secs[i].toLong())) }
    MealTimesContent(
        build(), rememberFormatter(), onBack,
        onChange = { meal, t -> secs = secs.toMutableList().also { it[meal.ordinal] = t.toSecondOfDay() } },
        onSave = { vm.saveMeals(build(), onSaved) },
    )
}

@Composable
fun MealTimesContent(meals: MealTimes, fmt: Formatter, onBack: () -> Unit, onChange: (MealTimes.Meal, LocalTime) -> Unit, onSave: () -> Unit) {
    val c = PillBox.colors
    val tints = PillBox.illustration.avatars
    var picking by rememberSaveable { mutableStateOf(-1) }
    // The day filling up: a quarter, half, three quarters, then night.
    val glyphs = listOf("◔", "◑", "◕", "☾")
    val rows = listOf(
        Triple(MealTimes.Meal.BREAKFAST, R.string.meal_breakfast, R.string.meal_breakfast_sub),
        Triple(MealTimes.Meal.LUNCH, R.string.meal_lunch, R.string.meal_lunch_sub),
        Triple(MealTimes.Meal.DINNER, R.string.meal_dinner, R.string.meal_dinner_sub),
        Triple(MealTimes.Meal.BEDTIME, R.string.meal_bedtime, R.string.meal_bedtime_sub),
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.meals_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Text(stringResource(R.string.meals_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.S8, vertical = Space.S6))
            Card {
                rows.forEachIndexed { i, (meal, nameRes, subRes) ->
                    val name = stringResource(nameRes)
                    val time = fmt.time(meals[meal])
                    val desc = stringResourceIso(R.string.meal_time_a11y, name, time)
                    Row(
                        Modifier
                            .fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
                            .clickable(role = Role.Button) { picking = i }
                            .semantics(mergeDescendants = true) { contentDescription = desc }
                            .padding(horizontal = Space.S16, vertical = Space.S8),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                    ) {
                        Box(Modifier.size(40.dp).background(tints[i], androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            RowGlyph(glyphs[i], large = true)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = c.text)
                            Text(stringResource(subRes), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                        Text(
                            time, style = MaterialTheme.typography.labelLarge, color = c.text, softWrap = false,
                            modifier = Modifier.background(c.primaryContainer, MaterialTheme.shapes.medium).padding(horizontal = Space.S12, vertical = Space.S8),
                        )
                    }
                }
            }
            Note(stringResource(R.string.meals_note))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) { PrimaryButton(stringResource(R.string.save_changes), onSave) }
    }
    if (picking >= 0) {
        val meal = rows[picking].first
        PBTimePickerDialog(meals[meal], fmt.is24Hour, onDismiss = { picking = -1 }) { onChange(meal, it); picking = -1 }
    }
}

// ---------------------------------------------------------------- When I take a dose late

@Composable
fun LateSettingScreen(vm: SettingsViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    if (!state.loaded) return
    var setting by rememberSaveable { mutableStateOf(state.reminder.late) }
    var threshold by rememberSaveable { mutableStateOf(state.reminder.lateThreshold) }
    LateSettingContent(setting, threshold, rememberFormatter(), onBack, { setting = it }, { threshold = it }, { vm.saveLate(setting, threshold, onSaved) })
}

@Composable
fun LateSettingContent(
    setting: com.pillbox.domain.LateSetting,
    threshold: com.pillbox.domain.LateThreshold,
    fmt: Formatter,
    onBack: () -> Unit,
    onSetting: (com.pillbox.domain.LateSetting) -> Unit,
    onThreshold: (com.pillbox.domain.LateThreshold) -> Unit,
    onSave: () -> Unit,
) {
    val c = PillBox.colors
    val options = listOf(
        Triple(com.pillbox.domain.LateSetting.ASK, R.string.late_ask, R.string.late_ask_sub),
        Triple(com.pillbox.domain.LateSetting.MOVE_NEXT, R.string.late_move_next, R.string.late_move_next_sub),
        Triple(com.pillbox.domain.LateSetting.KEEP, R.string.late_keep_times, R.string.late_keep_times_sub),
    )
    val thresholds = com.pillbox.domain.LateThreshold.entries
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.late_setting_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Column(Modifier.selectableGroup().padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                options.forEach { (value, title, sub) ->
                    val on = setting == value
                    Row(
                        Modifier
                            .fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
                            .background(if (on) c.primaryContainer else c.card, MaterialTheme.shapes.large)
                            .border(if (on) Dimens.ChipBorder else 0.dp, if (on) c.primary else c.card, MaterialTheme.shapes.large)
                            .selectable(selected = on, role = Role.RadioButton) { onSetting(value) }
                            .padding(Space.S16),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                    ) {
                        Box(
                            Modifier.size(28.dp).background(if (on) c.primary else c.card, CircleShape)
                                .border(Dimens.ChipBorder, if (on) c.primary else c.placeholder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = c.text)
                            if (value == com.pillbox.domain.LateSetting.ASK) {
                                Text(
                                    stringResource(R.string.late_suggested), style = MaterialTheme.typography.labelSmall, color = c.accent,
                                    modifier = Modifier.padding(vertical = Space.S4),
                                )
                            }
                            Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                    }
                }
            }
            Caption(stringResource(R.string.late_threshold_caption))
            Card {
                Box(Modifier.padding(Space.S8)) {
                    Segmented(
                        thresholds.map { if (it.minutes < 60) stringResourceIso(R.string.min_short, fmt.number(it.minutes)) else stringResource(R.string.late_threshold_hour) },
                        thresholds.indexOf(threshold), { onThreshold(thresholds[it]) }, stackWhenLarge = true,
                    )
                }
            }
            Note(stringResource(R.string.late_setting_note))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) { PrimaryButton(stringResource(R.string.save_changes), onSave) }
    }
}
