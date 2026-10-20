package com.pillbox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.pillbox.ui.PlaceholderScreen
import com.pillbox.ui.addmed.AddMedicineScreen
import com.pillbox.ui.addmed.AddMedicineViewModel
import com.pillbox.ui.components.PillBoxBottomBar
import com.pillbox.ui.components.Tab
import com.pillbox.ui.medicines.ListToast
import com.pillbox.ui.medicines.MedicineDetailScreen
import com.pillbox.ui.medicines.MedicineDetailViewModel
import com.pillbox.ui.medicines.MedicinesScreen
import com.pillbox.ui.medicines.MedicinesViewModel
import com.pillbox.ui.theme.PillBoxTheme
import com.pillbox.ui.today.TodayScreen
import com.pillbox.ui.today.TodayViewModel
import com.pillbox.ui.history.HistoryScreen
import com.pillbox.ui.history.HistoryViewModel
import com.pillbox.ui.refill.RefillAlertScreen
import com.pillbox.ui.report.ReportScreen
import com.pillbox.ui.report.ReportViewModel
import com.pillbox.ui.refill.RefillAlertViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A medicine to open, asked for by a refill notification. Taken once the screen shows it. */
    private val openMedicine = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PillBoxApp
        val repo = app.repository
        val reminders = app.reminders
        val meName = getString(R.string.person_me)
        stopRepeatIfOpenedFromNotification()
        readOpenMedicine()
        val remindDays = app.refillSettings.flow.map { it.daysBefore }.distinctUntilChanged()
        setContent {
            PillBoxTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val prefs = remember { context.getSharedPreferences("pillbox", Context.MODE_PRIVATE) }
                // Simple state-based navigation until there are more real destinations.
                var adding by rememberSaveable { mutableStateOf(false) }
                // A new Add medicine flow every time, so an old "was added" prompt never comes back.
                var addSession by rememberSaveable { mutableIntStateOf(0) }
                var showPermissions by rememberSaveable { mutableStateOf(false) }
                // Medicines tab: which medicine is open, which list tab, and a toast to show after Stop or Restart.
                var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
                var showFinishedTab by rememberSaveable { mutableStateOf(false) }
                var listToastTitle by rememberSaveable { mutableStateOf<String?>(null) }
                var listToastBody by rememberSaveable { mutableStateOf<String?>(null) }
                // Edit medicine: the medicine being edited (lost if the process dies, which just returns to the detail screen).
                var editItem by remember { mutableStateOf<com.pillbox.data.MedicineWithSchedule?>(null) }
                var editSession by rememberSaveable { mutableIntStateOf(0) }
                var detailToastTitle by rememberSaveable { mutableStateOf<String?>(null) }
                var detailToastBody by rememberSaveable { mutableStateOf<String?>(null) }
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                var showRefill by rememberSaveable { mutableStateOf(false) }
                var showHistory by rememberSaveable { mutableStateOf(false) }
                var showReport by rememberSaveable { mutableStateOf(false) }
                var refillSession by rememberSaveable { mutableIntStateOf(0) }
                // A refill notification asked for a medicine.
                val wanted = openMedicine.value
                LaunchedEffect(wanted) {
                    if (wanted != null) {
                        showRefill = false; showHistory = false; showReport = false; adding = false; editItem = null
                        detailId = wanted; openMedicine.value = null
                    }
                }
                var permissions by remember { mutableStateOf(ReminderPermissions.state(context)) }

                // Coming back from system settings: re-check permissions and arm the next reminder again.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    permissions = ReminderPermissions.state(context)
                    scope.launch {
                        repo.finishEndedCourses(java.time.LocalDate.now())
                        reminders.rearm()
                    }
                }

                if (showReport) {
                    val vm: ReportViewModel = viewModel(factory = viewModelFactory { initializer { ReportViewModel(applicationContext, repo, meName) } })
                    // Registered first, so the preview inside ReportScreen can take Back before this does.
                    BackHandler { showReport = false }
                    Box(Modifier.fillMaxSize().statusBarsPadding()) { ReportScreen(vm, onBack = { showReport = false }) }
                } else if (showHistory) {
                    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repo, meName) } })
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        HistoryScreen(vm, onBack = { showHistory = false }, onGoToToday = { showHistory = false; detailId = null; tab = Tab.TODAY },
                            onShareReport = { showReport = true })
                    }
                    BackHandler { showHistory = false }
                } else if (showRefill) {
                    val vm: RefillAlertViewModel = viewModel(
                        key = "refill-$refillSession",
                        factory = viewModelFactory { initializer { RefillAlertViewModel(repo, app.refillSettings, onChanged = { reminders.rearmRefill() }) } },
                    )
                    Box(Modifier.fillMaxSize().statusBarsPadding()) { RefillAlertScreen(vm, onClose = { showRefill = false; refillSession++ }) }
                } else if (showPermissions) {
                    PermissionsRoute(onDone = { showPermissions = false; permissions = ReminderPermissions.state(context) })
                } else if (adding) {
                    val vm: AddMedicineViewModel = viewModel(
                        key = "add-$addSession",
                        factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName, onChanged = { reminders.rearm() }) } },
                    )
                    AddMedicineScreen(vm, onClose = {
                        adding = false
                        addSession++
                        tab = Tab.TODAY
                        // First medicine saved: offer to let reminders ring, once.
                        val state = ReminderPermissions.state(context)
                        if (vm.state.value.savedAny && !state.remindersWork && !prefs.getBoolean("permissions_prompted", false)) {
                            prefs.edit().putBoolean("permissions_prompted", true).apply()
                            showPermissions = true
                        }
                    })
                } else if (editItem != null) {
                    val item = editItem!!
                    val vm: AddMedicineViewModel = viewModel(
                        key = "edit-$editSession",
                        factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName, onChanged = { reminders.rearm() }, editing = item) } },
                    )
                    AddMedicineScreen(vm, onClose = {
                        if (vm.state.value.saved) {
                            detailToastTitle = context.getString(R.string.toast_edit_title)
                            detailToastBody = context.getString(R.string.toast_edit_body, item.medicine.name)
                        }
                        editItem = null
                    })
                } else if (detailId != null) {
                    val id = detailId!!
                    val vm: MedicineDetailViewModel = viewModel(
                        key = "detail-$id",
                        factory = viewModelFactory { initializer { MedicineDetailViewModel(repo, id, onChanged = { reminders.rearm() }, remindDays = remindDays) } },
                    )
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        MedicineDetailScreen(
                            vm, onBack = { detailId = null },
                            onEdit = { vm.state.value.item?.let { editItem = it; editSession++ } },
                            onSeeHistory = { showHistory = true },
                            toast = detailToastTitle?.let { ListToast(it, detailToastBody) },
                            onToastShown = { detailToastTitle = null; detailToastBody = null },
                            onDone = { toast, finishedTab ->
                                listToastTitle = toast.title; listToastBody = toast.body
                                showFinishedTab = finishedTab
                                tab = Tab.MEDS
                                detailId = null
                            },
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).statusBarsPadding()) {
                            when (tab) {
                                Tab.TODAY -> {
                                    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo, meName, onChanged = { reminders.rearm() }, remindDays = remindDays) } })
                                    TodayScreen(
                                        vm, onAddMedicine = { adding = true },
                                        remindersOff = !permissions.remindersWork, onFixReminders = { showPermissions = true },
                                        onGoToMeds = { tab = Tab.MEDS },
                                    )
                                }
                                Tab.MEDS -> {
                                    val vm: MedicinesViewModel = viewModel(factory = viewModelFactory { initializer { MedicinesViewModel(repo, meName, remindDays) } })
                                    MedicinesScreen(
                                        vm, showFinished = showFinishedTab, onShowFinished = { showFinishedTab = it },
                                        onOpen = { detailId = it }, onAddMedicine = { adding = true },
                                        onRefillAlert = { refillSession++; showRefill = true },
                                        toast = listToastTitle?.let { ListToast(it, listToastBody) },
                                        onToastShown = { listToastTitle = null; listToastBody = null },
                                    )
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
        readOpenMedicine()
    }

    private fun readOpenMedicine() {
        val id = intent?.getLongExtra(Reminders.EXTRA_OPEN_MEDICINE, -1L) ?: -1L
        if (id > 0) openMedicine.value = id
        intent?.removeExtra(Reminders.EXTRA_OPEN_MEDICINE)
    }

    /** "Open" on a reminder means the user has seen it, so the 5-minute repeat stops. */
    private fun stopRepeatIfOpenedFromNotification() {
        val at = intent?.getStringExtra(Reminders.EXTRA_AT)?.let { runCatching { Mappers.dt(it) }.getOrNull() } ?: return
        (application as PillBoxApp).reminders.stopRepeat(at)
    }
}
