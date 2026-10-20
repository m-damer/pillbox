package com.pillbox.ui.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.BackupSummary
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.settings.Caption
import com.pillbox.ui.settings.Card
import com.pillbox.ui.settings.Note
import com.pillbox.ui.settings.SubHeader
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.LocalDate
import java.time.LocalDateTime

// ---------------------------------------------------------------- Backup and export

@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit, onReport: () -> Unit, onRestore: () -> Unit, onDeleted: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    var unavailable by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var failureHidden by rememberSaveable { mutableStateOf(false) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.folderPicked(uri) }

    val savedTitle = stringResource(R.string.bk_saved_title)
    val folderTitle = stringResource(R.string.bk_folder_set_title)
    val rootLabel = stringResource(R.string.bk_folder_root)
    val folderBody = { label: String? -> context.getString(R.string.bk_folder_set_body, label?.ifEmpty { null } ?: rootLabel) }
    val shareFailedTitle = stringResource(R.string.bk_share_failed_title)
    val shareFailedBody = stringResource(R.string.bk_share_failed_body)
    val shareChooser = stringResource(R.string.bk_share_chooser)

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is BackupEvent.Saved -> { failureHidden = false; host.showToast(savedTitle, e.fileName) }
                is BackupEvent.FolderSet -> { failureHidden = false; host.showToast(folderTitle, folderBody(e.label)) }
                is BackupEvent.Failed -> { failureHidden = false; if (e.problem == BackupProblem.FOLDER_UNAVAILABLE) unavailable = true }
                is BackupEvent.ShareFile -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, e.uri)
                        clipData = ClipData.newRawUri(e.name, e.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, shareChooser)) }
                }
                BackupEvent.ShareFailed -> host.showToast(shareFailedTitle, shareFailedBody)
                BackupEvent.NeedFolder -> pickFolder.launch(null)
            }
        }
    }

    val settings = state.settings
    if (settings.failing && settings.lastProblem != BackupProblem.FOLDER_UNAVAILABLE && !failureHidden) {
        BackupFailedContent(settings, fmt, LocalDate.now(), state.busy, onBack, onTryAgain = { vm.saveNow() }, onLater = { failureHidden = true })
    } else {
        BackupContent(
            settings, fmt, LocalDate.now(), host, onBack,
            onToggleAuto = vm::setAuto, onSaveNow = vm::saveNow, onReport = onReport, onShare = vm::share, onRestore = onRestore, onDelete = { confirmDelete = true },
            busy = state.busy,
        )
    }
    if (unavailable) {
        FolderUnavailableDialog(settings.folderLabel, onChoose = { unavailable = false; pickFolder.launch(null) }, onLater = { unavailable = false })
    }
    if (confirmDelete) {
        DeleteAllDialog(onKeep = { confirmDelete = false }, onDelete = { confirmDelete = false; vm.deleteAll(onDeleted) })
    }
}

@Composable
internal fun whenText(at: LocalDateTime, today: LocalDate, fmt: Formatter): String {
    val day = when (at.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.bk_yesterday)
        else -> shortDate(at.toLocalDate(), fmt)
    }
    return stringResource(R.string.bk_when, day, fmt.time(at.toLocalTime()))
}

@Composable
fun BackupContent(
    s: BackupSettings, fmt: Formatter, today: LocalDate, host: SnackbarHostState, onBack: () -> Unit,
    onToggleAuto: (Boolean) -> Unit, onSaveNow: () -> Unit, onReport: () -> Unit, onShare: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit,
    busy: Boolean = false,
) {
    val c = PillBox.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.bk_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH).padding(bottom = Space.S20),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                Card {
                    Row(Modifier.fillMaxWidth().padding(Space.S12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                        Tile(c.successContainer) { Icon(Icons.Filled.Check, contentDescription = null, tint = c.success) }
                        Column(Modifier.weight(1f)) {
                            val at = s.lastSuccessAt
                            if (at != null) {
                                Text(stringResource(R.string.bk_last, whenText(at, today, fmt)), style = MaterialTheme.typography.labelLarge, color = c.text)
                                Text(stringResource(R.string.bk_where_folder), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                            } else {
                                Text(stringResource(R.string.bk_never), style = MaterialTheme.typography.labelLarge, color = c.text)
                            }
                        }
                    }
                }
                Column {
                    Caption(stringResource(R.string.bk_sec_auto))
                    Card {
                        val folder = when {
                            !s.hasFolder -> stringResource(R.string.bk_folder_none)
                            else -> s.folderLabel?.ifEmpty { null } ?: stringResource(R.string.bk_folder_root)
                        }
                        SwitchRow(stringResource(R.string.bk_folder), s.autoFolder && s.hasFolder, onToggleAuto, subtitle = folder)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.large).padding(Space.S16),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.bk_warn_phone_only), style = MaterialTheme.typography.bodyMedium, color = c.warning, modifier = Modifier.weight(1f))
                }
                PrimaryButton(stringResource(R.string.bk_save_now), onSaveNow, enabled = !busy)
                Column {
                    Caption(stringResource(R.string.bk_sec_more))
                    Card {
                        BackupRow(stringResource(R.string.bk_report), stringResource(R.string.bk_report_sub), onReport) { Text("PDF", style = MaterialTheme.typography.labelMedium, color = c.accent, maxLines = 1) }
                        RowDivider()
                        BackupRow(stringResource(R.string.bk_share), stringResource(R.string.bk_share_sub), onShare) { Icon(Icons.Filled.Share, contentDescription = null, tint = c.accent) }
                        RowDivider()
                        BackupRow(stringResource(R.string.bk_restore), stringResource(R.string.bk_restore_sub), onRestore) { Icon(Icons.Filled.Refresh, contentDescription = null, tint = c.accent) }
                    }
                }
                Card {
                    BackupRow(stringResource(R.string.bk_delete), null, onDelete, danger = true, tile = c.dangerContainer) { Icon(Icons.Filled.Delete, contentDescription = null, tint = c.danger) }
                }
            }
        }
        ToastHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Tile(color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Box(Modifier.size(Dimens.TouchTargetMin).background(color, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = Space.S16).heightIn(min = 1.dp).background(PillBox.colors.divider))
}

@Composable
private fun BackupRow(title: String, subtitle: String?, onClick: () -> Unit, danger: Boolean = false, tile: androidx.compose.ui.graphics.Color = PillBox.colors.primaryContainer, icon: @Composable () -> Unit) {
    val c = PillBox.colors
    val desc = if (subtitle != null) "$title. $subtitle" else title
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = Space.S12, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Tile(tile, icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (danger) c.danger else c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        if (!danger) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

// ---------------------------------------------------------------- Backup failed

@Composable
fun BackupFailedContent(s: BackupSettings, fmt: Formatter, today: LocalDate, busy: Boolean, onBack: () -> Unit, onTryAgain: () -> Unit, onLater: () -> Unit) {
    val c = PillBox.colors
    val space = s.lastProblem == BackupProblem.NO_SPACE
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.bk_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Row(
                Modifier.fillMaxWidth().background(c.dangerContainer, MaterialTheme.shapes.large).padding(Space.S16),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.danger, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(stringResource(R.string.bk_failed_title), style = MaterialTheme.typography.labelLarge, color = c.danger)
                    Text(stringResource(if (space) R.string.bk_failed_space else R.string.bk_failed_other), style = MaterialTheme.typography.bodyMedium, color = c.danger)
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.S16, vertical = Space.S12)) {
                    Text(stringResource(R.string.bk_last_ok), style = MaterialTheme.typography.labelLarge, color = c.text)
                    Text(
                        s.lastSuccessAt?.let { whenText(it, today, fmt) } ?: stringResource(R.string.bk_never),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.S16, vertical = Space.S12), horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.lastProblemAt?.let { dayOnly(it, today, fmt) } ?: "", style = MaterialTheme.typography.labelLarge, color = c.text)
                        Text(stringResource(R.string.bk_not_saved), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    Text(stringResource(R.string.bk_failed_tag), style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
            Note(stringResource(if (space) R.string.bk_failed_hint_space else R.string.bk_failed_hint_other))
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.bk_try_again), onTryAgain, enabled = !busy)
            LinkButton(stringResource(R.string.bk_later), onLater)
        }
    }
}

@Composable
private fun dayOnly(at: LocalDateTime, today: LocalDate, fmt: Formatter): String = when (at.toLocalDate()) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.bk_yesterday)
    else -> shortDate(at.toLocalDate(), fmt)
}

@Composable
fun FolderUnavailableDialog(label: String?, onChoose: () -> Unit, onLater: () -> Unit) {
    MessageDialog(
        DialogKind.Warning, stringResource(R.string.bk_unavail_title),
        stringResource(R.string.bk_unavail_body, label?.ifEmpty { null } ?: stringResource(R.string.bk_folder_root)), onLater,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.bk_choose_folder), onChoose)
            LinkButton(stringResource(R.string.bk_later), onLater)
        }
    }
}

// ---------------------------------------------------------------- Delete all my data

@Composable
fun DeleteAllDialog(onKeep: () -> Unit, onDelete: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    val c = PillBox.colors
    MessageDialog(DialogKind.Danger, stringResource(R.string.da_title), stringResource(R.string.da_body), onKeep, icon = Icons.Filled.Delete) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin)
                    .toggleable(understood, role = Role.Checkbox, onValueChange = { understood = it })
                    .padding(vertical = Space.S4),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                androidx.compose.material3.Checkbox(
                    checked = understood, onCheckedChange = null,
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = c.danger, uncheckedColor = c.border, checkmarkColor = c.onPrimary),
                )
                Text(stringResource(R.string.da_check), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            }
            DangerButton(stringResource(R.string.da_do), onDelete, enabled = understood)
            OutlineButton(stringResource(R.string.da_keep), onKeep)
        }
    }
}

@Composable
fun DeleteDoneContent(onStartAgain: () -> Unit, onRestore: () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(80.dp).background(c.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(36.dp))
            }
            Text(
                stringResource(R.string.da_done_title), style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.S16).semantics { heading() },
            )
            Text(stringResource(R.string.da_done_body), style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S8))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.da_start), onStartAgain)
            OutlineButton(stringResource(R.string.bk_restore), onRestore)
        }
    }
}

// ---------------------------------------------------------------- Restore from backup

@Composable
fun RestoreScreen(vm: BackupViewModel, onBack: () -> Unit, onSetUpReminders: () -> Unit, onGoToToday: () -> Unit) {
    val state by vm.restore.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.filePicked(uri) }
    val choose = { pick.launch(arrayOf("*/*")) }
    when (val s = state) {
        RestoreState.Intro, RestoreState.Checking, RestoreState.Restoring -> RestoreIntroContent(onBack, choose, busy = s != RestoreState.Intro)
        is RestoreState.Confirm -> RestoreConfirmContent(s, fmt, LocalDate.now(), onBack, onRestore = vm::restoreNow, onAnother = choose)
        is RestoreState.Done -> RestoreDoneContent(s.summary, fmt, onSetUpReminders, onGoToToday)
        RestoreState.Invalid -> RestoreErrorContent(onBack, onAnother = choose, body = R.string.rs_err_body, safe = R.string.rs_err_safe, title = R.string.rs_err_title)
        RestoreState.Failed -> RestoreErrorContent(onBack, onAnother = choose, body = R.string.rs_failed_body, safe = R.string.rs_err_safe, title = R.string.rs_failed_title)
        RestoreState.Newer -> {
            RestoreIntroContent(onBack, choose, busy = false)
            MessageDialog(DialogKind.Warning, stringResource(R.string.rs_newer_title), stringResource(R.string.rs_newer_body), onDismiss = vm::startRestore, icon = Icons.Filled.Info) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                    PrimaryButton(stringResource(R.string.rs_ok), vm::startRestore)
                    OutlineButton(stringResource(R.string.rs_another), choose)
                }
            }
        }
    }
}

@Composable
fun RestoreIntroContent(onBack: () -> Unit, onChoose: () -> Unit, busy: Boolean = false) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.rs_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S20), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                Box(Modifier.size(64.dp).background(c.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
                }
                Text(stringResource(R.string.rs_choose_title), style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.rs_choose_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
            }
            Row(
                Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.large).padding(Space.S16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.rs_warn_replace), style = MaterialTheme.typography.bodyMedium, color = c.warning, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.rs_choose_btn), onChoose, enabled = !busy)
            LinkButton(stringResource(R.string.bk_cancel), onBack)
        }
    }
}

@Composable
fun RestoreConfirmContent(s: RestoreState.Confirm, fmt: Formatter, today: LocalDate, onBack: () -> Unit, onRestore: () -> Unit, onAnother: () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.rs_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Card {
                Row(Modifier.fillMaxWidth().padding(Space.S12), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                    Tile(c.successContainer) { Icon(Icons.Filled.Check, contentDescription = null, tint = c.success) }
                    Column(Modifier.weight(1f)) {
                        if (s.fileName.isNotEmpty()) Text(s.fileName, style = MaterialTheme.typography.labelLarge, color = c.text)
                        s.savedAt?.let { Text(stringResource(R.string.rs_saved, whenText(it, today, fmt)), style = MaterialTheme.typography.bodyMedium, color = c.textMuted) }
                    }
                }
                RowDivider()
                CountRow(stringResource(R.string.rs_people), fmt.number(s.summary.people))
                RowDivider()
                CountRow(stringResource(R.string.rs_meds), fmt.number(s.summary.medicines))
                RowDivider()
                CountRow(stringResource(R.string.rs_doses), fmt.number(s.summary.doses))
            }
            Row(
                Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.large).padding(Space.S16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.rs_warn_confirm), style = MaterialTheme.typography.bodyMedium, color = c.warning, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.rs_do), onRestore)
            OutlineButton(stringResource(R.string.rs_another), onAnother)
        }
    }
}

@Composable
private fun CountRow(label: String, value: String) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.ListRowMinHeight).padding(horizontal = Space.S16, vertical = Space.S8)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
    }
}

@Composable
fun RestoreDoneContent(summary: BackupSummary, fmt: Formatter, onSetUpReminders: () -> Unit, onGoToToday: () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(80.dp).background(c.successContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(40.dp))
            }
            Text(
                stringResource(R.string.rs_done_title), style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.S16).semantics { heading() },
            )
            Text(
                stringResource(
                    R.string.rs_done_body,
                    pluralStringResource(R.plurals.rs_n_people, summary.people, fmt.number(summary.people)),
                    pluralStringResource(R.plurals.rs_n_meds, summary.medicines, fmt.number(summary.medicines)),
                    pluralStringResource(R.plurals.rs_n_doses, summary.doses, fmt.number(summary.doses)),
                ),
                style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S8),
            )
            Row(
                Modifier.padding(top = Space.S16).fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.rs_remind_note), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.rs_set_up), onSetUpReminders)
            OutlineButton(stringResource(R.string.rs_go_today), onGoToToday)
        }
    }
}

@Composable
fun RestoreErrorContent(onBack: () -> Unit, onAnother: () -> Unit, title: Int, body: Int, safe: Int) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.rs_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            Row(
                Modifier.fillMaxWidth().background(c.dangerContainer, MaterialTheme.shapes.large).padding(Space.S16),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.danger, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = c.danger)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.danger)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(c.successContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(24.dp))
                Text(stringResource(safe), style = MaterialTheme.typography.bodyMedium, color = c.success, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.rs_another), onAnother)
            LinkButton(stringResource(R.string.bk_cancel), onBack)
        }
    }
}
