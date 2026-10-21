package com.pillbox.ui.addmed

import com.pillbox.ui.components.stringResourceIso
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.AddMedicineForm
import com.pillbox.domain.DaysChoice
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.FormError
import com.pillbox.domain.Frequency
import com.pillbox.domain.FreqChoice
import com.pillbox.domain.LengthChoice
import com.pillbox.domain.ScheduleEngine
import com.pillbox.domain.StartChoice
import com.pillbox.domain.StrengthUnit
import com.pillbox.domain.WithFood
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.ErrorText
import com.pillbox.ui.components.FieldLabel
import com.pillbox.ui.components.HelperText
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.StepHeader
import com.pillbox.ui.components.Stepper
import com.pillbox.ui.Block
import com.pillbox.ui.blockColors
import com.pillbox.ui.blockName
import com.pillbox.ui.blockOf
import com.pillbox.ui.quantityFor
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.unitPlural
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** The 3-step Add medicine flow, with its prompts. [onClose] leaves the flow. */
@Composable
fun AddMedicineScreen(vm: AddMedicineViewModel, onClose: () -> Unit, modifier: Modifier = Modifier, onEditMeals: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val back = { if (vm.back()) onClose() }
    BackHandler(onBack = back)
    androidx.compose.runtime.LaunchedEffect(state.saved) { if (state.saved) onClose() }

    AddMedicineContent(
        state = state, fmt = fmt, today = LocalDate.now(),
        edit = vm::edit, onBack = back, onNext = vm::next, onSave = vm::save, modifier = modifier, onEditMeals = onEditMeals,
    )

    when (val d = state.dialog) {
        is AddMedDialog.Duplicate -> MessageDialog(
            DialogKind.Warning, stringResourceIso(R.string.dup_title, d.name), stringResource(R.string.dup_body), vm::dismissDialog,
        ) {
            PrimaryButton(stringResourceIso(R.string.dup_open, d.name), { vm.dismissDialog(); onClose() })
            OutlineButton(stringResource(R.string.dup_add_anyway), vm::addAnyway)
            LinkButton(stringResource(R.string.cancel), vm::dismissDialog)
        }
        AddMedDialog.Unsaved -> MessageDialog(
            DialogKind.Warning, stringResource(R.string.unsaved_title), stringResource(R.string.unsaved_body), vm::dismissDialog,
        ) {
            PrimaryButton(stringResource(R.string.keep_editing), vm::dismissDialog)
            DangerButton(stringResource(R.string.leave), onClose)
        }
        AddMedDialog.SaveFailed -> MessageDialog(
            DialogKind.Danger, stringResource(R.string.save_failed_title), stringResource(R.string.save_failed_body), vm::dismissDialog,
        ) {
            PrimaryButton(stringResource(R.string.try_again), { vm.dismissDialog(); vm.save() })
            OutlineButton(stringResource(R.string.cancel), vm::dismissDialog)
        }
        is AddMedDialog.Added -> {
            val first = d.firstDose
            val today = LocalDate.now()
            val body = when {
                d.asNeeded -> stringResource(R.string.added_body_prn)
                first == null -> stringResource(R.string.added_body_none)
                first.toLocalDate() == today -> stringResourceIso(R.string.added_body_today, fmt.time(first.toLocalTime()))
                first.toLocalDate() == today.plusDays(1) -> stringResourceIso(R.string.added_body_tomorrow, fmt.time(first.toLocalTime()))
                else -> stringResourceIso(R.string.added_body_date, fmt.digits(shortDate(first.toLocalDate())), fmt.time(first.toLocalTime()))
            }
            MessageDialog(DialogKind.Success, stringResourceIso(R.string.added_title, d.name), body, onDismiss = {}) {
                PrimaryButton(stringResource(R.string.go_today), onClose)
                OutlineButton(stringResource(R.string.add_another), vm::startOver)
            }
        }
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicineContent(
    state: AddMedUiState,
    fmt: Formatter,
    today: LocalDate,
    edit: ((AddMedicineForm) -> AddMedicineForm) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onEditMeals: () -> Unit = {},
) {
    if (state.step == 4) { ReviewPlanContent(state, fmt, today, edit, onBack, onSave, onEditMeals, modifier); return }
    val form = state.form
    var timeTarget by remember { mutableStateOf<Int?>(null) } // -1 = first dose, 0.. = a dose chip
    var showDatePicker by remember { mutableStateOf(false) }
    val subtitles = listOf(R.string.step1_sub, R.string.step2_sub, R.string.step3_sub)

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(start = Dimens.ScreenPaddingH, end = Dimens.ScreenPaddingH, top = Dimens.ScreenPaddingTop)) {
            StepHeader(
                title = stringResource(if (state.isEdit) R.string.edit_title else R.string.add_medicine),
                subtitle = if (state.isEdit) stringResourceIso(R.string.edit_med_step, state.editingName.orEmpty(), fmt.number(state.step))
                else stringResourceIso(R.string.add_med_step, fmt.number(state.step), stringResource(subtitles[state.step - 1])),
                step = state.step, steps = 3, backLabel = stringResource(R.string.back), onBack = onBack,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPaddingH),
        ) {
            when (state.step) {
                1 -> Step1(form, state.showErrors, fmt, edit)
                2 -> Step2(form, today, fmt, edit) { timeTarget = it }
                else -> Step3(form, state.showErrors, today, fmt, edit) { showDatePicker = true }
            }
            Box(Modifier.size(Space.S16))
        }
        Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12)) {
            if (state.step < 3) PrimaryButton(stringResource(R.string.next), onNext)
            else PrimaryButton(stringResource(if (state.isEdit) R.string.save_changes else R.string.save_medicine), onSave, enabled = !state.saving)
        }
    }

    timeTarget?.let { target ->
        val initial = if (target < 0) form.firstTime() else form.times().getOrElse(target) { form.firstDose }
        PBTimePickerDialog(initial, fmt.is24Hour, onDismiss = { timeTarget = null }) { picked ->
            edit { f -> if (target < 0) f.withFirstDose(picked) else f.withDoseTime(target, picked) }
            timeTarget = null
        }
    }

    if (showDatePicker) {
        val zone = ZoneOffset.UTC
        val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (form.pickedDate ?: today).atStartOfDay(zone).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= startMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        edit { it.copy(start = StartChoice.PICK, pickedDate = d) }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
}

// ---------------------------------------------------------------- step 1

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PillBox.colors.text, unfocusedTextColor = PillBox.colors.text, errorTextColor = PillBox.colors.text,
    focusedContainerColor = PillBox.colors.card, unfocusedContainerColor = PillBox.colors.card, errorContainerColor = PillBox.colors.card,
    focusedBorderColor = PillBox.colors.primary, unfocusedBorderColor = PillBox.colors.border, errorBorderColor = PillBox.colors.danger,
    focusedPlaceholderColor = PillBox.colors.placeholder, unfocusedPlaceholderColor = PillBox.colors.placeholder,
    errorPlaceholderColor = PillBox.colors.placeholder,
    cursorColor = PillBox.colors.primary, errorCursorColor = PillBox.colors.danger,
)

@Composable
private fun Step1(form: AddMedicineForm, showErrors: Boolean, fmt: Formatter, edit: ((AddMedicineForm) -> AddMedicineForm) -> Unit) {
    val errors = form.step1Errors()
    val nameError = showErrors && FormError.NAME_MISSING in errors
    FieldLabel(stringResource(R.string.medicine_name))
    OutlinedTextField(
        value = form.name,
        onValueChange = { v -> edit { it.copy(name = v) } },
        placeholder = { Text(stringResource(R.string.name_hint), style = MaterialTheme.typography.bodyLarge) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        isError = nameError,
        shape = MaterialTheme.shapes.large,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton),
    )
    if (nameError) ErrorText(stringResource(R.string.name_error))

    FieldLabel(stringResource(R.string.type_label))
    val types = listOf(
        DoseUnit.TABLET to R.string.type_tablet, DoseUnit.CAPSULE to R.string.type_capsule, DoseUnit.ML to R.string.type_syrup,
        DoseUnit.INJECTION to R.string.type_injection, DoseUnit.DROPS to R.string.type_drops, DoseUnit.OTHER to R.string.type_other,
    )
    ChipGroup {
        types.forEach { (t, label) -> PBChip(stringResource(label), form.type == t, { edit { it.withType(t) } }) }
    }

    FieldLabel(stringResource(R.string.strength_label))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
        OutlinedTextField(
            value = form.strength,
            onValueChange = { v -> edit { it.copy(strength = v.take(10)) } },
            placeholder = { Text("500", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).heightIn(min = Dimens.StepperButton),
        )
        val units = StrengthUnit.values().toList()
        Segmented(
            options = listOf(stringResource(R.string.unit_mg), stringResource(R.string.unit_g), stringResource(R.string.unit_ml)),
            selectedIndex = units.indexOf(form.strengthUnit),
            onSelect = { i -> edit { it.copy(strengthUnit = units[i]) } },
            modifier = Modifier.weight(1.3f),
        )
    }

    val doseLabel = stringResource(R.string.dose_label)
    FieldLabel(doseLabel)
    val step = AddMedicineForm.doseStep(form.type)
    Stepper(
        valueText = fmt.amount(form.dose),
        unit = pluralStringResource(unitPlural(form.type), quantityFor(form.dose)),
        label = doseLabel,
        onMinus = { edit { it.copy(dose = (it.dose - step).coerceAtLeast(step)) } },
        onPlus = { edit { it.copy(dose = (it.dose + step).coerceAtMost(99.0)) } },
        minusEnabled = form.dose > step,
    )
    if (form.type == DoseUnit.TABLET) HelperText(stringResource(R.string.dose_half_hint))

    val boxLabel = stringResource(if (form.isEdit) R.string.box_label_now else R.string.box_label)
    FieldLabel(boxLabel)
    val boxError = showErrors && FormError.BOX_MISSING in errors
    Stepper(
        valueText = form.boxCount?.let { fmt.number(it) } ?: "",
        unit = pluralStringResource(unitPlural(form.type), form.boxCount ?: 0),
        label = boxLabel,
        onMinus = { edit { it.withBox(((it.boxCount ?: 0) - 1).coerceAtLeast(0).takeIf { n -> n > 0 }) } },
        onPlus = { edit { it.withBox(((it.boxCount ?: 0) + 1).coerceAtMost(99999)) } },
        onValueText = { text -> edit { it.withBox(fmt.parseInt(text)) } },
        isError = boxError,
    )
    if (boxError) ErrorText(stringResource(R.string.box_error))

    if (!form.isEdit && (form.boxCount ?: 0) > 1) {
        val leftLabel = stringResource(R.string.box_left_label)
        FieldLabel(leftLabel)
        Stepper(
            valueText = fmt.number(form.left()),
            unit = pluralStringResource(unitPlural(form.type), form.left()),
            label = leftLabel,
            onMinus = { edit { it.withLeft(it.left() - 1) } },
            onPlus = { edit { it.withLeft(it.left() + 1) } },
            onValueText = { text -> edit { it.withLeft(fmt.parseInt(text)) } },
            minusEnabled = form.left() > 1,
        )
        HelperText(stringResource(R.string.box_left_hint))
    }
}

// ---------------------------------------------------------------- step 2

@Composable
private fun Step2(
    form: AddMedicineForm,
    today: LocalDate,
    fmt: Formatter,
    edit: ((AddMedicineForm) -> AddMedicineForm) -> Unit,
    openTimePicker: (Int) -> Unit,
) {
    FieldLabel(stringResource(R.string.how_often))
    val freqs = listOf(
        FreqChoice.ONCE to R.string.freq_once, FreqChoice.TWICE to R.string.freq_twice, FreqChoice.THREE to R.string.freq_three,
        FreqChoice.HOURS to R.string.freq_hours, FreqChoice.AS_NEEDED to R.string.freq_prn,
    )
    ChipGroup {
        freqs.forEach { (f, label) -> PBChip(stringResource(label), form.freq == f, { edit { it.withFreq(f) } }) }
    }

    when (form.freq) {
        FreqChoice.ONCE, FreqChoice.TWICE, FreqChoice.THREE -> {
            val label = stringResource(if (form.freq == FreqChoice.ONCE) R.string.one_dose else R.string.first_dose)
            FieldLabel(label)
            TimeCard(label, fmt.time(form.firstTime())) { openTimePicker(-1) }
            if (form.freq == FreqChoice.ONCE) {
                HelperText(stringResource(R.string.once_hint))
            } else {
                FieldLabel(stringResource(R.string.doses_auto))
                val times = form.times()
                val bigText = LocalDensity.current.fontScale > 1.3f
                if (bigText) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                        times.forEachIndexed { i, t -> DoseChip(t, fmt, Modifier.fillMaxWidth()) { openTimePicker(i) } }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                        times.forEachIndexed { i, t -> DoseChip(t, fmt, Modifier.weight(1f)) { openTimePicker(i) } }
                    }
                }
                HelperText(stringResource(if (form.freq == FreqChoice.TWICE) R.string.twice_hint else R.string.three_hint))
            }
        }

        FreqChoice.HOURS -> {
            val hoursLabel = stringResource(R.string.hours_label)
            FieldLabel(hoursLabel)
            Stepper(
                valueText = fmt.number(form.hours),
                unit = pluralStringResource(R.plurals.unit_hours, form.hours),
                label = hoursLabel,
                onMinus = { edit { it.copy(hours = (it.hours - 1).coerceAtLeast(1)) } },
                onPlus = { edit { it.copy(hours = (it.hours + 1).coerceAtMost(24)) } },
                minusEnabled = form.hours > 1, plusEnabled = form.hours < 24,
            )
            HelperText(stringResource(R.string.hours_hint))
            val first = stringResource(R.string.first_dose)
            FieldLabel(first)
            TimeCard(first, fmt.time(form.firstDose)) { openTimePicker(-1) }
            FieldLabel(stringResource(R.string.doses_auto))
            val f = form.schedule(today).frequency as Frequency.EveryNHours
            val doses = ScheduleEngine.cycle(form.schedule(today), f, today).map { it.toLocalTime() }
            HoursList(doses, form.firstDose, fmt)
            HelperText(
                stringResource(
                    R.string.hours_summary, fmt.time(form.firstDose), pluralStringResource(R.plurals.n_hours, form.hours, fmt.number(form.hours)),
                    pluralStringResource(R.plurals.n_doses, doses.size, fmt.number(doses.size)),
                ),
            )
        }

        FreqChoice.AS_NEEDED -> {
            val maxLabel = stringResource(R.string.prn_max)
            FieldLabel(maxLabel)
            Stepper(
                valueText = fmt.number(form.maxPerDay), unit = pluralStringResource(R.plurals.unit_dose, form.maxPerDay), label = maxLabel,
                onMinus = { edit { it.copy(maxPerDay = (it.maxPerDay - 1).coerceAtLeast(1)) } },
                onPlus = { edit { it.copy(maxPerDay = (it.maxPerDay + 1).coerceAtMost(24)) } },
                minusEnabled = form.maxPerDay > 1, plusEnabled = form.maxPerDay < 24,
            )
            val gapLabel = stringResource(R.string.prn_gap)
            FieldLabel(gapLabel)
            Stepper(
                valueText = fmt.number(form.minGapHours), unit = pluralStringResource(R.plurals.unit_hours, form.minGapHours), label = gapLabel,
                onMinus = { edit { it.copy(minGapHours = (it.minGapHours - 1).coerceAtLeast(1)) } },
                onPlus = { edit { it.copy(minGapHours = (it.minGapHours + 1).coerceAtMost(24)) } },
                minusEnabled = form.minGapHours > 1, plusEnabled = form.minGapHours < 24,
            )
            Box(Modifier.size(Space.S12))
            InfoCard(stringResource(R.string.prn_info), Icons.Filled.Info)
        }
    }

    FieldLabel(stringResource(R.string.with_food))
    val foods = listOf(WithFood.BEFORE_MEAL, WithFood.WITH_MEAL, WithFood.AFTER_MEAL, WithFood.ANY)
    Segmented(
        options = listOf(R.string.food_before, R.string.food_with, R.string.food_after, R.string.food_any).map { stringResource(it) },
        selectedIndex = foods.indexOf(form.withFood).coerceAtLeast(0),
        onSelect = { i -> edit { it.copy(withFood = foods[i]) } },
    )
}

@Composable
private fun TimeCard(label: String, timeText: String, onClick: () -> Unit) {
    val colors = PillBox.colors
    val hint = stringResource(R.string.tap_to_change)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(colors.card, MaterialTheme.shapes.large)
            .border(Dimens.ChipBorder, colors.border, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $timeText. $hint" }
            .padding(Space.S12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(timeText, style = MaterialTheme.typography.displaySmall, color = colors.text, textAlign = TextAlign.Center)
        Text(hint, style = MaterialTheme.typography.labelMedium, color = colors.accent, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DoseChip(time: LocalTime, fmt: Formatter, modifier: Modifier, onClick: () -> Unit) {
    val block = blockOf(time)
    val (bg, fg) = blockColors(block)
    val name = blockName(block, afternoon = true)
    val timeText = fmt.time(time)
    val hint = stringResource(R.string.tap_to_change)
    Column(
        modifier = modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .background(bg, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $timeText. $hint" }
            .padding(horizontal = Space.S8, vertical = Space.S10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = fg, textAlign = TextAlign.Center)
        Text(timeText, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoursList(doses: List<LocalTime>, first: LocalTime, fmt: Formatter) {
    val colors = PillBox.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Block.values().forEach { block ->
            val inBlock = doses.filter { blockOf(it) == block }
            if (inBlock.isEmpty()) return@forEach
            val (bg, fg) = blockColors(block)
            Row(
                Modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.large).padding(Space.S8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                Text(blockName(block), style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.weight(0.6f))
                FlowRow(
                    Modifier.weight(1.6f),
                    horizontalArrangement = Arrangement.spacedBy(Space.S8),
                    verticalArrangement = Arrangement.spacedBy(Space.S8),
                ) {
                    inBlock.forEach { t ->
                        Box(
                            Modifier
                                .heightIn(min = Dimens.TouchTargetMin)
                                .background(colors.card, MaterialTheme.shapes.small)
                                .then(if (t == first) Modifier.border(Dimens.ChipBorder, colors.primary, MaterialTheme.shapes.small) else Modifier)
                                .padding(horizontal = Space.S10, vertical = Space.S8),
                            contentAlignment = Alignment.Center,
                        ) { Text(fmt.time(t), style = MaterialTheme.typography.labelMedium, color = colors.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = PillBox.colors
    Row(
        Modifier.fillMaxWidth().background(colors.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PBTimePickerDialog(initial: LocalTime, is24: Boolean, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val colors = PillBox.colors
    val st = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = { Text(stringResource(R.string.select_time), style = MaterialTheme.typography.labelMedium, color = colors.textMuted) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = st,
                    colors = TimePickerDefaults.colors(
                        selectorColor = colors.primary,
                        periodSelectorSelectedContainerColor = colors.primaryContainer,
                        timeSelectorSelectedContainerColor = colors.primaryContainer,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(st.hour, st.minute)) }) {
                Text(stringResource(R.string.ok), style = MaterialTheme.typography.labelLarge, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge, color = colors.accent) }
        },
    )
}

// ---------------------------------------------------------------- step 3

private fun locale() = java.util.Locale.getDefault()

private fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale()))

@Composable
private fun Step3(
    form: AddMedicineForm,
    showErrors: Boolean,
    today: LocalDate,
    fmt: Formatter,
    edit: ((AddMedicineForm) -> AddMedicineForm) -> Unit,
    openDatePicker: () -> Unit,
) {
    if (form.hasDays()) {
        FieldLabel(stringResource(R.string.which_days))
        ChipGroup {
            PBChip(stringResource(R.string.days_every_day), form.days == DaysChoice.EVERY_DAY, { edit { it.copy(days = DaysChoice.EVERY_DAY) } })
            PBChip(stringResource(R.string.days_specific), form.days == DaysChoice.SPECIFIC, { edit { it.copy(days = DaysChoice.SPECIFIC) } })
            PBChip(stringResource(R.string.days_every_n), form.days == DaysChoice.EVERY_N, { edit { it.copy(days = DaysChoice.EVERY_N) } })
        }
        when (form.days) {
            DaysChoice.SPECIFIC -> {
                val missing = showErrors && FormError.DAYS_MISSING in form.step3Errors()
                val first = com.pillbox.ui.firstDayOfWeek()
                val week = (0..6).map { first.plus(it.toLong()) }
                Box(Modifier.size(Space.S10))
                WeekdayRow(week, form.weekdays) { day -> edit { f -> f.copy(weekdays = if (day in f.weekdays) f.weekdays - day else f.weekdays + day) } }
                if (missing) {
                    ErrorText(stringResource(R.string.days_none))
                } else if (form.weekdays.isNotEmpty()) {
                    val names = week.filter { it in form.weekdays }.map { it.getDisplayName(TextStyle.FULL, locale()) }
                    HelperText(stringResourceIso(R.string.days_picked, fmt.list(names, stringResource(R.string.list_sep), stringResource(R.string.list_and))))
                }
            }
            DaysChoice.EVERY_N -> {
                val label = stringResource(R.string.repeat_every)
                FieldLabel(label)
                Stepper(
                    valueText = fmt.number(form.everyN), unit = pluralStringResource(R.plurals.unit_days, form.everyN), label = label,
                    onMinus = { edit { it.copy(everyN = (it.everyN - 1).coerceAtLeast(2)) } },
                    onPlus = { edit { it.copy(everyN = (it.everyN + 1).coerceAtMost(60)) } },
                    minusEnabled = form.everyN > 2, plusEnabled = form.everyN < 60,
                )
                val s = form.startDate(today)
                val dates = (0..2).map { shortDate(s.plusDays((it * form.everyN).toLong())) }
                HelperText(stringResourceIso(R.string.repeat_hint, fmt.digits(dates.joinToString(", "))))
            }
            DaysChoice.EVERY_DAY -> Unit
        }
    }

    if (form.isEdit) {
        FieldLabel(stringResource(R.string.started_label))
        val date = fmt.digits(shortDate(form.startDate(today)))
        val hint = stringResource(R.string.start_fixed)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ListRowMinHeight)
                .background(PillBox.colors.card, MaterialTheme.shapes.large)
                .padding(horizontal = Space.S16, vertical = Space.S10)
                .semantics(mergeDescendants = true) { contentDescription = "$date. $hint" },
            verticalArrangement = Arrangement.Center,
        ) {
            Text(date, style = MaterialTheme.typography.labelLarge, color = PillBox.colors.text)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = PillBox.colors.textMuted)
        }
    } else {
    FieldLabel(stringResource(R.string.starts))
    Segmented(
        options = listOf(
            stringResource(R.string.start_today), stringResource(R.string.start_tomorrow),
            form.pickedDate?.takeIf { form.start == StartChoice.PICK }?.let { fmt.digits(shortDate(it)) } ?: stringResource(R.string.start_pick),
        ),
        selectedIndex = form.start.ordinal,
        onSelect = { i ->
            when (i) {
                0 -> edit { it.copy(start = StartChoice.TODAY) }
                1 -> edit { it.copy(start = StartChoice.TOMORROW) }
                else -> openDatePicker()
            }
        },
    )
    }

    FieldLabel(stringResource(R.string.how_long))
    Segmented(
        options = listOf(R.string.len_ongoing, R.string.len_7, R.string.len_other).map { stringResource(it) },
        selectedIndex = form.length.ordinal,
        onSelect = { i -> edit { it.copy(length = LengthChoice.values()[i]) } },
    )
    if (form.length == LengthChoice.OTHER) {
        val label = stringResource(R.string.how_long)
        Box(Modifier.size(Space.S12))
        Stepper(
            valueText = fmt.number(form.lengthDays), unit = pluralStringResource(R.plurals.unit_days, form.lengthDays), label = label,
            onMinus = { edit { it.copy(lengthDays = (it.lengthDays - 1).coerceAtLeast(1)) } },
            onPlus = { edit { it.copy(lengthDays = (it.lengthDays + 1).coerceAtMost(365)) } },
            minusEnabled = form.lengthDays > 1, plusEnabled = form.lengthDays < 365,
        )
    }

    Box(Modifier.size(Space.S16))
    InfoCard(summaryText(form, fmt), Icons.Filled.DateRange)
}

@Composable
private fun WeekdayRow(week: List<DayOfWeek>, selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val colors = PillBox.colors
    val on = stringResource(R.string.state_selected)
    val off = stringResource(R.string.state_not_selected)
    Row(Modifier.fillMaxWidth()) {
        week.forEach { day ->
            val isOn = day in selected
            val full = day.getDisplayName(TextStyle.FULL, locale())
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.TouchTargetMin)
                    .clickable(role = Role.Checkbox) { onToggle(day) }
                    .semantics { contentDescription = full; stateDescription = if (isOn) on else off },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(if (isOn) colors.primary else colors.card, CircleShape)
                        .border(Dimens.ChipBorder, if (isOn) colors.primary else colors.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isOn) colors.onPrimary else colors.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun summaryText(form: AddMedicineForm, fmt: Formatter): String {
    val freq = when (form.freq) {
        FreqChoice.ONCE -> stringResource(R.string.freq_once)
        FreqChoice.TWICE -> stringResource(R.string.freq_twice)
        FreqChoice.THREE -> stringResource(R.string.freq_three)
        FreqChoice.HOURS -> stringResource(
            R.string.sum_hours, pluralStringResource(R.plurals.n_hours, form.hours, fmt.number(form.hours)), fmt.time(form.firstDose),
            pluralStringResource(R.plurals.n_doses, 24 / form.hours, fmt.number(24 / form.hours)),
        )
        FreqChoice.AS_NEEDED -> stringResource(R.string.freq_prn)
    }
    val length = when (form.length) {
        LengthChoice.ONGOING -> stringResource(R.string.sum_ongoing)
        LengthChoice.SEVEN -> stringResourceIso(R.string.sum_for, pluralStringResource(R.plurals.n_days, 7, fmt.number(7)))
        LengthChoice.OTHER -> stringResourceIso(R.string.sum_for, pluralStringResource(R.plurals.n_days, form.lengthDays, fmt.number(form.lengthDays)))
    }
    if (!form.hasDays()) return stringResourceIso(R.string.sum_short, freq, length)
    val days = when (form.days) {
        DaysChoice.EVERY_DAY -> stringResource(R.string.sum_every_day)
        DaysChoice.SPECIFIC -> {
            val first = com.pillbox.ui.firstDayOfWeek()
            val names = (0..6).map { first.plus(it.toLong()) }.filter { it in form.weekdays }.map { it.getDisplayName(TextStyle.SHORT, locale()) }
            if (names.isEmpty()) stringResource(R.string.sum_specific_none)
            else stringResourceIso(R.string.sum_on, fmt.list(names, stringResource(R.string.list_sep), stringResource(R.string.list_and)))
        }
        DaysChoice.EVERY_N -> stringResourceIso(R.string.sum_every_n, pluralStringResource(R.plurals.n_days, form.everyN, fmt.number(form.everyN)))
    }
    return stringResourceIso(R.string.sum_full, freq, days, length)
}
