package com.pillbox.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class PermissionState(val notifications: Boolean, val exactAlarms: Boolean, val battery: Boolean) {
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

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
