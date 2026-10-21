package com.pillbox

import com.pillbox.ui.components.getStringIso
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pillbox.data.Mappers
import com.pillbox.reminder.PermissionsRoute
import com.pillbox.domain.BackupSettings
import com.pillbox.domain.RefillSettings
import com.pillbox.domain.ReminderSettings
import com.pillbox.reminder.ReminderPermissions
import com.pillbox.ui.backup.BackupScreen
import com.pillbox.ui.backup.BackupViewModel
import com.pillbox.domain.FeedbackMail
import com.pillbox.ui.about.AboutScreen
import com.pillbox.ui.about.CrashReportDialog
import com.pillbox.ui.about.FeedbackScreen
import com.pillbox.ui.about.PrivacyPolicyContent
import com.pillbox.ui.about.RateScreen
import com.pillbox.ui.about.TermsContent
import com.pillbox.ui.about.appInfo
import com.pillbox.ui.about.openEmail
import com.pillbox.ui.backup.DeleteDoneContent
import com.pillbox.ui.backup.RestoreScreen
import com.pillbox.reminder.Reminders
import com.pillbox.ui.people.PeopleBar
import com.pillbox.ui.AppLocale
import com.pillbox.ui.ReminderState
import com.pillbox.ui.settings.AccessibilityScreen
import com.pillbox.ui.settings.LanguageScreen
import com.pillbox.ui.settings.LateSettingScreen
import com.pillbox.ui.settings.HighContrastPreviewScreen
import com.pillbox.ui.settings.MealTimesScreen
import com.pillbox.ui.settings.NotRingingContent
import com.pillbox.ui.settings.RingFix
import com.pillbox.ui.settings.SimpleModeScreen
import com.pillbox.ui.settings.QuietHoursScreen
import com.pillbox.ui.settings.SettingsScreen
import com.pillbox.ui.settings.SoundScreen
import com.pillbox.ui.settings.SettingsTarget
import com.pillbox.ui.settings.SettingsViewModel
import com.pillbox.ui.settings.ThemeScreen
import com.pillbox.ui.people.PeopleScreen
import com.pillbox.ui.people.PeopleViewModel
import com.pillbox.ui.people.PersonEditScreen
import com.pillbox.ui.people.PersonEditViewModel
import com.pillbox.ui.people.PersonMode
import com.pillbox.ui.people.PersonResult
import com.pillbox.ui.addmed.AddMedicineScreen
import com.pillbox.ui.addmed.AddMedicineViewModel
import com.pillbox.ui.components.PillBoxBottomBar
import com.pillbox.ui.components.Tab
import com.pillbox.ui.medicines.ListToast
import com.pillbox.ui.medicines.MedicineDetailScreen
import com.pillbox.ui.medicines.MedicineDetailViewModel
import com.pillbox.ui.medicines.MedicinesScreen
import com.pillbox.ui.medicines.MedicinesViewModel
import com.pillbox.ui.DisplayState
import com.pillbox.ui.theme.PillBoxAppTheme
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

class MainActivity : PillBoxActivity() {
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
            val display by app.displaySettings.flow.collectAsStateWithLifecycle(DisplayState.current)
            val backup by app.backups.settings.flow.collectAsStateWithLifecycle(BackupSettings())
            PillBoxAppTheme(display) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val prefs = remember { context.getSharedPreferences("pillbox", Context.MODE_PRIVATE) }
                // Simple state-based navigation until there are more real destinations.
                var adding by rememberSaveable { mutableStateOf(false) }
                // A new Add medicine flow every time, so an old "was added" prompt never comes back.
                var addSession by rememberSaveable { mutableIntStateOf(0) }
                var showPermissions by rememberSaveable { mutableStateOf(false) }
                // First launch: language and text size, shown once.
                var welcomeDone by rememberSaveable { mutableStateOf(prefs.getBoolean("welcome_done", false)) }
                var welcomeLegal by rememberSaveable { mutableIntStateOf(0) }
                // Medicines tab: which medicine is open, which list tab, and a toast to show after Stop or Restart.
                var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
                // As-needed log: which medicine, whether to log a dose on opening, and a counter so each opening starts fresh.
                var prnId by rememberSaveable { mutableStateOf<Long?>(null) }
                var prnAuto by rememberSaveable { mutableStateOf(false) }
                var prnSession by rememberSaveable { mutableIntStateOf(0) }
                // Kept here so Settings opens where it was left after a sub-screen closes.
                val settingsScroll = androidx.compose.foundation.rememberScrollState()
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
                // People: which person screen is open (0 none, 1 add, 2 edit, 3 my profile) and a toast for the People tab.
                var personMode by rememberSaveable { mutableIntStateOf(0) }
                var personId by rememberSaveable { mutableStateOf(0L) }
                var personSession by rememberSaveable { mutableIntStateOf(0) }
                var peopleToastTitle by rememberSaveable { mutableStateOf<String?>(null) }
                var peopleToastBody by rememberSaveable { mutableStateOf<String?>(null) }
                // Settings: which settings screen is open (0 none, 1 accessibility, 2 language, 3 dark theme) and a toast for the Settings tab.
                var settingsScreen by rememberSaveable { mutableIntStateOf(0) }
                // Privacy and about: a counter that shows the "Thank you" toast after the email app was opened, and a crash report waiting from the last run.
                var aboutThanks by rememberSaveable { mutableIntStateOf(0) }
                var deleteFromPrivacy by rememberSaveable { mutableStateOf(false) }
                var crashReport by remember { mutableStateOf(CrashReporter.pending(context)) }
                crashReport?.let { report ->
                    CrashReportDialog(
                        onSend = {
                            if (!openEmail(context, FeedbackMail.crash(report, appInfo(context)))) { /* no email app: keep the report for next time */ } else { CrashReporter.clear(context); crashReport = null }
                        },
                        onSkip = { CrashReporter.clear(context); crashReport = null },
                    )
                }
                // Only a flag is kept: the words are looked up when the toast shows, so they follow a language switch.
                var settingsSaved by rememberSaveable { mutableStateOf(false) }
                val peopleList by repo.observePeople().collectAsStateWithLifecycle(emptyList())
                val activePersonId by repo.activePersonId.collectAsStateWithLifecycle()
                val peopleBar = PeopleBar(peopleList, activePersonId, onSelect = { repo.selectPerson(it) })
                // A refill notification asked for a medicine.
                val wanted = openMedicine.value
                LaunchedEffect(wanted) {
                    if (wanted != null) {
                        showRefill = false; showHistory = false; showReport = false; adding = false; editItem = null
                        detailId = wanted; openMedicine.value = null
                    }
                }
                LaunchedEffect(display.onlyTodayAndSettings) {
                    if (display.onlyTodayAndSettings && (tab == Tab.MEDS || tab == Tab.PEOPLE)) tab = Tab.TODAY
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

                if (!welcomeDone) {
                    val wvm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.displaySettings, app.refillSettings, repo, app.reminderSettings) } })
                    val back = { welcomeLegal = 0 }
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        when (welcomeLegal) {
                            16 -> { BackHandler(onBack = back); TermsContent(onBack = back, onDone = back) }
                            15 -> { BackHandler(onBack = back); PrivacyPolicyContent(onBack = back, onDelete = {}, onClose = back, showDelete = false) }
                            else -> com.pillbox.ui.welcome.WelcomeContent(
                                language = AppLocale.active(context), textSize = display.textSize,
                                onLanguage = { code ->
                                    if (code != AppLocale.active(context)) {
                                        wvm.saveLanguage(com.pillbox.ui.settings.LanguageChoice(code, code == "ar", display.timeFormat, java.time.temporal.WeekFields.of(java.util.Locale.forLanguageTag(code)).firstDayOfWeek)) {
                                            AppLocale.set(context, code)
                                            if (android.os.Build.VERSION.SDK_INT < 33 && AppLocale.active(context) != code) this@MainActivity.recreate()
                                        }
                                    }
                                },
                                onTextSize = wvm::setTextSize,
                                onTerms = { welcomeLegal = 16 }, onPrivacy = { welcomeLegal = 15 },
                                onContinue = { prefs.edit().putBoolean("welcome_done", true).apply(); welcomeDone = true },
                            )
                        }
                    }
                } else if (personMode != 0) {
                    val mode = when (personMode) { 1 -> PersonMode.ADD; 2 -> PersonMode.EDIT; else -> PersonMode.PROFILE }
                    val vm: PersonEditViewModel = viewModel(
                        key = "person-$personSession",
                        factory = viewModelFactory { initializer { PersonEditViewModel(repo, meName, mode, personId, onChanged = { reminders.rearm() }) } },
                    )
                    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                        PersonEditScreen(vm, onClose = { result ->
                            when (result) {
                                is PersonResult.Added -> {
                                    peopleToastTitle = context.getStringIso(R.string.toast_person_added_title, result.name)
                                    peopleToastBody = context.getString(R.string.toast_person_added_body)
                                }
                                is PersonResult.Removed -> {
                                    peopleToastTitle = context.getStringIso(R.string.toast_person_removed_title, result.name)
                                    peopleToastBody = context.getString(R.string.toast_person_removed_body)
                                }
                                PersonResult.Saved -> {
                                    peopleToastTitle = context.getString(R.string.toast_person_saved_title)
                                    peopleToastBody = context.getString(R.string.toast_person_saved_body)
                                }
                                null -> {}
                            }
                            personMode = 0
                            tab = Tab.PEOPLE
                        })
                    }
                } else if (settingsScreen != 0) {
                    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.displaySettings, app.refillSettings, repo, app.reminderSettings) } })
                    val close = { settingsScreen = when (settingsScreen) { 9, 10 -> 1; 12 -> 11; 15, 16, 17, 18 -> 14; else -> 0 } }
                    BackHandler { close() }
                    val savedToast = { settingsSaved = true }
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        when (settingsScreen) {
                            14 -> AboutScreen(
                                aboutThanks, onBack = close,
                                onPrivacy = { settingsScreen = 15 }, onTerms = { settingsScreen = 16 }, onFeedback = { settingsScreen = 17 }, onRate = { settingsScreen = 18 },
                            )
                            15 -> PrivacyPolicyContent(onBack = close, onDelete = { deleteFromPrivacy = true; settingsScreen = 11 }, onClose = close)
                            16 -> TermsContent(onBack = close, onDone = close)
                            19 -> com.pillbox.ui.about.DeveloperContent(onBack = close)
                            17 -> FeedbackScreen(onBack = close, onOpened = { aboutThanks++; settingsScreen = 14 })
                            18 -> RateScreen(onBack = close, onOpened = { aboutThanks++; settingsScreen = 14 }, onWriteFeedback = { settingsScreen = 17 })
                            1 -> AccessibilityScreen(vm, onBack = close, onOpenSimple = { settingsScreen = 9 }, onOpenPreview = { settingsScreen = 10 })
                            9 -> SimpleModeScreen(vm, onBack = close)
                            10 -> HighContrastPreviewScreen(vm, onBack = close)
                            8 -> {
                                var ringing by remember { mutableStateOf(ReminderPermissions.state(context)) }
                                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                                    ringing = ReminderPermissions.state(context); permissions = ringing
                                }
                                fun open(intent: android.content.Intent) { runCatching { context.startActivity(intent) } }
                                NotRingingContent(
                                    ringing, onBack = close,
                                    onFix = { fix ->
                                        when (fix) {
                                            RingFix.NOTIFICATIONS -> open(ReminderPermissions.notificationSettings(context))
                                            RingFix.EXACT -> open(ReminderPermissions.exactAlarmSettings(context))
                                            RingFix.BATTERY -> open(ReminderPermissions.batterySettings())
                                            RingFix.DND -> open(ReminderPermissions.doNotDisturbSettings(context))
                                        }
                                    },
                                    onTest = { if (ringing.notifications) reminders.sendTest() else open(ReminderPermissions.notificationSettings(context)) },
                                )
                            }
                            11, 12, 13 -> {
                                val backupVm: BackupViewModel = viewModel(factory = viewModelFactory {
                                    initializer {
                                        BackupViewModel(
                                            app.backups,
                                            onDataChanged = { reminders.rearm() },
                                            resetOtherSettings = {
                                                app.reminderSettings.update { ReminderSettings() }; app.refillSettings.save(RefillSettings()); app.widgetSettings.clear(); com.pillbox.widget.PillBoxWidget.refresh(applicationContext)
                                                androidx.core.app.NotificationManagerCompat.from(applicationContext).cancelAll()
                                            },
                                        )
                                    }
                                })
                                when (settingsScreen) {
                                    11 -> BackupScreen(
                                        backupVm, onBack = close, openDelete = deleteFromPrivacy.also { androidx.compose.runtime.SideEffect { deleteFromPrivacy = false } },
                                        onRestore = { backupVm.startRestore(); settingsScreen = 12 },
                                        onDeleted = { settingsScreen = 13 },
                                    )
                                    12 -> RestoreScreen(
                                        backupVm, onBack = close,
                                        onSetUpReminders = { settingsScreen = 0; tab = Tab.TODAY; showPermissions = true },
                                        onGoToToday = { settingsScreen = 0; tab = Tab.TODAY; detailId = null },
                                    )
                                    else -> DeleteDoneContent(
                                        onStartAgain = { settingsScreen = 0; tab = Tab.TODAY; detailId = null },
                                        onRestore = { backupVm.startRestore(); settingsScreen = 12 },
                                    )
                                }
                            }
                            3 -> ThemeScreen(vm, onBack = close)
                            4 -> SoundScreen(vm, onBack = close)
                            5 -> QuietHoursScreen(vm, onBack = close, onSaved = { savedToast(); settingsScreen = 0 })
                            7 -> LateSettingScreen(vm, onBack = close, onSaved = { savedToast(); settingsScreen = 0 })
                            6 -> MealTimesScreen(vm, onBack = close, onSaved = { savedToast(); settingsScreen = 0 })
                            else -> LanguageScreen(
                                vm, activeLanguage = AppLocale.active(context), onBack = close,
                                onSave = { choice ->
                                    vm.saveLanguage(choice) {
                                        settingsSaved = true
                                        settingsScreen = 0
                                        AppLocale.set(context, choice.language)
                                        // Android 13 and newer recreate the screens by themselves; older phones need a nudge.
                                        if (android.os.Build.VERSION.SDK_INT < 33 && AppLocale.active(context) != choice.language) this@MainActivity.recreate()
                                    }
                                },
                            )
                        }
                    }
                } else if (showReport) {
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
                        factory = viewModelFactory { initializer { AddMedicineViewModel(repo, meName, onChanged = { reminders.rearm() }, meals = ReminderState.current.meals) } },
                    )
                    AddMedicineScreen(vm, onEditMeals = { settingsScreen = 6 }, onClose = {
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
                            detailToastBody = context.getStringIso(R.string.toast_edit_body, item.medicine.name)
                        }
                        editItem = null
                    })
                } else if (prnId != null) {
                    val pid = prnId!!
                    val vm: com.pillbox.ui.prn.PrnViewModel = viewModel(
                        key = "prn-$pid-$prnSession",
                        factory = viewModelFactory { initializer { com.pillbox.ui.prn.PrnViewModel(repo, pid, prnAuto, onChanged = { reminders.rearm() }) } },
                    )
                    Box(Modifier.fillMaxSize().statusBarsPadding()) { com.pillbox.ui.prn.PrnScreen(vm, onBack = { prnId = null }) }
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
                            onTakeNow = { prnAuto = true; prnId = detailId; prnSession++ },
                            onOpenLog = { prnAuto = false; prnId = detailId; prnSession++ },
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
                                    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo, meName, onChanged = { reminders.rearm() }, remindDays = remindDays, saveLateSetting = { v -> app.reminderSettings.update { it.copy(late = v) } }) } })
                                    TodayScreen(
                                        vm, onAddMedicine = { adding = true },
                                        remindersOff = !permissions.remindersWork, onFixReminders = { showPermissions = true },
                                        onGoToMeds = { tab = Tab.MEDS }, people = peopleBar,
                                        onTakeAsNeeded = { prnAuto = true; prnId = it; prnSession++ },
                                    )
                                }
                                Tab.MEDS -> {
                                    val vm: MedicinesViewModel = viewModel(factory = viewModelFactory { initializer { MedicinesViewModel(repo, meName, remindDays) } })
                                    MedicinesScreen(
                                        vm, showFinished = showFinishedTab, onShowFinished = { showFinishedTab = it },
                                        onOpen = { detailId = it }, onAddMedicine = { adding = true },
                                        onRefillAlert = { refillSession++; showRefill = true }, people = peopleBar,
                                        toast = listToastTitle?.let { ListToast(it, listToastBody) },
                                        onToastShown = { listToastTitle = null; listToastBody = null },
                                    )
                                }
                                Tab.PEOPLE -> {
                                    val vm: PeopleViewModel = viewModel(factory = viewModelFactory { initializer { PeopleViewModel(repo, meName, onChanged = { reminders.rearm() }) } })
                                    PeopleScreen(
                                        vm, onOpenPerson = { tab = Tab.TODAY },
                                        onEditPerson = { id ->
                                            val isMe = peopleList.firstOrNull { it.id == id }?.isMe == true
                                            personId = id; personMode = if (isMe) 3 else 2; personSession++
                                        },
                                        onAddPerson = { personId = 0L; personMode = 1; personSession++ },
                                        toast = peopleToastTitle?.let { ListToast(it, peopleToastBody) },
                                        onToastShown = { peopleToastTitle = null; peopleToastBody = null },
                                    )
                                }
                                Tab.SETTINGS -> {
                                    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app.displaySettings, app.refillSettings, repo, app.reminderSettings) } })
                                    SettingsScreen(
                                        vm,
                                        onOpen = { target ->
                                            when (target) {
                                                SettingsTarget.REFILL -> { refillSession++; showRefill = true }
                                                SettingsTarget.SOUND -> settingsScreen = 4
                                SettingsTarget.QUIET -> settingsScreen = 5
                                SettingsTarget.MEALS -> settingsScreen = 6
                                SettingsTarget.LATE -> settingsScreen = 7
                                SettingsTarget.RINGING -> settingsScreen = 8
                                SettingsTarget.BACKUP -> settingsScreen = 11
                                SettingsTarget.PRIVACY -> settingsScreen = 14
                                SettingsTarget.ACCESSIBILITY -> settingsScreen = 1
                                                SettingsTarget.LANGUAGE -> settingsScreen = 2
                                                SettingsTarget.THEME -> settingsScreen = 3
                                                SettingsTarget.REPORT -> showReport = true
                                                SettingsTarget.DEVELOPER -> settingsScreen = 19
                                            }
                                        },
                                        toast = if (settingsSaved) ListToast(stringResource(R.string.toast_setting_saved_title), null) else null,
                                        onToastShown = { settingsSaved = false },
                                        ringingIssues = permissions.issues,
                                        backup = backup,
                                        scroll = settingsScroll,
                                    )
                                }
                            }
                        }
                        PillBoxBottomBar(tab, onSelect = { tab = it }, onAdd = { adding = true }, onlyTodayAndSettings = display.onlyTodayAndSettings)
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
