package com.pillbox.ui.medicines

import com.pillbox.ui.components.stringResourceIso
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import com.pillbox.R
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.Medicine
import com.pillbox.domain.Schedule
import com.pillbox.domain.StockMath
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.ErrorText
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.RoundStepButton
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.unitPlural

/** Bold the parts of a sentence given as "New total {total} · about {days}". */
private fun boldTokens(template: String, values: Map<String, String>, bold: SpanStyle): AnnotatedString = buildAnnotatedString {
    var rest = template
    while (rest.isNotEmpty()) {
        val hit = values.keys.mapNotNull { k -> rest.indexOf("{$k}").takeIf { it >= 0 }?.let { it to k } }.minByOrNull { it.first }
        if (hit == null) { append(rest); break }
        append(rest.substring(0, hit.first))
        withStyle(bold) { append(values.getValue(hit.second)) }
        rest = rest.substring(hit.first + hit.second.length + 2)
    }
}

/** Add stock: how many boxes were bought and how many are in a box. Used on Medicine detail and from Today's "No tablets left". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockSheet(
    medicine: Medicine,
    schedule: Schedule?,
    fmt: Formatter,
    onSave: (boxes: Int, perBox: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PillBox.colors
    var boxes by rememberSaveable { mutableIntStateOf(1) }
    var perBoxText by rememberSaveable { mutableStateOf(medicine.packSize?.takeIf { it > 0 }?.toString() ?: "") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val perBox = fmt.parseInt(perBoxText)?.takeIf { it > 0 }
    val total = StockMath.newTotal(medicine.stock, boxes, perBox ?: 0)
    val days = StockMath.daysFor(total, medicine, schedule)

    val boxesLabel = stringResource(com.pillbox.ui.boughtLabel(medicine.unit))
    val perLabel = stringResource(com.pillbox.ui.perPackLabel(medicine.unit))
    val unitWord = pluralStringResource(unitPlural(medicine.unit), quantityFor(2.0))
    val totalText = fmt.amount(total) + " " + pluralStringResource(unitPlural(medicine.unit), quantityFor(total))
    val summary = if (days != null) {
        boldTokens(
            stringResource(R.string.stock_new_total), 
            mapOf("total" to totalText, "days" to pluralStringResource(R.plurals.n_days, days.coerceAtLeast(0), fmt.number(days))),
            SpanStyle(fontWeight = FontWeight.Bold),
        )
    } else {
        boldTokens(stringResource(R.string.stock_new_total_only), mapOf("total" to totalText), SpanStyle(fontWeight = FontWeight.Bold))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                MedicineBadge(medicine.unit)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stock_title), style = MaterialTheme.typography.titleMedium, color = c.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        listOfNotNull(medicine.name, com.pillbox.ui.strengthLabel(medicine.strength)).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton), verticalAlignment = Alignment.CenterVertically) {
                Text(boxesLabel, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                RoundStepButton("−", stringResourceIso(R.string.stepper_decrease, boxesLabel), boxes > 1) { boxes = (boxes - 1).coerceAtLeast(1) }
                Text(
                    fmt.number(boxes), style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 56.dp).padding(horizontal = Space.S8),
                )
                RoundStepButton("+", stringResourceIso(R.string.stepper_increase, boxesLabel), boxes < 99) { boxes = (boxes + 1).coerceAtMost(99) }
            }
            HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton), verticalAlignment = Alignment.CenterVertically) {
                Text(perLabel, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f).padding(end = Space.S12))
                Box(
                    Modifier
                        .widthIn(min = 96.dp)
                        .heightIn(min = Dimens.TouchTargetMin)
                        .background(c.primaryContainer, MaterialTheme.shapes.medium)
                        .border(Dimens.ChipBorder, if (showError && perBox == null) c.danger else c.primaryContainer, MaterialTheme.shapes.medium)
                        .padding(horizontal = Space.S16, vertical = Space.S8),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = fmt.digits(perBoxText),
                        onValueChange = { text -> perBoxText = fmt.parseInt(text)?.toString() ?: "" },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = c.text, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 48.dp).semantics { contentDescription = perLabel },
                    )
                }
            }
            if (showError && perBox == null) ErrorText(stringResourceIso(R.string.stock_per_error, unitWord))
            Text(
                summary, style = MaterialTheme.typography.bodyMedium, color = c.accent,
                modifier = Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S16),
            )
            PrimaryButton(stringResource(R.string.stock_save), {
                if (perBox == null) showError = true else onSave(boxes, perBox)
            })
            Box(Modifier.size(Space.S12))
        }
    }
}
