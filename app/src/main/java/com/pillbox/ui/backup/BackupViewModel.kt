package com.pillbox.ui.backup

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.backup.BackupManager
import com.pillbox.backup.DriveClient
import com.pillbox.domain.BackupData
import com.pillbox.domain.BackupFrequency
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupResult
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.BackupSummary
import com.pillbox.domain.DriveProblem
import java.time.LocalDateTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(val settings: BackupSettings = BackupSettings(), val busy: Boolean = false)

/** Why Google asks the person for a screen: to connect the account, or to read a backup from Drive. */
enum class ConsentFor { CONNECT, RESTORE }

/** One-time things the Backup screen reacts to: a toast, a dialog, the Share sheet, the folder picker or Google's sign-in screen. */
sealed interface BackupEvent {
    data class Saved(val fileName: String) : BackupEvent
    data class FolderSet(val label: String?) : BackupEvent
    data class Failed(val problem: BackupProblem) : BackupEvent
    data class DriveFailed(val problem: DriveProblem) : BackupEvent
    data object DriveConnected : BackupEvent
    data class ShareFile(val uri: Uri, val name: String) : BackupEvent
    data object ShareFailed : BackupEvent
    data object NeedFolder : BackupEvent
    data class Consent(val sender: IntentSender) : BackupEvent
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
    data object DriveEmpty : RestoreState
    data object DriveOffline : RestoreState
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

    private var consentFor = ConsentFor.CONNECT

    private fun emit(run: BackupManager.Run) {
        if (run.saved) _events.trySend(BackupEvent.Saved(run.fileName))
        if (run.folderTried && run.folder != null) _events.trySend(BackupEvent.Failed(run.folder))
        if (run.driveTried && run.drive != null) _events.trySend(BackupEvent.DriveFailed(run.drive))
    }

    fun saveNow() = viewModelScope.launch {
        val s = manager.settings.current()
        if (!s.hasFolder && !s.driveOn) { _events.trySend(BackupEvent.NeedFolder); return@launch }
        busy.value = true
        try { emit(manager.backupNow()) } finally { busy.value = false }
    }

    /** The person picked a folder: remember it, start the repeating job, and save the first backup there. */
    fun folderPicked(tree: Uri) = viewModelScope.launch {
        busy.value = true
        try {
            manager.setFolder(tree)
            val run = manager.backupNow()
            if (run.toFolder) _events.trySend(BackupEvent.FolderSet(manager.settings.current().folderLabel))
            else if (run.folder != null) _events.trySend(BackupEvent.Failed(run.folder))
            if (run.driveTried && run.drive != null) _events.trySend(BackupEvent.DriveFailed(run.drive))
        } finally { busy.value = false }
    }

    fun setAuto(on: Boolean) = viewModelScope.launch {
        if (on && !manager.settings.current().hasFolder) { _events.trySend(BackupEvent.NeedFolder); return@launch }
        manager.setAuto(on)
    }

    fun setFrequency(f: BackupFrequency) = viewModelScope.launch { manager.setFrequency(f) }

    fun setNotify(on: Boolean) = viewModelScope.launch { manager.setNotify(on) }

    fun share() = viewModelScope.launch {
        busy.value = true
        try {
            val (file, uri) = manager.fileForSharing()
            _events.trySend(BackupEvent.ShareFile(uri, file.name))
        } catch (e: Exception) {
            _events.trySend(BackupEvent.ShareFailed)
        } finally { busy.value = false }
    }

    // ---- Google Drive ----

    /** "Choose Google account": Google's own screen picks the account and asks once. */
    fun connectDrive() = viewModelScope.launch {
        consentFor = ConsentFor.CONNECT
        busy.value = true
        try {
            when (val a = manager.driveClient.authorize()) {
                is DriveClient.Auth.Ready -> finishConnect(a.email)
                is DriveClient.Auth.NeedsConsent -> _events.trySend(BackupEvent.Consent(a.intentSender))
                is DriveClient.Auth.Failed -> _events.trySend(BackupEvent.DriveFailed(a.problem))
            }
        } finally { busy.value = false }
    }

    fun disconnectDrive() = viewModelScope.launch { manager.disconnectDrive() }

    /** The sign-in screen closed. */
    fun consentResult(data: Intent?) = viewModelScope.launch {
        when (val a = manager.driveClient.fromIntent(data)) {
            is DriveClient.Auth.Ready -> if (consentFor == ConsentFor.CONNECT) finishConnect(a.email) else restoreFromDriveWith(a.token)
            is DriveClient.Auth.NeedsConsent -> if (consentFor == ConsentFor.RESTORE) _restore.value = RestoreState.Failed
            is DriveClient.Auth.Failed ->
                if (consentFor == ConsentFor.RESTORE) _restore.value = if (a.problem == DriveProblem.NO_INTERNET) RestoreState.DriveOffline else RestoreState.Intro
                // Closing the screen without choosing is not an error.
                else if (a.problem != DriveProblem.SIGN_IN) _events.trySend(BackupEvent.DriveFailed(a.problem))
        }
    }

    private suspend fun finishConnect(email: String?) {
        manager.connectDrive(email)
        _events.trySend(BackupEvent.DriveConnected)
        // Start with a copy right away, so Drive is not empty until the next scheduled backup.
        busy.value = true
        try {
            val run = manager.backupNow()
            if (run.driveTried && run.drive != null) _events.trySend(BackupEvent.DriveFailed(run.drive))
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

    /** "From Google Drive": the newest backup there. */
    fun startDriveRestore() = viewModelScope.launch {
        consentFor = ConsentFor.RESTORE
        _restore.value = RestoreState.Checking
        when (val a = manager.driveClient.authorize()) {
            is DriveClient.Auth.Ready -> restoreFromDriveWith(a.token)
            is DriveClient.Auth.NeedsConsent -> _events.trySend(BackupEvent.Consent(a.intentSender))
            is DriveClient.Auth.Failed -> _restore.value = if (a.problem == DriveProblem.NO_INTERNET) RestoreState.DriveOffline else RestoreState.Failed
        }
    }

    private suspend fun restoreFromDriveWith(token: String) {
        _restore.value = RestoreState.Checking
        _restore.value = when (val r = manager.readFromDrive(token)) {
            BackupManager.DriveRead.Empty -> RestoreState.DriveEmpty
            is BackupManager.DriveRead.Problem -> if (r.problem == DriveProblem.NO_INTERNET) RestoreState.DriveOffline else RestoreState.Failed
            is BackupManager.DriveRead.Found -> when (val x = r.result) {
                is BackupResult.Ok -> RestoreState.Confirm(r.name, x.savedAt, x.data.summary, x.data)
                is BackupResult.Newer -> RestoreState.Newer
                BackupResult.Invalid -> RestoreState.Invalid
            }
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
