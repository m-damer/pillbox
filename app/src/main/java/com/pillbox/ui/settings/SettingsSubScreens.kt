package com.pillbox.ui.settings

import com.pillbox.ui.components.stringResourceIso
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.DisplaySettings
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.TextSize
import com.pillbox.domain.ThemeChoice
import com.pillbox.domain.TimeFormat
import com.pillbox.ui.LocalDisplaySettings
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.RadioRow
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.firstDayOfWeek
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.PillIcon
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun SubHeader(title: String, onBack: () -> Unit) {
    val c = PillBox.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8, vertical = Space.S8), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

@Composable
internal fun Caption(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = PillBox.colors.textMuted,
        modifier = Modifier.padding(start = Space.S8, top = Space.S16, bottom = Space.S6).semantics { heading() },
    )
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(PillBox.colors.card, MaterialTheme.shapes.large)) { content() }
}

@Composable
internal fun Note(text: String) {
    val c = PillBox.colors
    Row(
        Modifier.padding(top = Space.S12).fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Accessibility

/** Text size, high contrast and the way to the dark theme choice. Every change applies at once and shows a toast. */
@Composable
fun AccessibilityScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenTheme: () -> Unit, onOpenSimple: () -> Unit = {}, onOpenPreview: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedTitle = stringResource(R.string.toast_setting_saved_title)
    val savedBody = stringResource(R.string.toast_setting_saved_body)
    var changes by remember { mutableStateOf(0) }
    LaunchedEffect(changes) { if (changes > 0) snackbar.showToast(savedTitle, savedBody) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        AccessibilityContent(
            state.display, onBack, onOpenTheme,
            onTextSize = { vm.setTextSize(it); changes++ }, onHighContrast = { vm.setHighContrast(it); changes++ },
            reminder = state.reminder, onKeepRinging = { vm.setKeepRinging(it); changes++ },
            onSimple = { vm.setSimple(it); changes++ }, onReadAloud = { vm.setReadAloud(it); changes++ },
            onOpenSimple = onOpenSimple, onOpenPreview = onOpenPreview,
        )
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun AccessibilityContent(
    display: DisplaySettings,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onTextSize: (TextSize) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    reminder: com.pillbox.domain.ReminderSettings = com.pillbox.domain.ReminderSettings(),
    onKeepRinging: (Boolean) -> Unit = {},
    onSimple: (Boolean) -> Unit = {},
    onReadAloud: (Boolean) -> Unit = {},
    onOpenSimple: () -> Unit = {},
    onOpenPreview: () -> Unit = {},
) {
    val c = PillBox.colors
    val fmt = com.pillbox.ui.rememberFormatter()
    val sizes = listOf(TextSize.STANDARD to R.string.acc_size_standard, TextSize.LARGE to R.string.acc_size_large, TextSize.LARGEST to R.string.acc_size_largest)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.acc_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                Text(stringResource(R.string.acc_text_size), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.semantics { heading() })
                Segmented(sizes.map { stringResource(it.second) }, sizes.indexOfFirst { it.first == display.textSize }, { onTextSize(sizes[it].first) }, stackWhenLarge = true)
                Row(
                    Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                    horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillIcon(DoseUnit.CAPSULE, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.acc_preview_name), style = MaterialTheme.typography.labelLarge, color = c.text)
                        Text(stringResource(R.string.acc_preview_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                }
                Text(stringResource(R.string.acc_follows), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            Card {
                SwitchRow(stringResource(R.string.acc_high_contrast), display.highContrast, onHighContrast, subtitle = stringResource(R.string.acc_high_contrast_sub))
                SwitchRow(stringResource(R.string.acc_simple), display.simpleMode, onSimple, subtitle = stringResource(R.string.acc_simple_sub))
                SwitchRow(stringResource(R.string.acc_read), reminder.readAloud, onReadAloud, subtitle = stringResource(R.string.acc_read_sub))
                SwitchRow(
                    stringResource(R.string.acc_keep), reminder.keepRinging, onKeepRinging,
                    subtitle = if (reminder.keepRinging) stringResourceIso(R.string.acc_keep_sub, androidx.compose.ui.res.pluralStringResource(R.plurals.n_minutes, reminder.repeatMinutes, fmt.number(reminder.repeatMinutes)))
                    else stringResource(R.string.acc_keep_off_sub),
                )
            }
            Card {
                NavRow(stringResource(R.string.acc_simple), stringResource(R.string.acc_simple_row_sub), null, onOpenSimple)
                NavRow(stringResource(R.string.acc_hc_preview), stringResource(R.string.acc_hc_preview_sub), null, onOpenPreview)
                val value = stringResource(themeName(display.theme))
                val desc = stringResourceIso(R.string.settings_row_a11y, stringResource(R.string.acc_dark), value)
                Row(
                    Modifier
                        .fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
                        .clickable(role = Role.Button, onClick = onOpenTheme)
                        .semantics(mergeDescendants = true) { contentDescription = desc }
                        .padding(horizontal = Space.S16, vertical = Space.S8),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.acc_dark), style = MaterialTheme.typography.labelLarge, color = c.text)
                        Text(stringResource(R.string.acc_dark_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
                }
            }
            Box(Modifier.size(96.dp))
        }
    }
}

/** A row that opens another screen: title, a line under it, an optional value and the arrow. */
@Composable
internal fun NavRow(title: String, subtitle: String, value: String?, onClick: () -> Unit) {
    val c = PillBox.colors
    val desc = if (value != null) stringResourceIso(R.string.settings_row_a11y, title, value) else "$title. $subtitle"
    Row(
        Modifier
            .fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

private fun themeName(t: ThemeChoice) = when (t) { ThemeChoice.SYSTEM -> R.string.theme_system; ThemeChoice.LIGHT -> R.string.theme_light; ThemeChoice.DARK -> R.string.theme_dark }

// ---------------------------------------------------------------- Dark theme

@Composable
fun ThemeScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    ThemeContent(state.display.theme, onBack, vm::setTheme)
}

@Composable
fun ThemeContent(selected: ThemeChoice, onBack: () -> Unit, onSelect: (ThemeChoice) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.theme_title), onBack)
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
            Column(Modifier.fillMaxWidth().background(PillBox.colors.card, MaterialTheme.shapes.large).selectableGroup()) {
                ThemeChoice.entries.forEach { t -> RadioRow(stringResource(themeName(t)), t == selected, { onSelect(t) }) }
            }
        }
    }
}

// ---------------------------------------------------------------- Language and numbers

/** Language, digits, time format and week start. Chosen first, applied with Save. */
@Composable
fun LanguageScreen(vm: SettingsViewModel, activeLanguage: String, onBack: () -> Unit, onSave: (LanguageChoice) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    if (!state.loaded) return
    val d = state.display
    val firstDay = firstDayOfWeek()
    var language by rememberSaveable { mutableStateOf(activeLanguage) }
    var arabicDigits by rememberSaveable { mutableStateOf(d.useArabicDigits(activeLanguage)) }
    var time by rememberSaveable { mutableStateOf(d.timeFormat) }
    var week by rememberSaveable { mutableStateOf(firstDay) }
    LanguageContent(
        language, arabicDigits, time, week, onBack,
        onLanguage = { language = it }, onDigits = { arabicDigits = it }, onTime = { time = it }, onWeek = { week = it },
        onSave = { onSave(LanguageChoice(language, arabicDigits, time, week)) },
    )
}

@Composable
fun LanguageContent(
    language: String,
    arabicDigits: Boolean,
    time: TimeFormat,
    week: DayOfWeek,
    onBack: () -> Unit,
    onLanguage: (String) -> Unit,
    onDigits: (Boolean) -> Unit,
    onTime: (TimeFormat) -> Unit,
    onWeek: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val times = listOf(TimeFormat.PHONE to R.string.lang_time_phone, TimeFormat.H12 to R.string.lang_time_12, TimeFormat.H24 to R.string.lang_time_24)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.lang_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Caption(stringResource(R.string.lang_language))
            Card {
                Column(Modifier.selectableGroup()) {
                    RadioRow(stringResource(R.string.lang_en), language == "en", { onLanguage("en") }, subtitle = stringResource(R.string.lang_en_sub))
                    RadioRow(stringResource(R.string.lang_ar), language == "ar", { onLanguage("ar") }, subtitle = stringResource(R.string.lang_ar_sub))
                }
            }
            Caption(stringResource(R.string.lang_numbers))
            Card {
                Box(Modifier.padding(Space.S8)) {
                    // The labels are the numbers themselves, drawn the way they will look.
                    Segmented(listOf("123", "١٢٣"), if (arabicDigits) 1 else 0, { onDigits(it == 1) }, stackWhenLarge = true)
                }
            }
            Caption(stringResource(R.string.lang_time))
            Card { Box(Modifier.padding(Space.S8)) { Segmented(times.map { stringResource(it.second) }, times.indexOfFirst { it.first == time }, { onTime(times[it].first) }, stackWhenLarge = true) } }
            Caption(stringResource(R.string.lang_week))
            Card {
                Box(Modifier.padding(Space.S8)) {
                    val days = DisplaySettings.WEEK_STARTS
                    Segmented(days.map { it.getDisplayName(TextStyle.SHORT, locale) }, days.indexOf(week).coerceAtLeast(0), { onWeek(days[it]) }, stackWhenLarge = true)
                }
            }
            Note(stringResource(R.string.lang_note))
            Box(Modifier.size(Space.S16))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom)) { PrimaryButton(stringResource(R.string.btn_save), onSave) }
    }
}
