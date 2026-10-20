package com.pillbox.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pillbox.MainActivity
import com.pillbox.R
import com.pillbox.data.Mappers
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.DoseEvent
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.LateRules
import com.pillbox.domain.Medicine
import com.pillbox.domain.ReminderPlanner
import com.pillbox.domain.Schedule
import com.pillbox.domain.TodayBuilder
import com.pillbox.ui.Block
import com.pillbox.ui.blockOf
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Everything that makes a dose ring: one exact alarm for the next dose, a notification with three actions
 * (Take all, Snooze, Open) plus a full-screen reminder, a repeat if nobody answers, and re-arming.
 * The pure decisions live in [ReminderPlanner]; this class only talks to Android.
 */
class Reminders(private val context: Context, private val repo: PillBoxRepository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private enum class Kind(val code: Int, val action: String) {
        DUE(1, "com.pillbox.action.DOSE_DUE"),
        NAG(2, "com.pillbox.action.DOSE_NAG"),
        SNOOZE(3, "com.pillbox.action.DOSE_SNOOZE"),
    }

    // ---------------------------------------------------------------- channels

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOSES, context.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_doses_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REFILLS, context.getString(R.string.channel_refills), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_refills_desc)
            },
        )
    }

    // ---------------------------------------------------------------- data

    private suspend fun items(): List<Triple<Medicine, Schedule, List<Pause>>> =
        repo.allActiveWithSchedules().mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }

    /** Doses waiting at [at], from the database right now. */
    suspend fun pendingRows(at: LocalDateTime): List<DoseRow> {
        val day = at.toLocalDate().atStartOfDay()
        return ReminderPlanner.dueRows(items(), repo.allDoseEventsFor(day, day.plusDays(1)), at)
    }

    // ---------------------------------------------------------------- arming

    /** Arms the alarm for the next dose. Call after any change and on boot, app update, time and time zone change. */
    suspend fun rearm(now: LocalDateTime = LocalDateTime.now()) {
        val next = ReminderPlanner.nextDue(items(), now)
        if (next == null) cancel(Kind.DUE, null) else schedule(Kind.DUE, next, next, 0)
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun pending(kind: Kind, at: LocalDateTime?, count: Int): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).setAction(kind.action).putExtra(EXTRA_COUNT, count)
        if (at != null) intent.putExtra(EXTRA_AT, Mappers.dt(at))
        // The one DUE alarm has a fixed identity so arming again replaces it; repeats and snoozes are one per dose time.
        if (at != null && kind != Kind.DUE) intent.setData(Uri.parse("pillbox://dose/${kind.name}/${Mappers.dt(at)}"))
        return PendingIntent.getBroadcast(context, kind.code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(kind: Kind, trigger: LocalDateTime, at: LocalDateTime, count: Int) {
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(kind, at, count)
        if (canScheduleExact()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun cancel(kind: Kind, at: LocalDateTime?) {
        alarms.cancel(pending(kind, at, 0))
    }

    // ---------------------------------------------------------------- events

    /** The alarm for [at] fired. */
    suspend fun onDue(at: LocalDateTime) {
        notifyIfPending(at, count = 0)
        val now = LocalDateTime.now()
        rearm(if (now.isAfter(at)) now else at)
    }

    /** Nobody answered, or the snooze ended: ring again. */
    suspend fun onRepeat(at: LocalDateTime, count: Int) = notifyIfPending(at, count)

    private suspend fun notifyIfPending(at: LocalDateTime, count: Int) {
        val rows = pendingRows(at)
        if (rows.isEmpty()) { dismiss(at); return }
        show(rows, at)
        if (count < ReminderPlanner.MAX_NAGS) {
            schedule(Kind.NAG, LocalDateTime.now().plusMinutes(ReminderPlanner.NAG_MINUTES), at, count + 1)
        }
    }

    /** Take all from the notification. */
    suspend fun takeAll(at: LocalDateTime) {
        take(pendingRows(at))
        dismiss(at)
    }

    suspend fun snooze(at: LocalDateTime) {
        cancel(Kind.NAG, at)
        notificationManager().cancel(notificationId(at))
        schedule(Kind.SNOOZE, LocalDateTime.now().plusMinutes(ReminderPlanner.SNOOZE_MINUTES), at, 0)
    }

    /** The snooze alarm fired. */
    suspend fun onSnoozeEnded(at: LocalDateTime) = notifyIfPending(at, count = 0)

    /** Stops the repeat and removes the notification for [at]. */
    fun dismiss(at: LocalDateTime) {
        cancel(Kind.NAG, at)
        cancel(Kind.SNOOZE, at)
        notificationManager().cancel(notificationId(at))
    }

    /** Stops only the repeat (the user opened the app). */
    fun stopRepeat(at: LocalDateTime) = cancel(Kind.NAG, at)

    suspend fun take(rows: List<DoseRow>) {
        val now = LocalDateTime.now()
        rows.forEach { r ->
            repo.takeDose(
                r.medicine,
                DoseEvent(
                    planned = r.planned, taken = now, status = DoseStatus.TAKEN,
                    lateMinutes = LateRules.lateMinutes(r.planned, now, TodayBuilder.DEFAULT_LATE_THRESHOLD),
                ),
            )
        }
    }

    suspend fun skip(rows: List<DoseRow>) {
        rows.forEach { r ->
            repo.recordDose(DoseEvent(medicineId = r.medicine.id, planned = r.planned, status = DoseStatus.SKIPPED))
        }
    }

    // ---------------------------------------------------------------- notification

    private fun notificationManager() = NotificationManagerCompat.from(context)

    private fun notificationId(at: LocalDateTime) = (at.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt()

    private fun actionPending(action: String, at: LocalDateTime): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).setAction(action)
            .putExtra(EXTRA_AT, Mappers.dt(at)).setData(Uri.parse("pillbox://dose/$action/${Mappers.dt(at)}"))
        return PendingIntent.getBroadcast(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun activityPending(cls: Class<*>, at: LocalDateTime, code: Int): PendingIntent {
        val intent = Intent(context, cls).putExtra(EXTRA_AT, Mappers.dt(at))
            .setData(Uri.parse("pillbox://open/${cls.simpleName}/${Mappers.dt(at)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun show(rows: List<DoseRow>, at: LocalDateTime) {
        val nm = notificationManager()
        if (!nm.areNotificationsEnabled()) return
        val title = context.getString(R.string.notif_title, context.getString(blockNameRes(blockOf(at.toLocalTime()))))
        val text = rows.joinToString(" · ") { r -> r.medicine.name + (com.pillbox.ui.localizedStrength(r.medicine.strength, context.resources)?.let { " $it" } ?: "") }
        val builder = NotificationCompat.Builder(context, CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setWhen(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setContentIntent(activityPending(MainActivity::class.java, at, 20))
            // At most three buttons: Take all, Snooze, Open. Per-medicine Take and Skip are on the full-screen reminder.
            .addAction(0, context.getString(R.string.take_all), actionPending(ACTION_TAKE_ALL, at))
            .addAction(0, context.getString(R.string.snooze_15), actionPending(ACTION_SNOOZE, at))
            .addAction(0, context.getString(R.string.open), activityPending(MainActivity::class.java, at, 21))
        // The notification above is the fallback when the full-screen screen is not allowed (Android 14+ may need approval).
        builder.setFullScreenIntent(activityPending(ReminderActivity::class.java, at, 22), true)
        runCatching { nm.notify(notificationId(at), builder.build()) }
    }

    companion object {
        const val CHANNEL_DOSES = "doses"
        const val CHANNEL_REFILLS = "refills"
        const val EXTRA_AT = "at"
        const val EXTRA_COUNT = "count"
        const val ACTION_TAKE_ALL = "com.pillbox.action.TAKE_ALL"
        const val ACTION_SNOOZE = "com.pillbox.action.SNOOZE"

        fun blockNameRes(b: Block) = when (b) {
            Block.MORNING -> R.string.block_morning
            Block.NOON -> R.string.block_afternoon
            Block.EVENING -> R.string.block_evening
            Block.NIGHT -> R.string.block_night
        }
    }
}
