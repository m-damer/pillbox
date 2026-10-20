package com.pillbox.reminder

import android.os.Bundle
import com.pillbox.PillBoxActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.pillbox.PillBoxApp
import com.pillbox.data.Mappers
import com.pillbox.domain.DoseRow
import com.pillbox.ui.rememberFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.ui.DisplayState
import com.pillbox.ui.theme.PillBoxAppTheme
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** The full-screen reminder: per-medicine Take and Skip. Shown over the lock screen when Android allows it. */
class ReminderActivity : PillBoxActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val at = intent.getStringExtra(Reminders.EXTRA_AT)?.let { runCatching { Mappers.dt(it) }.getOrNull() }
        if (at == null) { finish(); return }
        val app = application as PillBoxApp
        val reminders = app.reminders
        setContent {
            val display by app.displaySettings.flow.collectAsStateWithLifecycle(DisplayState.current)
            PillBoxAppTheme(display) { ReminderRoute(reminders, at, onFinish = { finish() }) }
        }
    }
}

@Composable
private fun ReminderRoute(reminders: Reminders, at: LocalDateTime, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val fmt = rememberFormatter()
    var rows by remember { mutableStateOf<List<DoseRow>?>(null) }
    var owners by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    suspend fun reload() {
        owners = reminders.owners()
        val pending = reminders.pendingRows(at)
        if (pending.isEmpty()) { reminders.dismiss(at); onFinish() } else rows = pending
    }
    LaunchedEffect(Unit) { reload() }

    val current = rows ?: return
    ReminderScreen(
        at = at, rows = current, fmt = fmt,
        onTake = { r -> scope.launch { reminders.take(listOf(r)); reload() } },
        onSkip = { r -> scope.launch { reminders.skip(listOf(r)); reload() } },
        onTakeAll = { scope.launch { reminders.take(current); reload() } },
        onSkipAll = { scope.launch { reminders.skip(current); reload() } },
        onSnooze = { scope.launch { reminders.snooze(at); onFinish() } },
        owners = owners,
    )
}
