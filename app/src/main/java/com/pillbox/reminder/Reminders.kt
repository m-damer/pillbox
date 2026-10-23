package com.pillbox.reminder

import com.pillbox.ui.components.getStringIso
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import com.pillbox.ui.ReminderState
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
import com.pillbox.domain.ExpiryInfo
import com.pillbox.domain.ExpiryRules
import com.pillbox.domain.DoseRow
import com.pillbox.domain.DoseStatus
import com.pillbox.domain.Pause
import com.pillbox.domain.LateChoice
import com.pillbox.domain.LateRules
import com.pillbox.domain.Medicine
import com.pillbox.domain.Notice
import com.pillbox.domain.NoticeKind
import com.pillbox.domain.RefillAlert
import com.pillbox.domain.RefillPlanner
import com.pillbox.domain.ReminderPlanner
import com.pillbox.domain.Schedule
import com.pillbox.domain.TodayBuilder
import com.pillbox.ui.Block
import com.pillbox.ui.Formatter
import com.pillbox.ui.blockOf
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Everything that makes a dose ring: one exact alarm for the next dose, a notification with three actions
 * (Take all, Snooze, Open) plus a full-screen reminder, a repeat if nobody answers, and re-arming.
 * The pure decisions live in [ReminderPlanner]; this class only talks to Android.
 */
class Reminders(
    private val context: Context,
    private val repo: PillBoxRepository,
    private val refillSettings: com.pillbox.data.RefillSettingsStore,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private enum class Kind(val code: Int, val action: String) {
        DUE(1, "com.pillbox.action.DOSE_DUE"),
        NAG(2, "com.pillbox.action.DOSE_NAG"),
        SNOOZE(3, "com.pillbox.action.DOSE_SNOOZE"),
        REFILL(4, "com.pillbox.action.REFILL_DUE"),
        FOLLOW(5, "com.pillbox.action.DOSE_FOLLOW"),
    }

    // ---------------------------------------------------------------- channels

    /**
     * Android fixes a channel's sound and vibration when it is created, so each tone and vibration choice has its own
     * channel; the one in use is created and the others are removed. The quiet channel has no sound, for quiet hours.
     */
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val s = ReminderState.current
        val doses = doseChannelId(s)
        val sound = Uri.parse("android.resource://" + context.packageName + "/" + toneResource(s.tone))
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(
            NotificationChannel(doses, context.getString(R.string.channel_doses), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_doses_desc)
                enableVibration(s.vibration)
                setSound(sound, attrs)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOSES_QUIET, context.getString(R.string.channel_doses_quiet), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_doses_quiet_desc)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REFILLS, context.getString(R.string.channel_refills), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_refills_desc)
            },
        )
        val keep = setOf(doses, CHANNEL_DOSES_QUIET, CHANNEL_REFILLS)
        nm.notificationChannels.filter { it.id.startsWith(CHANNEL_DOSES) && it.id !in keep }.forEach { nm.deleteNotificationChannel(it.id) }
    }

    // ---------------------------------------------------------------- data

    private suspend fun items(): List<Triple<Medicine, Schedule, List<Pause>>> =
        repo.allActiveRinging().mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }

    /** Names of the people other than me, for "For Mom" on the reminder screen. */
    suspend fun owners(): Map<Long, String> = repo.otherPeopleNames()

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
        rearmRefill(now)
        // The home screen widget shows the next dose, so it is redrawn whenever the plan changes.
        com.pillbox.widget.PillBoxWidget.refresh(context)
    }

    // ---------------------------------------------------------------- refill alerts

    /** One alarm a day at the time chosen on the Refill alert screen. It does not need to be exact: a few minutes late is fine. */
    suspend fun rearmRefill(now: LocalDateTime = LocalDateTime.now()) {
        val at = RefillPlanner.nextFire(refillSettings.current(), now)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(Kind.REFILL, null, 0))
    }

    /** The daily refill alarm fired: one quiet notification per medicine that is low or out, then arm tomorrow's. */
    suspend fun onRefillDue() {
        val now = LocalDateTime.now()
        // Quiet hours can mute refill alerts. The alert for today is skipped; tomorrow's comes as usual.
        if (ReminderState.current.quiet.silencesRefillAt(now.toLocalTime())) { rearmRefill(now.plusMinutes(1)); return }
        val items = repo.allActiveWithSchedules().map { Triple(it.medicine, it.schedule, it.pauses) }
        val alerts = RefillPlanner.due(items, refillSettings.current(), now, repo.myPersonId())
        val shown = alerts.map { notificationIdForRefill(it.medicine.id) }.toSet()
        // A medicine that is no longer low (stock added, paused) loses its old alert.
        items.map { notificationIdForRefill(it.first.id) }.filter { it !in shown }.forEach { notificationManager().cancel(it) }
        alerts.forEach { showRefill(it, now.toLocalDate()) }
        runCatching { ExpiryRules.noticesFor(repo.medicinesWithExpiry(), now.toLocalDate()).forEach { (m, info) -> showExpiry(m, info) } }
        // One minute on, so an alarm that arrives a moment early does not ring twice.
        rearmRefill(now.plusMinutes(1))
    }

    private fun notificationIdForExpiry(medicineId: Long) = EXPIRY_ID_BASE + (medicineId % 1_000_000L).toInt()

    /** One quiet notice, 30 days before, 7 days before and on the last day. It never says the medicine is safe. */
    @SuppressLint("MissingPermission")
    private fun showExpiry(m: Medicine, info: ExpiryInfo) {
        val nm = notificationManager()
        val fmt = Formatter.from(context)
        val res = context.resources
        val ym = m.expiry ?: return
        val month = fmt.digits(ym.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + ym.year)
        val left = info.daysLeft ?: 0
        val name = m.name + (com.pillbox.ui.localizedStrength(m.strength, res)?.let { " $it" } ?: "")
        val title = context.getStringIso(R.string.expiry_notif_title, name)
        val text = if (left == 0) context.getStringIso(R.string.expiry_today, month)
        else context.getStringIso(R.string.expiry_soon, res.getQuantityString(R.plurals.n_days, left, fmt.number(left)), month)
        val stage = if (left <= 0) 0 else if (left <= 7) 7 else 30
        record(Notice("expiry:${m.id}:$ym:$stage", NoticeKind.EXPIRY, title, text, System.currentTimeMillis(), target = "medicine:${m.id}", personId = m.personId))
        if (!nm.areNotificationsEnabled()) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_MEDICINE, m.id)
            .setData(Uri.parse("pillbox://expiry/open/${m.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(context, 32, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_REFILLS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + context.getString(R.string.expiry_soon_note)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true).setContentIntent(openPending)
            .build()
        runCatching { nm.notify(notificationIdForExpiry(m.id), n) }
    }

    fun dismissRefill(medicineId: Long) = notificationManager().cancel(notificationIdForRefill(medicineId))

    private fun notificationIdForRefill(medicineId: Long) = REFILL_ID_BASE + (medicineId % 1_000_000L).toInt()

    @SuppressLint("MissingPermission")
    private fun showRefill(a: RefillAlert, today: java.time.LocalDate) {
        val nm = notificationManager()
        val fmt = Formatter.from(context)
        val res = context.resources
        val m = a.medicine
        val name = m.name + (com.pillbox.ui.localizedStrength(m.strength, res)?.let { " $it" } ?: "")
        val id = notificationIdForRefill(m.id)
        val title: String
        val text: String
        val big: String
        if (a.out) {
            title = context.getStringIso(R.string.refill_notif_out_title, name)
            text = context.getString(R.string.refill_notif_out_text)
            big = text
        } else {
            val amount = fmt.amount(m.stock) + " " + res.getQuantityString(com.pillbox.ui.unitPlural(m.unit), com.pillbox.ui.quantityFor(m.stock))
            title = context.getStringIso(R.string.refill_notif_title, name)
            text = context.getString(R.string.refill_notif_left, amount)
            val days = a.daysLeft ?: 0
            val about = res.getQuantityString(R.plurals.n_days, days, fmt.number(days))
            big = text + "\n" + context.getString(R.string.refill_notif_about, about, a.runsOutOn?.let { com.pillbox.ui.shortDate(it, fmt) } ?: "")
        }
        record(Notice("stock:${m.id}:$today", NoticeKind.STOCK, title, text, System.currentTimeMillis(), target = "medicine:${m.id}", personId = m.personId))
        if (!nm.areNotificationsEnabled()) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_MEDICINE, m.id)
            .setData(Uri.parse("pillbox://refill/open/${m.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(context, 30, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val later = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_REFILL_TOMORROW)
            .putExtra(EXTRA_MEDICINE_ID, m.id).setData(Uri.parse("pillbox://refill/tomorrow/${m.id}"))
        val laterPending = PendingIntent.getBroadcast(context, 31, later, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_REFILLS)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setSubText(context.getString(R.string.channel_refills))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            // "I bought it" opens the medicine so the new boxes can be added with Add stock.
            .addAction(0, context.getString(R.string.refill_notif_bought), openPending)
            .addAction(0, context.getString(R.string.refill_notif_tomorrow), laterPending)
            .build()
        runCatching { nm.notify(id, n) }
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
        val settings = ReminderState.current
        // One more reminder later, in case this one was missed too. The first ring and a snooze arm it; it only fires while doses are still waiting.
        if (count == 0) ReminderPlanner.followUpAt(at, settings.followUpMinutes, LocalDateTime.now())?.let { schedule(Kind.FOLLOW, it, at, 0) }
        if (count < settings.maxRepeats) {
            schedule(Kind.NAG, LocalDateTime.now().plusMinutes(settings.repeatMinutes.toLong()), at, count + 1)
        }
    }

    /** The follow-up alarm fired: if doses are still waiting, remind once more, saying they are still waiting. */
    suspend fun onFollowUp(at: LocalDateTime) {
        val rows = pendingRows(at)
        if (rows.isEmpty()) { dismiss(at); return }
        show(rows, at, followUp = true)
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
        cancel(Kind.FOLLOW, at)
        cancel(Kind.SNOOZE, at)
        notificationManager().cancel(notificationId(at))
    }

    /** Stops only the repeat (the user opened the app). */
    fun stopRepeat(at: LocalDateTime) = cancel(Kind.NAG, at)

    suspend fun take(rows: List<DoseRow>) {
        val now = LocalDateTime.now()
        val settings = ReminderState.current
        var moved = false
        rows.forEach { r ->
            val late = LateRules.lateMinutes(r.planned, now, settings.lateThreshold)
            repo.takeDose(r.medicine, DoseEvent(planned = r.planned, taken = now, status = DoseStatus.TAKEN, lateMinutes = late))
            // No sheet here (notification and full-screen reminder): "Move next doses" follows the setting, everything else keeps the times.
            if (LateRules.effectiveChoice(r.schedule.frequency, settings.late, late, asked = null) == LateChoice.MOVE) {
                val entries = LateRules.moveEntries(r.schedule, r.planned, now)
                if (entries.isNotEmpty()) { repo.moveDoses(r.medicine.id, entries); moved = true }
            }
        }
        if (moved) rearm()
    }

    suspend fun skip(rows: List<DoseRow>) {
        rows.forEach { r ->
            repo.recordDose(DoseEvent(medicineId = r.medicine.id, planned = r.planned, status = DoseStatus.SKIPPED))
        }
    }

    // ---------------------------------------------------------------- notification

    private fun notificationManager() = NotificationManagerCompat.from(context)

    /**
     * "Take all" for my own doses; "Take all for Mom" when every dose is Mom's, so the button never takes someone else's
     * doses by surprise. Doses for several people say "for everyone".
     */
    private fun takeAllLabel(people: List<Long>, owners: Map<Long, String>): String = when {
        people.none { it in owners } -> context.getString(R.string.take_all)
        people.size == 1 -> context.getStringIso(R.string.take_all_for, owners.getValue(people.first()))
        else -> context.getString(R.string.take_all_everyone)
    }

    private fun record(n: Notice) { (context.applicationContext as? com.pillbox.PillBoxApp)?.recordNotice(n) }

    private fun notificationId(at: LocalDateTime) = (at.atZone(ZoneId.systemDefault()).toEpochSecond() / 60).toInt()

    private fun actionPending(action: String, at: LocalDateTime): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).setAction(action)
            .putExtra(EXTRA_AT, Mappers.dt(at)).setData(Uri.parse("pillbox://dose/$action/${Mappers.dt(at)}"))
        return PendingIntent.getBroadcast(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun activityPending(cls: Class<*>, at: LocalDateTime, code: Int, personId: Long? = null): PendingIntent {
        val intent = Intent(context, cls).putExtra(EXTRA_AT, Mappers.dt(at))
            .apply { if (personId != null) putExtra(EXTRA_PERSON_ID, personId) }
            .setData(Uri.parse("pillbox://open/${cls.simpleName}/${Mappers.dt(at)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private suspend fun show(rows: List<DoseRow>, at: LocalDateTime, followUp: Boolean = false) {
        val owners = repo.otherPeopleNames()
        val nm = notificationManager()
        // Opening the notification shows the person these doses are for: the one person, or me when it is mixed.
        val people = rows.map { it.medicine.personId }.distinct()
        val openFor = if (people.size == 1) people.first() else (repo.myPersonId()?.takeIf { it in people } ?: people.firstOrNull())
        val block = context.getString(blockNameRes(blockOf(at.toLocalTime())))
        val title = context.getString(if (followUp) R.string.notif_title_waiting else R.string.notif_title, block)
        // A dose for someone I care for says whose it is, so the wrong person never gets it.
        val text = rows.joinToString(" · ") { r ->
            val line = r.medicine.name + (com.pillbox.ui.localizedStrength(r.medicine.strength, context.resources)?.let { " $it" } ?: "")
            owners[r.medicine.personId]?.let { context.getStringIso(R.string.notif_dose_for, it, line) } ?: line
        }
        // Saved for the notifications screen even when Android notifications are off, so a missed alert can still be seen there.
        record(Notice("dose:${notificationId(at)}" + (if (followUp) ":waiting" else ""), NoticeKind.DOSE, title, text, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), target = openFor?.let { "today:$it" } ?: "today", personId = openFor))
        if (!nm.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, channelFor(LocalDateTime.now().toLocalTime()))
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setWhen(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setContentIntent(activityPending(MainActivity::class.java, at, 20, openFor))
            // At most three buttons: Take all, Snooze, Open. Per-medicine Take and Skip are on the full-screen reminder.
            .addAction(0, takeAllLabel(people, owners), actionPending(ACTION_TAKE_ALL, at))
            .addAction(0, context.getString(R.string.snooze_15), actionPending(ACTION_SNOOZE, at))
            .addAction(0, context.getString(R.string.open), activityPending(MainActivity::class.java, at, 21, openFor))
        // The notification above is the fallback when the full-screen screen is not allowed (Android 14+ may need approval).
        builder.setFullScreenIntent(activityPending(ReminderActivity::class.java, at, 22), true)
        runCatching { nm.notify(notificationId(at), builder.build()) }
        val settings = ReminderState.current
        if (settings.readAloud && !settings.quiet.silencesDoseAt(LocalDateTime.now().toLocalTime())) {
            ReminderSpeaker.speak(context, context.getString(R.string.tts_time_to_take, rows.joinToString(", ") { com.pillbox.ui.components.isolate(it.medicine.name) }))
        }
    }

    /** "Send a test reminder": a notification on the same channel as a real one, so the user hears what a dose sounds like. */
    @SuppressLint("MissingPermission")
    fun sendTest() {
        val nm = notificationManager()
        if (!nm.areNotificationsEnabled()) return
        val title = context.getString(R.string.test_notif_title)
        val text = context.getString(R.string.test_notif_text)
        val n = NotificationCompat.Builder(context, channelFor(LocalDateTime.now().toLocalTime()))
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(TEST_ID, n) }
        val s = ReminderState.current
        if (s.readAloud && !s.quiet.silencesDoseAt(LocalDateTime.now().toLocalTime())) ReminderSpeaker.speak(context, context.getString(R.string.tts_time_to_take, context.getString(R.string.test_tts_name)))
    }

    /** The channel a dose reminder uses now: silent during quiet hours only when the user turned "Medicine reminders still ring" off. */
    private fun channelFor(now: java.time.LocalTime): String {
        val s = ReminderState.current
        return if (s.quiet.silencesDoseAt(now)) CHANNEL_DOSES_QUIET else doseChannelId(s)
    }

    companion object {
        const val CHANNEL_DOSES = "doses"
        const val CHANNEL_DOSES_QUIET = "doses_quiet"

        fun doseChannelId(s: com.pillbox.domain.ReminderSettings) = CHANNEL_DOSES + "_" + s.tone.name.lowercase() + if (s.vibration) "_v" else "_n"

        fun toneResource(tone: com.pillbox.domain.Tone) = when (tone) {
            com.pillbox.domain.Tone.GENTLE_BELL -> R.raw.tone_gentle_bell
            com.pillbox.domain.Tone.MORNING_CHIME -> R.raw.tone_morning_chime
            com.pillbox.domain.Tone.LOUD_ALARM -> R.raw.tone_loud_alarm
            com.pillbox.domain.Tone.SOFT_HARP -> R.raw.tone_soft_harp
            com.pillbox.domain.Tone.CALM_BELLS -> R.raw.tone_calm_bells
            com.pillbox.domain.Tone.WARM_GLOW -> R.raw.tone_warm_glow
            com.pillbox.domain.Tone.WATER_DROPS -> R.raw.tone_water_drops
            com.pillbox.domain.Tone.SOFT_MARIMBA -> R.raw.tone_soft_marimba
        }
        const val CHANNEL_REFILLS = "refills"
        private const val TEST_ID = 999_999_999
        const val EXTRA_AT = "at"
        const val EXTRA_COUNT = "count"
        const val ACTION_TAKE_ALL = "com.pillbox.action.TAKE_ALL"
        const val ACTION_SNOOZE = "com.pillbox.action.SNOOZE"
        const val ACTION_REFILL_TOMORROW = "com.pillbox.action.REFILL_TOMORROW"
        const val EXTRA_MEDICINE_ID = "medicine_id"
        const val EXTRA_OPEN_MEDICINE = "open_medicine"
        const val EXTRA_PERSON_ID = "open_person"
        /** Refill notification ids start here so they never clash with dose notification ids (minutes since 1970). */
        private const val REFILL_ID_BASE = 1_000_000_000
        private const val EXPIRY_ID_BASE = 1_100_000_000

        fun blockNameRes(b: Block) = when (b) {
            Block.MORNING -> R.string.block_morning
            Block.NOON -> R.string.block_afternoon
            Block.EVENING -> R.string.block_evening
            Block.NIGHT -> R.string.block_night
        }
    }
}
