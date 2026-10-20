package com.pillbox.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillbox.PillBoxApp

/** Daily or weekly, as chosen: save a backup into the chosen folder. A failure is recorded and shown on the Backup screen; it is not retried in a loop. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backups = (applicationContext as PillBoxApp).backups
        if (!backups.wantsAutomatic()) return Result.success()
        val run = backups.backupNow(automatic = true, attempt = runAttemptCount)
        // Google Drive needs internet: wait and try again on its own, a couple of times.
        if (run.drive == com.pillbox.domain.DriveProblem.NO_INTERNET && runAttemptCount < BackupManager.RETRIES) return Result.retry()
        return Result.success()
    }
}
