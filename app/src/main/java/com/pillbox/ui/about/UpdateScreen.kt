package com.pillbox.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pillbox.PillBoxApp
import com.pillbox.R
import com.pillbox.domain.UpdateInfo
import com.pillbox.ui.components.DesignIcons
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.settings.Card
import com.pillbox.ui.settings.SubHeader
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.update.CheckResult
import com.pillbox.update.DownloadResult
import java.io.File

/** What the update screen shows. */
sealed interface UpdateUi {
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class AllowInstall(val info: UpdateInfo) : UpdateUi
    data class Ready(val info: UpdateInfo) : UpdateUi
    data class Failed(val info: UpdateInfo?) : UpdateUi
}

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PillBoxApp
    val m = app.updates
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Checking) }
    var file by remember { mutableStateOf<File?>(null) }
    val settings by m.settings.flow.collectAsState(com.pillbox.domain.UpdateSettings())

    suspend fun check() {
        ui = UpdateUi.Checking
        ui = when (val r = m.check()) {
            is CheckResult.Available -> UpdateUi.Available(r.info)
            CheckResult.UpToDate -> UpdateUi.UpToDate
            CheckResult.Failed -> UpdateUi.Failed(null)
        }
    }
    suspend fun download(info: UpdateInfo) {
        ui = UpdateUi.Downloading(info, 0f)
        when (val r = m.download(info) { p -> ui = UpdateUi.Downloading(info, p) }) {
            is DownloadResult.Ready -> { file = r.file; ui = UpdateUi.Ready(info); runCatching { context.startActivity(m.installIntent(r.file)) } }
            DownloadResult.Failed -> ui = UpdateUi.Failed(info)
        }
    }
    LaunchedEffect(Unit) { check() }
    // Coming back from Android's "allow installs" page.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            val cur = ui
            if (e == Lifecycle.Event.ON_RESUME && cur is UpdateUi.AllowInstall && m.canInstall()) scope.launch { download(cur.info) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    UpdateContent(
        ui = ui, enabled = settings.enabled, onBack = onBack,
        onToggle = { on -> scope.launch { m.setEnabled(on) } },
        onDownload = { info -> if (m.canInstall()) scope.launch { download(info) } else ui = UpdateUi.AllowInstall(info) },
        onOpenAllow = { runCatching { context.startActivity(m.allowInstallIntent()) } },
        onInstall = { file?.let { f -> runCatching { context.startActivity(m.installIntent(f)) } } },
        onRetry = { scope.launch { check() } },
    )
}

@Composable
fun UpdateContent(
    ui: UpdateUi, enabled: Boolean, onBack: () -> Unit, onToggle: (Boolean) -> Unit,
    onDownload: (UpdateInfo) -> Unit, onOpenAllow: () -> Unit, onInstall: () -> Unit, onRetry: () -> Unit,
) {
    val c = PillBox.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.upd_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                when (ui) {
                    UpdateUi.Checking -> Status(DesignIcons.Refresh, R.string.upd_checking, null, live = true)
                    UpdateUi.UpToDate -> Status(DesignIcons.Check, R.string.upd_uptodate_title, R.string.upd_uptodate_body)
                    is UpdateUi.Available -> {
                        Status(DesignIcons.Info, R.string.upd_found_title, null, versionName = ui.info.versionName)
                        if (ui.info.notes.isNotBlank()) Notes(ui.info.notes)
                        PrimaryButton(stringResource(R.string.upd_download), { onDownload(ui.info) })
                    }
                    is UpdateUi.Downloading -> {
                        Status(DesignIcons.Info, R.string.upd_downloading, null, live = true)
                        LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = Space.S4), color = c.primary, trackColor = c.primaryContainer)
                        Text(stringResource(R.string.upd_percent, rememberFormatter().digits((ui.progress * 100).toInt().toString())), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    }
                    is UpdateUi.AllowInstall -> {
                        Status(DesignIcons.Lock, R.string.upd_allow_title, R.string.upd_allow_body)
                        PrimaryButton(stringResource(R.string.upd_allow_button), onOpenAllow)
                    }
                    is UpdateUi.Ready -> {
                        Status(DesignIcons.Check, R.string.upd_ready_title, R.string.upd_ready_body)
                        PrimaryButton(stringResource(R.string.upd_install), onInstall)
                    }
                    is UpdateUi.Failed -> {
                        Status(DesignIcons.Info, R.string.upd_failed_title, R.string.upd_failed_body)
                        PrimaryButton(stringResource(R.string.upd_try_again), onRetry)
                    }
                }
                Card { SwitchRow(stringResource(R.string.upd_switch), enabled, onToggle, subtitle = stringResource(R.string.upd_switch_hint)) }
                Text(stringResource(R.string.upd_privacy), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(vertical = Space.S8))
            }
        }
    }
}

@Composable
private fun Status(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, body: Int?, live: Boolean = false, versionName: String? = null) {
    val c = PillBox.colors
    Column(
        Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.large).padding(Space.S16)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        verticalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
            Text(
                if (versionName != null) stringResource(title, rememberFormatter().digits(versionName)) else stringResource(title),
                style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f),
            )
        }
        if (body != null) Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = c.text)
    }
}

@Composable
private fun Notes(notes: String) {
    val c = PillBox.colors
    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S6)) {
        Text(stringResource(R.string.upd_whats_new), style = MaterialTheme.typography.labelLarge, color = c.text)
        Text(notes, style = MaterialTheme.typography.bodyLarge, color = c.text)
    }
}
