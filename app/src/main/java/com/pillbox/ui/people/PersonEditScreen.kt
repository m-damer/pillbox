package com.pillbox.ui.people

import com.pillbox.ui.components.stringResourceIso
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.PeopleRules
import com.pillbox.domain.Relation
import com.pillbox.ui.addmed.fieldColors
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.DangerOutlineButton
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.ErrorText
import com.pillbox.ui.components.FieldLabel
import com.pillbox.ui.components.HelperText
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration

@Composable
fun PersonEditScreen(vm: PersonEditViewModel, onClose: (PersonResult?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { state.done?.let { onClose(it) } }
    BackHandler { onClose(null) }
    PersonEditContent(
        state, onBack = { onClose(null) }, onName = vm::setName, onRelation = vm::setRelation, onColor = vm::setColor,
        onRing = vm::setRing, onBirthYear = vm::setBirthYear, onSave = vm::save, onAskRemove = vm::askRemove,
        onKeep = vm::keep, onRemove = vm::remove,
    )
}

/** Add person, Edit person and Edit my profile, without a ViewModel, so screenshots can draw them. */
@Composable
fun PersonEditContent(
    state: PersonEditState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onRelation: (Relation) -> Unit,
    onColor: (Int) -> Unit,
    onRing: (Boolean) -> Unit,
    onBirthYear: (String) -> Unit,
    onSave: () -> Unit,
    onAskRemove: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = PillBox.colors
    val profile = state.mode == PersonMode.PROFILE
    val title = stringResource(
        when (state.mode) { PersonMode.ADD -> R.string.person_add_title; PersonMode.EDIT -> R.string.person_edit_title; PersonMode.PROFILE -> R.string.profile_title },
    )
    val name = PeopleRules.cleanName(state.name)
    val shownName = name.ifEmpty { "" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH)) {
            Box(Modifier.fillMaxWidth().padding(vertical = Space.S12), contentAlignment = Alignment.Center) {
                Avatar(
                    PeopleRules.initials(listOf(shownName)).first(), state.colorIndex, solid = profile, size = 80.dp,
                    modifier = Modifier.semantics { contentDescription = shownName },
                )
            }
            if (!profile) {
                ColorRow(state.colorIndex, onColor)
            }
            FieldLabel(stringResource(if (profile) R.string.profile_name_label else R.string.person_name))
            OutlinedTextField(
                value = state.name, onValueChange = onName,
                placeholder = { Text(stringResource(R.string.person_name_hint), style = MaterialTheme.typography.bodyLarge) },
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, shape = MaterialTheme.shapes.large, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton),
            )
            if (profile) {
                Row(
                    Modifier.padding(top = Space.S12).fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                    horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.profile_note), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                }
                FieldLabel(stringResource(R.string.profile_birth))
                OutlinedTextField(
                    value = state.birthYear, onValueChange = onBirthYear, singleLine = true, isError = state.birthYearError,
                    textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.large, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.widthIn(min = 140.dp).heightIn(min = Dimens.StepperButton),
                )
                if (state.birthYearError) ErrorText(stringResource(R.string.profile_birth_error)) else HelperText(stringResource(R.string.profile_birth_help))
            } else {
                FieldLabel(stringResource(R.string.person_who))
                ChipGroup {
                    listOf(Relation.PARENT to R.string.rel_parent, Relation.CHILD to R.string.rel_child, Relation.PARTNER to R.string.rel_partner, Relation.OTHER to R.string.rel_other)
                        .forEach { (r, label) -> PBChip(stringResource(label), state.relation == r, { onRelation(r) }) }
                }
                Column(Modifier.padding(top = Space.S16).fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                    SwitchRow(stringResource(R.string.person_ring), state.ring, onRing, subtitle = stringResource(R.string.person_ring_sub))
                }
                Box(Modifier.size(Space.S12))
            }
        }
        Column(
            Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            val saveText = when {
                !state.canSave && !state.saving -> stringResource(R.string.person_type_name)
                profile -> stringResource(R.string.btn_save)
                else -> stringResource(R.string.person_save)
            }
            PrimaryButton(saveText, onSave, enabled = state.canSave)
            if (state.mode == PersonMode.EDIT) {
                DangerOutlineButton(stringResourceIso(R.string.person_remove, name), onAskRemove)
            }
        }
    }

    if (state.showRemove) {
        MessageDialog(DialogKind.Danger, stringResourceIso(R.string.person_remove_title, name), stringResource(R.string.person_remove_body), onKeep) {
            DangerButton(stringResourceIso(R.string.person_remove, name), onRemove)
            LinkButton(stringResourceIso(R.string.person_keep, name), onKeep)
        }
    }
}

/** Six round swatches, each a 48dp radio button with a name for TalkBack; the chosen one has a ring and a check mark. */
@Composable
private fun ColorRow(selected: Int, onColor: (Int) -> Unit) {
    val c = PillBox.colors
    val names = listOf(R.string.person_color_0, R.string.person_color_1, R.string.person_color_2, R.string.person_color_3, R.string.person_color_4, R.string.person_color_5)
        .map { stringResource(it) }
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until PeopleRules.COLORS) {
            val on = i == PeopleRules.colorFor(selected)
            Box(
                Modifier
                    .size(48.dp)
                    .background(PillBox.illustration.avatars[i], CircleShape)
                    .then(if (on) Modifier.border(BorderStroke(2.dp, c.primary), CircleShape) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onColor(i) })
                    .semantics { contentDescription = names[i] },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) }
        }
    }
}
