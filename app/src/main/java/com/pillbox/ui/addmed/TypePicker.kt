package com.pillbox.ui.addmed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pillbox.R
import com.pillbox.domain.DoseUnit
import com.pillbox.ui.components.DesignIcons
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.stringResourceIso
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.PillIcon
import com.pillbox.ui.typeName

/** The type field on Add medicine: shows the chosen type with its icon. Pressing it opens the type popup. One tidy row instead of fifteen chips. */
@Composable
fun TypeButton(type: DoseUnit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val name = stringResource(typeName(type))
    val desc = stringResourceIso(R.string.type_button_desc, name)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.PrimaryButtonHeight)
            .background(c.card, MaterialTheme.shapes.large)
            .border(Dimens.ChipBorder, c.border, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        PillIcon(type, size = 32.dp)
        Text(name, style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.expiry_change), style = MaterialTheme.typography.labelLarge, color = c.accent)
        Icon(DesignIcons.Chevron, contentDescription = null, tint = c.textMuted)
    }
}

/** The type popup: every type with its icon, one is marked, Choose confirms. Nothing changes until Choose. */
@Composable
fun TypePickerDialog(current: DoseUnit, onDismiss: () -> Unit, onChoose: (DoseUnit) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        TypePickerCard(picked, onPick = { picked = it }, onChoose = { onChoose(picked) }, onCancel = onDismiss)
    }
}

/** The popup's card, separate from the Dialog so screenshots can draw it. */
@Composable
internal fun TypePickerCard(picked: DoseUnit, onPick: (DoseUnit) -> Unit, onChoose: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Column(
        modifier.fillMaxWidth().heightIn(max = maxHeight).background(c.card, MaterialTheme.shapes.extraLarge).padding(vertical = Space.S16),
    ) {
        Text(
            stringResource(R.string.type_choose_title), style = MaterialTheme.typography.titleMedium, color = c.text,
            modifier = Modifier.padding(horizontal = Space.S16).padding(bottom = Space.S8).semantics { heading() },
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal = Space.S8)) {
            DoseUnit.entries.forEach { t ->
                val on = t == picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ListRowMinHeight)
                        .background(if (on) c.primaryContainer else c.card, MaterialTheme.shapes.medium)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(t) })
                        .padding(horizontal = Space.S8, vertical = Space.S6),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    PillIcon(t, size = 36.dp)
                    Text(stringResource(typeName(t)), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(28.dp).background(if (on) c.primary else c.card, CircleShape).border(Dimens.ChipBorder, if (on) c.primary else c.placeholder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(DesignIcons.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Column(Modifier.padding(horizontal = Space.S16).padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.type_choose), onChoose)
            LinkButton(stringResource(R.string.cancel), onCancel)
        }
    }
}
