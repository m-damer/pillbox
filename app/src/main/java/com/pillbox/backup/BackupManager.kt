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
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Makes, shares and reads backup files. Everything stays on the phone unless the person shares the file. */
class BackupManager(private val context: Context, private val repo: PillBoxRepository, val settings: BackupSettingsStore) {

    sealed interface Outcome {
        data class Saved(val fileName: String) : Outcome
        data class Failed(val problem: BackupProblem) : Outcome
    }

    private val resolver get() = context.contentResolver

    private suspend fun json(now: LocalDateTime) = BackupCodec.encode(repo.exportBackup(), now)

    /** Saves a backup into the chosen folder and records how it went. */
    suspend fun backupNow(now: LocalDateTime = LocalDateTime.now()): Outcome = withContext(Dispatchers.IO) {
        val s = settings.current()
        val outcome = try {
            val tree = Uri.parse(s.folderUri ?: return@withContext fail(now, BackupProblem.FOLDER_UNAVAILABLE))
            Outcome.Saved(writeTo(tree, json(now), now))
        } catch (e: FolderGone) {
            Outcome.Failed(BackupProblem.FOLDER_UNAVAILABLE)
        } catch (e: IOException) {
            Outcome.Failed(if (isNoSpace(e)) BackupProblem.NO_SPACE else BackupProblem.OTHER)
        } catch (e: SecurityException) {
            Outcome.Failed(BackupProblem.FOLDER_UNAVAILABLE)
        } catch (e: Exception) {
            Outcome.Failed(BackupProblem.OTHER)
        }
        when (outcome) {
            is Outcome.Saved -> settings.update { it.copy(lastSuccessAt = now, lastProblem = null, lastProblemAt = null) }
            is Outcome.Failed -> settings.update { it.copy(lastProblem = outcome.problem, lastProblemAt = now) }
        }
        outcome
    }

    private suspend fun fail(now: LocalDateTime, p: BackupProblem): Outcome {
        settings.update { it.copy(lastProblem = p, lastProblemAt = now) }
        return Outcome.Failed(p)
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
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } } ?: throw FolderGone()
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
        scheduleWeekly()
    }

    suspend fun setAuto(on: Boolean) {
        settings.update { it.copy(autoFolder = on) }
        if (on) scheduleWeekly() else cancelWeekly()
    }

    fun scheduleWeekly() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).setConstraints(Constraints.NONE).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
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
        const val KEEP = 4
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
