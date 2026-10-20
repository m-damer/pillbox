package com.pillbox.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean,
    /** Do Not Disturb is on, so reminders may be silenced unless the phone lets them through. */
    val doNotDisturb: Boolean = false,
) {
    /** How many things on "Reminders not ringing?" still need fixing. */
    val issues: Int get() = listOf(!notifications, !exactAlarms, !battery, doNotDisturb).count { it }

    /** Notifications and exact alarms are what a reminder needs; battery only makes it more reliable. */
    val remindersWork: Boolean get() = notifications && exactAlarms
    val all: Boolean get() = notifications && exactAlarms && battery
}

object ReminderPermissions {
    fun state(context: Context): PermissionState {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return PermissionState(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
            battery = power.isIgnoringBatteryOptimizations(context.packageName),
            doNotDisturb = context.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            appDetails(context)
        }

    /** The system list of battery-optimised apps. We never ask for the exemption in code; Google Play restricts that. */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** Where the user lets certain apps through Do Not Disturb. Falls back to the policy access list on phones without it. */
    fun doNotDisturbSettings(context: Context): Intent {
        val priority = Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS")
        return if (priority.resolveActivity(context.packageManager) != null) priority else Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
