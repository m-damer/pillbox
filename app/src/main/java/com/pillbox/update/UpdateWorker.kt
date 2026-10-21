package com.pillbox.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillbox.PillBoxApp
import com.pillbox.domain.UpdateRules

/** The weekly look for a new version. Quiet: one notification per version, and none when the user turned it off. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val m = (applicationContext as PillBoxApp).updates
        val s = m.settings.current()
        if (!s.enabled) return Result.success()
        return when (val r = m.check()) {
            is CheckResult.Available -> {
                if (UpdateRules.shouldNotify(true, r.info, m.installedCode(), s.lastNotifiedCode)) {
                    m.notifyAvailable(r.info)
                    m.settings.update { it.copy(lastNotifiedCode = r.info.versionCode) }
                }
                Result.success()
            }
            CheckResult.UpToDate -> Result.success()
            CheckResult.Failed -> Result.retry()
        }
    }
}
