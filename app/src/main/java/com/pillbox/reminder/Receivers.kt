package com.pillbox.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pillbox.PillBoxApp
import com.pillbox.data.Mappers
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { pending.finish() }
    }
}

private fun Context.reminders() = (applicationContext as PillBoxApp).reminders

private fun Intent.at(): LocalDateTime? = getStringExtra(Reminders.EXTRA_AT)?.let { runCatching { Mappers.dt(it) }.getOrNull() }

/** A dose alarm, a repeat or a snooze ended. */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.reminders()
        val at = intent.at()
        async {
            when (intent.action) {
                "com.pillbox.action.DOSE_DUE" -> if (at != null) reminders.onDue(at) else reminders.rearm()
                "com.pillbox.action.DOSE_NAG" -> if (at != null) reminders.onRepeat(at, intent.getIntExtra(Reminders.EXTRA_COUNT, 1))
                "com.pillbox.action.DOSE_SNOOZE" -> if (at != null) reminders.onSnoozeEnded(at)
            }
        }
    }
}

/** Take all and Snooze buttons on the notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.reminders()
        val at = intent.at() ?: return
        async {
            when (intent.action) {
                Reminders.ACTION_TAKE_ALL -> reminders.takeAll(at)
                Reminders.ACTION_SNOOZE -> reminders.snooze(at)
            }
        }
    }
}

/** Alarms are lost on reboot, app update, time change and time zone change: arm them again. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.reminders()
        async { reminders.rearm() }
    }
}
