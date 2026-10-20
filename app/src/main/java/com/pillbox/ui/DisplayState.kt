package com.pillbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.pillbox.domain.DisplaySettings
import java.time.DayOfWeek
import java.time.temporal.WeekFields

/** The settings screens can read these in any composable. Screenshots and previews get the defaults. */
val LocalDisplaySettings = compositionLocalOf { DisplaySettings() }

/**
 * The latest display settings for code that is not Compose (notifications, the PDF). The app keeps it up to date.
 * Compose screens read [LocalDisplaySettings] instead so they redraw when a setting changes.
 */
object DisplayState {
    @Volatile var current: DisplaySettings = DisplaySettings()
}

/** First day of the week: the Language and numbers choice, or the language's own. */
@Composable
fun firstDayOfWeek(): DayOfWeek {
    val settings = LocalDisplaySettings.current
    val locale = LocalConfiguration.current.locales[0]
    return settings.firstDay(WeekFields.of(locale).firstDayOfWeek)
}

/** The latest reminder settings for code outside Compose (notifications). The app keeps it up to date. */
object ReminderState {
    @Volatile var current: com.pillbox.domain.ReminderSettings = com.pillbox.domain.ReminderSettings()
}
