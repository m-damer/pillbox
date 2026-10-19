package com.pillbox.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.RowButton

/** "Let reminders ring": three permissions, each with an Allow button that opens the right system screen. */
@Composable
fun PermissionsScreen(
    state: PermissionState,
    onAllowNotifications: () -> Unit,
    onAllowExact: () -> Unit,
    onAllowBattery: () -> Unit,
    onContinue: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = Space.S16).semantics { heading() })
            Text(stringResource(R.string.perm_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                PermRow(Icons.Filled.Notifications, c.successContainer, c.success, R.string.perm_notif, R.string.perm_notif_sub, state.notifications, onAllowNotifications)
                PermRow(Icons.Filled.DateRange, c.noon, c.noonText, R.string.perm_exact, R.string.perm_exact_sub, state.exactAlarms, onAllowExact)
                PermRow(Icons.Filled.Settings, c.warningContainer, c.warning, R.string.perm_battery, R.string.perm_battery_sub, state.battery, onAllowBattery)
            }
            Row(
                Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.perm_later_info), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.continue_label), onContinue)
            LinkButton(stringResource(R.string.set_up_later), onLater)
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, bg: Color, fg: Color, title: Int, sub: Int, granted: Boolean, onAllow: () -> Unit) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight).padding(Space.S12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(Modifier.size(40.dp).background(bg, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.perm_allowed), style = MaterialTheme.typography.labelLarge, color = c.success)
            }
        } else {
            RowButton(stringResource(R.string.perm_allow), stringResource(R.string.perm_allow_desc, stringResource(title)), filled = true, onClick = onAllow)
        }
    }
}

/** Shown when notifications or exact alarms are still off after Continue. */
@Composable
fun RemindersOffScreen(onOpenSettings: () -> Unit, onContinueWithout: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Dimens.ScreenPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(80.dp).background(c.warningContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = c.warning, modifier = Modifier.size(40.dp))
            }
            Text(stringResource(R.string.off_title), style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S20).semantics { heading() })
            Text(stringResource(R.string.off_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S12))
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.off_open_settings), onOpenSettings)
            OutlineButton(stringResource(R.string.off_continue), onContinueWithout)
        }
    }
}

/** Runs the permission flow: asks, re-checks when the user comes back from system settings, and shows the "off" screen if needed. */
@Composable
fun PermissionsRoute(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ReminderPermissions.state(context)) }
    var showOff by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("pillbox", android.content.Context.MODE_PRIVATE) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { state = ReminderPermissions.state(context) }
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { state = ReminderPermissions.state(context) }
    androidx.compose.runtime.LaunchedEffect(state.remindersWork, showOff) { if (showOff && state.remindersWork) onDone() }

    fun open(intent: android.content.Intent) { runCatching { context.startActivity(intent) } }

    if (showOff) {
        RemindersOffScreen(
            onOpenSettings = {
                open(if (!state.notifications) ReminderPermissions.notificationSettings(context) else ReminderPermissions.exactAlarmSettings(context))
            },
            onContinueWithout = onDone,
        )
    } else {
        PermissionsScreen(
            state = state,
            onAllowNotifications = {
                if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications", false)) {
                    prefs.edit().putBoolean("asked_notifications", true).apply()
                    askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    open(ReminderPermissions.notificationSettings(context))
                }
            },
            onAllowExact = { open(ReminderPermissions.exactAlarmSettings(context)) },
            onAllowBattery = { open(ReminderPermissions.batterySettings()) },
            onContinue = { if (state.remindersWork) onDone() else showOff = true },
            onLater = onDone,
        )
    }
}
