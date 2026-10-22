package com.pillbox.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pillbox.R
import com.pillbox.data.UpdateSettingsStore
import com.pillbox.domain.UpdateInfo
import com.pillbox.domain.UpdateRules
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Available(val info: UpdateInfo) : CheckResult
    data object Failed : CheckResult
}

sealed interface DownloadResult {
    data class Ready(val file: File) : DownloadResult
    data object Failed : DownloadResult
}

/**
 * Looks for a newer release on GitHub, tells the user once, downloads the file and hands it to Android's installer.
 * The only request sent is a plain GET of the public releases page: no account, no device id, no personal data.
 */
class UpdateManager(private val context: Context, val settings: UpdateSettingsStore) {

    fun installedCode(): Int =
        runCatching { PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt() }.getOrDefault(0)

    /** Starts the weekly check, or stops it when the user turned it off. A running job is left alone. */
    suspend fun reconcile() {
        val wm = WorkManager.getInstance(context)
        if (settings.current().enabled) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else wm.cancelUniqueWork(WORK_NAME)
    }

    suspend fun setEnabled(on: Boolean) { settings.update { it.copy(enabled = on) }; reconcile() }

    /** Asks GitHub for the latest release. Saves what it found. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val info = runCatching { fetchLatest() }.getOrNull() ?: return@withContext CheckResult.Failed
        val now = System.currentTimeMillis()
        val newer = UpdateRules.isNewer(info.versionCode, installedCode())
        settings.update { it.copy(lastCheckMillis = now, available = if (newer) info else null) }
        if (newer) CheckResult.Available(info) else CheckResult.UpToDate
    }

    private fun fetchLatest(): UpdateInfo? {
        val json = JSONObject(httpGet(LATEST_URL, "application/vnd.github+json"))
        val tag = json.optString("tag_name")
        val code = UpdateRules.versionCode(tag) ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        var apk: JSONObject? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val n = a.optString("name")
            if (n.endsWith(".apk.sha256")) sha = a.optString("browser_download_url")
            else if (n.matches(Regex("PillBox-.*\\.apk"))) apk = a
        }
        val url = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
        return UpdateInfo(tag.removePrefix("v"), code, UpdateRules.plainNotes(json.optString("body")), url, sha?.takeIf { it.startsWith("https://") }, apk?.optLong("size") ?: 0L)
    }

    private fun httpGet(url: String, accept: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "PillBox")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** Saves the file in the cache, reporting progress 0..1, and checks it against the published checksum. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val out = File(dir, "PillBox-${info.versionName}.apk")
            val expected = info.sha256Url?.let { UpdateRules.parseSha256(httpGet(it, "text/plain")) }
            val c = URL(info.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "PillBox")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                var done = 0L
                c.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n); digest.update(buf, 0, n); done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally { c.disconnect() }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && expected != actual) { out.delete(); error("checksum") }
            DownloadResult.Ready(out)
        }.getOrDefault(DownloadResult.Failed)
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android's page where the user allows this app to install apps, once. */
    fun allowInstallIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android shows its own confirmation. Nothing is installed without the user pressing Install there. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** One quiet notification per version. Not sent when notifications are off. */
    fun notifyAvailable(info: UpdateInfo) {
        val nm = NotificationManagerCompat.from(context)
        (context.applicationContext as? com.pillbox.PillBoxApp)?.recordNotice(
            com.pillbox.domain.Notice(
                "app:update:${info.versionName}", com.pillbox.domain.NoticeKind.APP, context.getString(R.string.upd_notif_title),
                context.getString(R.string.upd_notif_text, info.versionName), System.currentTimeMillis(), target = "updates",
            ),
        )
        if (!nm.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_update), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, com.pillbox.MainActivity::class.java).setAction(ACTION_OPEN_UPDATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.upd_notif_title)
        val text = context.getString(R.string.upd_notif_text, info.versionName)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pill).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    companion object {
        const val WORK_NAME = "pillbox_update_check"
        const val CHANNEL = "update"
        const val NOTIFICATION_ID = 7401
        const val ACTION_OPEN_UPDATE = "com.pillbox.OPEN_UPDATE"
        const val LATEST_URL = "https://api.github.com/repos/m-damer/pillbox/releases/latest"
    }
}
