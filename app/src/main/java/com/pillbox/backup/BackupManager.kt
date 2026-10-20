package com.pillbox.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pillbox.data.BackupSettingsStore
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.BackupCodec
import com.pillbox.domain.BackupFolders
import com.pillbox.domain.BackupProblem
import com.pillbox.domain.BackupResult
import com.pillbox.domain.DriveProblem
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Makes, shares and reads backup files. Everything stays on the phone unless the person shares the file. */
class BackupManager(private val context: Context, private val repo: PillBoxRepository, val settings: BackupSettingsStore) {

    /** How one backup run went: what was tried, and what went wrong where. A target that was not tried has no problem. */
    data class Run(
        val fileName: String,
        val folderTried: Boolean, val folder: BackupProblem?,
        val driveTried: Boolean, val drive: DriveProblem?,
    ) {
        val toFolder get() = folderTried && folder == null
        val toDrive get() = driveTried && drive == null
        val saved get() = toFolder || toDrive
        val nothingToDo get() = !folderTried && !driveTried
    }

    private val resolver get() = context.contentResolver
    val driveClient by lazy { DriveClient(context) }

    private suspend fun json(now: LocalDateTime) = BackupCodec.encode(repo.exportBackup(), now)

    /** Saves a backup into the chosen folder and to Google Drive (each only if set up), and records how it went. */
    suspend fun backupNow(now: LocalDateTime = LocalDateTime.now(), automatic: Boolean = false, attempt: Int = 0): Run = withContext(Dispatchers.IO) {
        val s = settings.current()
        val name = BackupCodec.fileName(now.toLocalDate())
        val folderTried = s.hasFolder && (!automatic || s.autoFolder)
        val driveTried = s.driveOn
        if (!folderTried && !driveTried) return@withContext Run(name, false, null, false, null)
        val text = try { json(now) } catch (e: Exception) { null }
        var folder: BackupProblem? = null
        var drive: DriveProblem? = null
        if (text == null) {
            if (folderTried) folder = BackupProblem.OTHER
            if (driveTried) drive = DriveProblem.OTHER
        } else {
            if (folderTried) folder = try {
                writeTo(Uri.parse(s.folderUri), text, now); null
            } catch (e: FolderGone) {
                BackupProblem.FOLDER_UNAVAILABLE
            } catch (e: IOException) {
                if (isNoSpace(e)) BackupProblem.NO_SPACE else BackupProblem.OTHER
            } catch (e: SecurityException) {
                BackupProblem.FOLDER_UNAVAILABLE
            } catch (e: Exception) {
                BackupProblem.OTHER
            }
            if (driveTried) drive = uploadToDrive(name, text)
        }
        val run = Run(name, folderTried, folder, driveTried, drive)
        settings.update {
            var n = it
            if (run.saved) n = n.copy(lastSuccessAt = now, lastToFolder = run.toFolder, lastToDrive = run.toDrive)
            if (folderTried) n = n.copy(lastProblem = folder, lastProblemAt = if (folder != null) now else null)
            if (driveTried) n = n.copy(driveProblem = drive, driveProblemAt = if (drive != null) now else null)
            n
        }
        // No internet is tried again by the worker a couple of times before the person is told.
        if (automatic && s.notify && !(run.drive == DriveProblem.NO_INTERNET && attempt < RETRIES)) notify(run, s.folderLabel)
        run
    }

    private suspend fun uploadToDrive(name: String, text: String): DriveProblem? =
        when (val auth = driveClient.authorize()) {
            is DriveClient.Auth.Ready -> when (val r = driveClient.upload(auth.token, name, text, KEEP)) {
                is DriveClient.Result.Ok -> null
                is DriveClient.Result.Problem -> r.problem
            }
            // Choosing an account needs a screen, which a backup running by itself does not have.
            is DriveClient.Auth.NeedsConsent -> DriveProblem.SIGN_IN
            is DriveClient.Auth.Failed -> auth.problem
        }

    /** Google Drive is connected: remember the account and start with a backup there. */
    suspend fun connectDrive(email: String?) {
        settings.update { it.copy(driveOn = true, driveEmail = email ?: it.driveEmail, driveProblem = null, driveProblemAt = null) }
        reconcile()
    }

    suspend fun disconnectDrive() {
        settings.update { it.copy(driveOn = false, driveEmail = null, driveProblem = null, driveProblemAt = null) }
        reconcile()
    }

    /** The newest backup in Google Drive, read and checked, for "Restore from Google Drive". */
    suspend fun readFromDrive(token: String): DriveRead = withContext(Dispatchers.IO) {
        when (val r = driveClient.newest(token)) {
            is DriveClient.Result.Problem -> DriveRead.Problem(r.problem)
            is DriveClient.Result.Ok -> {
                val found = r.value ?: return@withContext DriveRead.Empty
                val result = BackupCodec.decode(found.second)
                if (result is BackupResult.Ok && runCatching { com.pillbox.data.BackupMapper.toEntities(result.data) }.isFailure) DriveRead.Found(found.first.name, BackupResult.Invalid)
                else DriveRead.Found(found.first.name, result)
            }
        }
    }

    sealed interface DriveRead {
        data object Empty : DriveRead
        data class Problem(val problem: DriveProblem) : DriveRead
        data class Found(val name: String, val result: BackupResult) : DriveRead
    }

    private class FolderGone : Exception()

    private fun isNoSpace(e: IOException) = e.message?.contains("ENOSPC", true) == true || e.message?.contains("No space", true) == true

    private fun writeTo(tree: Uri, text: String, now: LocalDateTime): String {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission }) throw FolderGone()
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val children = try {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { c -> buildList<Pair<String, String>> { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } } ?: throw FolderGone()
        } catch (e: IllegalArgumentException) {
            throw FolderGone()
        }
        val name = BackupCodec.fileName(now.toLocalDate())
        // The same day twice replaces the file instead of piling up "(1)" copies.
        children.filter { it.second == name }.forEach { delete(tree, it.first) }
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: throw FolderGone()
        try {
            (resolver.openOutputStream(doc, "wt") ?: throw IOException("no stream")).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            // Read it back: a backup that cannot be read is worse than none.
            val back = resolver.openInputStream(doc)?.use { it.readBytes().toString(Charsets.UTF_8) }
            if (back == null || BackupCodec.decode(back) !is BackupResult.Ok) throw IOException("check failed")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        // Keep the newest few, so the folder does not fill up over the years. Only files named like ours are ever removed.
        val ours = children.associate { it.second to it.first } + (name to DocumentsContract.getDocumentId(doc))
        BackupCodec.toRemove(ours.keys.toList(), KEEP).forEach { n -> ours[n]?.let { delete(tree, it) } }
        return name
    }

    private fun delete(tree: Uri, documentId: String) {
        runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, documentId)) }
    }

    /** Remembers the chosen folder (the permission survives restarts) and starts the weekly job. */
    suspend fun setFolder(tree: Uri) {
        runCatching { resolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val path = BackupFolders.path(DocumentsContract.getTreeDocumentId(tree))
        settings.update { it.copy(autoFolder = true, folderUri = tree.toString(), folderLabel = path, lastProblem = null, lastProblemAt = null) }
        schedule(replace = true)
    }

    suspend fun setAuto(on: Boolean) {
        settings.update { it.copy(autoFolder = on) }
        reconcile()
    }

    /** The repeating job runs while either the folder or Google Drive wants automatic backups. */
    suspend fun wantsAutomatic(): Boolean = settings.current().let { (it.autoFolder && it.hasFolder) || it.driveOn }

    suspend fun reconcile(replace: Boolean = false) { if (wantsAutomatic()) schedule(replace) else cancelWeekly() }

    /** Starts the repeating job (daily or weekly). [replace] restarts it, for when the choice changed; otherwise a running job is left alone. */
    suspend fun schedule(replace: Boolean = false) {
        val days = settings.current().frequency.days
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS).setConstraints(Constraints.NONE).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun setFrequency(f: com.pillbox.domain.BackupFrequency) {
        settings.update { it.copy(frequency = f) }
        reconcile(replace = true)
    }

    suspend fun setNotify(on: Boolean) { settings.update { it.copy(notify = on) } }

    /** One quiet notification after an automatic backup: saved, or did not finish. Not sent when notifications are off. */
    private fun notify(run: Run, folderLabel: String?) {
        val nm = androidx.core.app.NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val failed = (run.folderTried && run.folder != null) || (run.driveTried && run.drive != null)
        val channel = if (failed) CHANNEL_FAILED else CHANNEL_OK
        val system = context.getSystemService(android.app.NotificationManager::class.java)
        system.createNotificationChannel(android.app.NotificationChannel(CHANNEL_OK, context.getString(com.pillbox.R.string.channel_backup_ok), android.app.NotificationManager.IMPORTANCE_LOW))
        system.createNotificationChannel(android.app.NotificationChannel(CHANNEL_FAILED, context.getString(com.pillbox.R.string.channel_backup_failed), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val place = folderLabel?.ifEmpty { null } ?: context.getString(com.pillbox.R.string.bk_folder_root)
        val title: String
        val text: String
        if (!failed) {
            title = context.getString(com.pillbox.R.string.bk_saved_title)
            text = when {
                run.toFolder && run.toDrive -> context.getString(com.pillbox.R.string.bk_notif_saved_both, run.fileName, place)
                run.toDrive -> context.getString(com.pillbox.R.string.bk_notif_saved_drive, run.fileName)
                else -> context.getString(com.pillbox.R.string.bk_notif_saved, run.fileName, place)
            }
        } else if (run.folderTried && run.folder != null) {
            title = context.getString(com.pillbox.R.string.bk_failed_title)
            text = context.getString(when (run.folder) {
                BackupProblem.NO_SPACE -> com.pillbox.R.string.bk_failed_space
                BackupProblem.FOLDER_UNAVAILABLE -> com.pillbox.R.string.bk_notif_folder
                else -> com.pillbox.R.string.bk_failed_other
            })
        } else {
            title = context.getString(com.pillbox.R.string.dr_failed_title)
            text = context.getString(driveReason(run.drive))
        }
        val open = android.app.PendingIntent.getActivity(
            context, NOTIFICATION_ID, android.content.Intent(context, com.pillbox.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, channel)
            .setSmallIcon(com.pillbox.R.drawable.ic_stat_pill).setContentTitle(title).setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private fun driveReason(p: DriveProblem?) = when (p) {
        DriveProblem.NO_INTERNET -> com.pillbox.R.string.dr_failed_net
        DriveProblem.SIGN_IN -> com.pillbox.R.string.dr_failed_signin
        else -> com.pillbox.R.string.dr_failed_other
    }

    fun cancelWeekly() { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }

    /** A copy in the cache for the Share sheet. */
    suspend fun fileForSharing(now: LocalDateTime = LocalDateTime.now()): Pair<File, Uri> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, BackupCodec.fileName(now.toLocalDate()))
        file.writeText(json(now), Charsets.UTF_8)
        file to androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Reads and checks a file the person picked. Nothing is changed. */
    suspend fun read(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val bytes = resolver.openInputStream(uri)?.use { readUpTo(it, MAX_BYTES + 1) } ?: return@withContext BackupResult.Invalid
            if (bytes.size > MAX_BYTES) return@withContext BackupResult.Invalid
            val result = BackupCodec.decode(bytes.toString(Charsets.UTF_8))
            // Rows of the wrong kind are found now, before anything is offered for restoring.
            if (result is BackupResult.Ok && runCatching { com.pillbox.data.BackupMapper.toEntities(result.data) }.isFailure) BackupResult.Invalid else result
        } catch (e: Exception) {
            BackupResult.Invalid
        }
    }

    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** The name the phone shows for a picked file. */
    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Replaces everything on the phone with a backup that was already read and checked. */
    suspend fun restore(data: com.pillbox.domain.BackupData) = withContext(Dispatchers.IO) { repo.restoreBackup(data) }

    /** "Delete all my data": the database, the settings and every alarm and notification. Backups saved elsewhere stay. */
    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        repo.deleteEverything()
        cancelWeekly()
        settings.clear()
        File(context.cacheDir, "backups").deleteRecursively()
        File(context.cacheDir, "reports").deleteRecursively()
    }

    companion object {
        const val WORK_NAME = "weekly-backup"
        const val KEEP = 7
        const val RETRIES = 2
        const val CHANNEL_OK = "backup_ok"
        const val CHANNEL_FAILED = "backup_failed"
        const val NOTIFICATION_ID = 700_000_001
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
