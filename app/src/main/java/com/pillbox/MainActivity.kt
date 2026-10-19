package com.pillbox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pillbox.data.Mappers
import com.pillbox.reminder.PermissionsRoute
import com.pillbox.reminder.ReminderPermissions
import com.pillbox.reminder.Reminders
import com.pillbox.ui.MedicinesPreviewScreen
import com.pillbox.ui.MedicinesViewModel
import com.pillbox.ui.PlaceholderScreen
import com.pillbox.ui.addmed.AddMedicineScreen
import com.pillbox.ui.addmed.AddMedicineViewModel
import com.pillbox.ui.components.PillBoxBottomBar
import com.pillbox.ui.components.Tab
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.today.TodayScreen
import com.pillbox.ui.today.TodayViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PillBoxApp
        val repo = app.repository
        val reminders = app.reminders
        val meName = getString(R.string.person_me)
        stopRepeatIfOpenedFromNotification()
        setContent {
            PillBoxTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val prefs = remember { context.getSharedPreferences("pillbox", Context.MODE_PRIVATE) }
                // Simple state-based navigation until there are more real destinations.
                var adding by rememberSaveable { mutableStateOf(false) }
                var showPermissions by rememberSaveable { mutableStateOf(false) }
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                var permissions by remember { mutableStateOf(ReminderPermissions.state(context)) }

                // Coming back from system settings: re-check permissions and arm the next reminder again.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissions = ReminderPermissions.state(context)
                    scope.launch { reminders.rearm() }
                }

                if (showPermissions) {
                    PermissionsRoute(onDone = { showPermissions = false; permissions = ReminderPermissions.state(context) })
                } else if (adding) {
                    val vm: AddMedicineViewModel = viewModel(
                        factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName, onChanged = { reminders.rearm() }) } },
                    )
                    AddMedicineScreen(vm, onClose = {
                        adding = false
                        tab = Tab.TODAY
                        // First medicine saved: offer to let reminders ring, once.
                        val state = ReminderPermissions.state(context)
                        if (vm.state.value.savedAny && !state.remindersWork && !prefs.getBoolean("permissions_prompted", false)) {
                            prefs.edit().putBoolean("permissions_prompted", true).apply()
                            showPermissions = true
                        }
                    })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).statusBarsPadding()) {
                            when (tab) {
                                Tab.TODAY -> {
                                    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo, meName) } })
                                    TodayScreen(
                                        vm, onAddMedicine = { adding = true },
                                        remindersOff = !permissions.remindersWork, onFixReminders = { showPermissions = true },
                                    )
                                }
                                Tab.MEDS -> {
                                    val vm: MedicinesViewModel = viewModel(factory = viewModelFactory { initializer { MedicinesViewModel(repo, meName) } })
                                    val medicines by vm.medicines.collectAsStateWithLifecycle()
                                    MedicinesPreviewScreen(medicines)
                                }
                                Tab.PEOPLE -> PlaceholderScreen(stringResource(R.string.nav_people))
                                Tab.SETTINGS -> PlaceholderScreen(stringResource(R.string.nav_settings))
                            }
                        }
                        PillBoxBottomBar(tab, onSelect = { tab = it }, onAdd = { adding = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stopRepeatIfOpenedFromNotification()
    }

    /** "Open" on a reminder means the user has seen it, so the 5-minute repeat stops. */
    private fun stopRepeatIfOpenedFromNotification() {
        val at = intent?.getStringExtra(Reminders.EXTRA_AT)?.let { runCatching { Mappers.dt(it) }.getOrNull() } ?: return
        (application as PillBoxApp).reminders.stopRepeat(at)
    }
}
