package com.pillbox.ui.settings

import com.pillbox.ui.components.stringResourceIso
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.TextSize
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.medicines.ListToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration

/** Where a Settings row leads. */
enum class SettingsTarget { SOUND, LATE, REFILL, QUIET, MEALS, RINGING, ACCESSIBILITY, THEME, LANGUAGE, REPORT, BACKUP, PRIVACY }

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpen: (SettingsTarget) -> Unit, toast: ListToast?, onToastShown: () -> Unit, modifier: Modifier = Modifier, ringingIssues: Int = 0, backup: BackupSettings = BackupSettings()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        if (toast != null) { snackbar.showToast(toast.title, toast.body); onToastShown() }
    }
    Box(modifier.fillMaxSize()) {
        SettingsContent(state, rememberFormatter(), onOpen, ringingIssues, backup)
        ToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** The Settings home without a ViewModel, so screenshots can draw it. Only the rows that lead somewhere are shown. */
@Composable
fun SettingsContent(state: SettingsUiState, fmt: Formatter, onOpen: (SettingsTarget) -> Unit, ringingIssues: Int = 0, backup: BackupSettings = BackupSettings()) {
    val c = PillBox.colors
    val tints = PillBox.illustration.avatars
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingTop)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Section(stringResource(R.string.settings_sec_reminders))
            Group {
                SettingsRow(stringResource(R.string.settings_sound), stringResource(toneName(state.reminder.tone)), tints[0], { RowGlyph("♪", large = true) }, { onOpen(SettingsTarget.SOUND) })
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_late),
                    stringResource(when (state.reminder.late) {
                        com.pillbox.domain.LateSetting.ASK -> R.string.late_value_ask
                        com.pillbox.domain.LateSetting.MOVE_NEXT -> R.string.late_value_move
                        com.pillbox.domain.LateSetting.KEEP -> R.string.late_value_keep
                    }),
                    tints[5], { RowIcon(Icons.Filled.Refresh) }, { onOpen(SettingsTarget.LATE) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_refill),
                    pluralStringResource(R.plurals.n_days, state.refillDays, fmt.number(state.refillDays)),
                    tints[1], { RowIcon(Icons.Filled.Notifications) }, { onOpen(SettingsTarget.REFILL) },
                )
                Divider()
                val q = state.reminder.quiet
                SettingsRow(
                    stringResource(R.string.settings_quiet),
                    if (q.enabled) stringResourceIso(R.string.settings_quiet_range, fmt.time(q.from), fmt.time(q.until)) else stringResource(R.string.settings_off),
                    tints[2], { RowGlyph("☾", large = true) }, { onOpen(SettingsTarget.QUIET) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_meals), pluralStringResource(R.plurals.meals_count, 4, fmt.number(4)),
                    tints[3], { RowGlyph("◔", large = true) }, { onOpen(SettingsTarget.MEALS) },
                )
            }
            Section(stringResource(R.string.settings_sec_display))
            Group {
                SettingsRow(
                    stringResource(R.string.settings_accessibility), textSizeName(state.display.effectiveTextSize),
                    tints[0], { RowGlyph("Aa") }, { onOpen(SettingsTarget.ACCESSIBILITY) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_language), languageName(com.pillbox.ui.AppLocale.active(androidx.compose.ui.platform.LocalContext.current)),
                    tints[5], { RowGlyph("A ع") }, { onOpen(SettingsTarget.LANGUAGE) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.acc_dark), stringResource(themeLabel(state.display.theme)),
                    tints[2], { RowGlyph("☾", large = true) }, { onOpen(SettingsTarget.THEME) },
                )
            }
            Section(stringResource(R.string.settings_sec_data))
            Group {
                SettingsRow(
                    stringResource(R.string.settings_report), stringResource(R.string.settings_report_value),
                    tints[4], { RowGlyph("PDF") }, { onOpen(SettingsTarget.REPORT) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_backup), backupValue(backup, fmt),
                    tints[1], { RowIcon(Icons.Filled.Share) }, { onOpen(SettingsTarget.BACKUP) },
                )
            }
            Section(stringResource(R.string.settings_sec_help))
            Group {
                SettingsRow(
                    stringResource(R.string.settings_ring),
                    if (ringingIssues > 0) pluralStringResource(R.plurals.settings_ring_fix, ringingIssues, fmt.number(ringingIssues)) else stringResource(R.string.settings_ring_ok),
                    tints[2], { RowIcon(Icons.Filled.Notifications) }, { onOpen(SettingsTarget.RINGING) },
                )
                Divider()
                SettingsRow(
                    stringResource(R.string.settings_privacy),
                    stringResourceIso(R.string.ab_version, fmt.digits(com.pillbox.ui.about.appVersion(androidx.compose.ui.platform.LocalContext.current))),
                    tints[1], { RowIcon(Icons.Filled.Lock) }, { onOpen(SettingsTarget.PRIVACY) },
                )
            }
            Spacer(Modifier.size(Space.S20))
            Text(
                stringResource(R.string.settings_made_with), style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp),
            )
        }
    }
}

/** "Failed", "Today", "Yesterday", a date, or "No backup yet": how the last backup went, in one word. */
@Composable
private fun backupValue(b: BackupSettings, fmt: Formatter): String {
    val at = b.lastSuccessAt?.toLocalDate()
    val today = java.time.LocalDate.now()
    return when {
        b.failing -> stringResource(R.string.bk_failed_tag)
        at == null -> stringResource(R.string.bk_never)
        at == today -> stringResource(R.string.today)
        at == today.minusDays(1) -> stringResource(R.string.bk_yesterday)
        else -> com.pillbox.ui.shortDate(at, fmt)
    }
}

internal fun themeLabel(t: com.pillbox.domain.ThemeChoice) = when (t) {
    com.pillbox.domain.ThemeChoice.SYSTEM -> R.string.theme_system
    com.pillbox.domain.ThemeChoice.LIGHT -> R.string.theme_light
    com.pillbox.domain.ThemeChoice.DARK -> R.string.theme_dark
}

internal fun toneName(t: com.pillbox.domain.Tone) = when (t) {
    com.pillbox.domain.Tone.GENTLE_BELL -> R.string.tone_gentle_bell
    com.pillbox.domain.Tone.MORNING_CHIME -> R.string.tone_morning_chime
    com.pillbox.domain.Tone.LOUD_ALARM -> R.string.tone_loud_alarm
}

@Composable
private fun textSizeName(s: TextSize) = stringResource(
    when (s) { TextSize.STANDARD -> R.string.acc_size_standard; TextSize.LARGE -> R.string.acc_size_large; TextSize.LARGEST -> R.string.acc_size_largest },
)

@Composable
internal fun languageName(code: String) = stringResource(if (code == "ar") R.string.lang_ar else R.string.lang_en)

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = PillBox.colors.textMuted,
        modifier = Modifier.padding(start = Space.S8, top = Space.S16, bottom = Space.S6).semantics { heading() },
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(PillBox.colors.card, MaterialTheme.shapes.large)) { content() }
}

@Composable
private fun Divider() { Box(Modifier.fillMaxWidth().padding(start = Space.S16).heightIn(min = 1.dp).background(PillBox.colors.divider)) }

@Composable
private fun RowIcon(icon: ImageVector) = Icon(icon, contentDescription = null, tint = PillBox.colors.accent, modifier = Modifier.size(22.dp))

@Composable
internal fun RowGlyph(text: String, large: Boolean = false) = Text(text, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium, color = PillBox.colors.accent, maxLines = 1)

@Composable
private fun SettingsRow(title: String, value: String, tint: Color, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val c = PillBox.colors
    val desc = stringResourceIso(R.string.settings_row_a11y, title, value)
    // At big text, or with a long value, the value goes under the title, so no word is squeezed into a few letters per line.
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f || value.length > 14 || title.length > 14
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SettingsRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = Space.S12, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(Modifier.size(40.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { icon() }
        if (big) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        } else {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}
