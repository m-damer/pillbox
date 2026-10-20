package com.pillbox.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillbox.PillBoxApp

/** Once a week: save a backup into the chosen folder. A failure is recorded and shown on the Backup screen; it is not retried in a loop. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val backups = (applicationContext as PillBoxApp).backups
        if (!backups.settings.current().autoFolder) return Result.success()
        backups.backupNow()
        return Result.success()
    }
}
