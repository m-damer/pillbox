package com.pillbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pillbox.R
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.util.Locale

/** Small caption above a field. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Space.S16, bottom = Space.S6).semantics { heading() },
    )
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(top = Space.S6).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
fun HelperText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Space.S8),
    )
}

/** Single choice pill with a check mark when selected (color is never the only signal). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.S10),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) { content() }
}

@Composable
fun PBChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PillBox.colors
    Row(
        modifier = modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .background(if (selected) colors.primary else colors.card, Dimens.ButtonShape)
            .border(Dimens.ChipBorder, if (selected) colors.primary else colors.border, Dimens.ButtonShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.S6))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.text)
    }
}

/** Two to four options in one track, the selected one raised. */
@Composable
fun Segmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = PillBox.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.night, Dimens.ButtonShape)
            .padding(Space.S4)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.TouchTargetMin)
                    .background(if (selected) colors.card else Color.Transparent, Dimens.ButtonShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = Space.S8, vertical = Space.S8),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.primary else colors.nightText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RoundStepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = PillBox.colors
    Box(
        modifier = Modifier
            .size(Dimens.StepperButton)
            .border(Dimens.ChipBorder, if (enabled) colors.border else colors.divider, CircleShape)
            .background(colors.card, CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.headlineSmall, color = if (enabled) colors.primary else colors.placeholder)
    }
}

/**
 * − [value unit] +. Pass [onValueText] to let the user type the number (used for the box size).
 * [label] names the field for TalkBack ("Decrease How much each time").
 */
@Composable
fun Stepper(
    valueText: String,
    unit: String,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    onValueText: ((String) -> Unit)? = null,
    isError: Boolean = false,
) {
    val colors = PillBox.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
        RoundStepButton("−", stringResource(R.string.stepper_decrease, label), minusEnabled, onMinus)
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.StepperButton)
                .background(colors.card, MaterialTheme.shapes.large)
                .border(Dimens.ChipBorder, if (isError) colors.danger else colors.border, MaterialTheme.shapes.large)
                .padding(horizontal = Space.S12, vertical = Space.S8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (onValueText != null) {
                BasicTextField(
                    value = valueText,
                    onValueChange = onValueText,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.text, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 56.dp).semantics { contentDescription = label },
                )
            } else {
                Text(valueText, style = MaterialTheme.typography.headlineSmall, color = colors.text)
            }
            Spacer(Modifier.width(Space.S8))
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f, fill = false))
        }
        RoundStepButton("+", stringResource(R.string.stepper_increase, label), plusEnabled, onPlus)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = PillBox.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Dimens.ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.PrimaryButtonHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PillBox.colors
    OutlinedButton(
        onClick = onClick,
        shape = Dimens.ButtonShape,
        border = BorderStroke(Dimens.ChipBorder, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.PrimaryButtonHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PillBox.colors
    Button(
        onClick = onClick,
        shape = Dimens.ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onPrimary),
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.PrimaryButtonHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = Dimens.TouchTargetMin)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = PillBox.colors.accent)
    }
}

enum class DialogKind { Success, Warning, Danger }

/** The rounded message card used for success, warning and error prompts. Announced as a status message. */
@Composable
fun MessageDialog(
    kind: DialogKind,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    val colors = PillBox.colors
    val (bg, fg, icon) = when (kind) {
        DialogKind.Success -> Triple(colors.successContainer, colors.success, Icons.Filled.Check)
        DialogKind.Warning -> Triple(colors.warningContainer, colors.warning, Icons.Filled.Warning)
        DialogKind.Danger -> Triple(colors.dangerContainer, colors.danger, Icons.Filled.Warning)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card, MaterialTheme.shapes.extraLarge)
                .padding(Space.S20),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            DialogIcon(bg, fg, icon)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.size(Space.S4))
            buttons()
        }
    }
}

@Composable
private fun DialogIcon(bg: Color, fg: Color, icon: ImageVector) {
    Box(Modifier.size(56.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
    }
}

/** Back arrow, title, subtitle and the 3-part progress bar that every step shares. */
@Composable
fun StepHeader(title: String, subtitle: String, step: Int, steps: Int, backLabel: String, onBack: () -> Unit) {
    val colors = PillBox.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
            Box(
                Modifier
                    .size(Dimens.TouchTargetMin)
                    .semantics { contentDescription = backLabel; role = Role.Button }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.text) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.size(Space.S12))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(Space.S6)) {
            for (i in 1..steps) {
                Box(Modifier.weight(1f).heightIn(min = 6.dp).background(if (i <= step) colors.primary else colors.border, CircleShape))
            }
        }
    }
}
