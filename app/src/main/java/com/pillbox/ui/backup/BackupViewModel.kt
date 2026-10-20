package com.pillbox.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.backup.BackupManager
import com.pillbox.domain.BackupData
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupResult
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.BackupSummary
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(val settings: BackupSettings = BackupSettings(), val busy: Boolean = false)

/** One-time things the Backup screen reacts to: a toast, a dialog, the Share sheet or the folder picker. */
sealed interface BackupEvent {
    data class Saved(val fileName: String) : BackupEvent
    data class FolderSet(val label: String?) : BackupEvent
    data class Failed(val problem: BackupProblem) : BackupEvent
    data class ShareFile(val uri: Uri, val name: String) : BackupEvent
    data object ShareFailed : BackupEvent
    data object NeedFolder : BackupEvent
}

sealed interface RestoreState {
    data object Intro : RestoreState
    data object Checking : RestoreState
    data class Confirm(val fileName: String, val savedAt: LocalDateTime?, val summary: BackupSummary, val data: BackupData) : RestoreState
    data object Restoring : RestoreState
    data class Done(val summary: BackupSummary) : RestoreState
    /** Not a Pill Box backup, or damaged. */
    data object Invalid : RestoreState
    data object Newer : RestoreState
    data object Failed : RestoreState
}

class BackupViewModel(
    private val manager: BackupManager,
    /** Run after a restore or a delete, so reminders match the new data. */
    private val onDataChanged: suspend () -> Unit,
    /** "Delete all my data" also resets what lives outside the backup manager (reminder and refill settings). */
    private val resetOtherSettings: suspend () -> Unit,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    val state: StateFlow<BackupUiState> = combine(manager.settings.flow, busy) { s, b -> BackupUiState(s, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.Intro)
    val restore: StateFlow<RestoreState> = _restore

    private fun emitResult(o: BackupManager.Outcome) {
        _events.trySend(when (o) { is BackupManager.Outcome.Saved -> BackupEvent.Saved(o.fileName); is BackupManager.Outcome.Failed -> BackupEvent.Failed(o.problem) })
    }

    fun saveNow() = viewModelScope.launch {
        if (!manager.settings.current().hasFolder) { _events.trySend(BackupEvent.NeedFolder); return@launch }
        busy.value = true
        try { emitResult(manager.backupNow()) } finally { busy.value = false }
    }

    /** The person picked a folder: remember it, start the weekly job, and save the first backup there. */
    fun folderPicked(tree: Uri) = viewModelScope.launch {
        busy.value = true
        try {
            manager.setFolder(tree)
            val label = manager.settings.current().folderLabel
            when (val o = manager.backupNow()) {
                is BackupManager.Outcome.Saved -> _events.trySend(BackupEvent.FolderSet(label))
                is BackupManager.Outcome.Failed -> _events.trySend(BackupEvent.Failed(o.problem))
            }
        } finally { busy.value = false }
    }

    fun setAuto(on: Boolean) = viewModelScope.launch {
        if (on && !manager.settings.current().hasFolder) { _events.trySend(BackupEvent.NeedFolder); return@launch }
        manager.setAuto(on)
    }

    fun share() = viewModelScope.launch {
        busy.value = true
        try {
            val (file, uri) = manager.fileForSharing()
            _events.trySend(BackupEvent.ShareFile(uri, file.name))
        } catch (e: Exception) {
            _events.trySend(BackupEvent.ShareFailed)
        } finally { busy.value = false }
    }

    // ---- restore ----

    fun startRestore() { _restore.value = RestoreState.Intro }

    fun filePicked(uri: Uri) = viewModelScope.launch {
        _restore.value = RestoreState.Checking
        val name = manager.displayName(uri) ?: ""
        _restore.value = when (val r = manager.read(uri)) {
            is BackupResult.Ok -> RestoreState.Confirm(name, r.savedAt, r.data.summary, r.data)
            is BackupResult.Newer -> RestoreState.Newer
            BackupResult.Invalid -> RestoreState.Invalid
        }
    }

    fun restoreNow() = viewModelScope.launch {
        val confirm = _restore.value as? RestoreState.Confirm ?: return@launch
        _restore.value = RestoreState.Restoring
        _restore.value = try {
            manager.restore(confirm.data)
            onDataChanged()
            RestoreState.Done(confirm.summary)
        } catch (e: Exception) {
            RestoreState.Failed
        }
    }

    fun deleteAll(onDone: () -> Unit) = viewModelScope.launch {
        busy.value = true
        try {
            manager.deleteEverything()
            resetOtherSettings()
            onDataChanged()
            onDone()
        } finally { busy.value = false }
    }
}
