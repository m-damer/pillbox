package com.pillbox.reminder

import com.pillbox.ui.components.stringResourceIso
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pillbox.R
import com.pillbox.domain.DoseRow
import com.pillbox.ui.Formatter
import com.pillbox.ui.blockOf
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.RowButton
import com.pillbox.ui.today.foodText
import com.pillbox.ui.unitPlural
import java.time.LocalDateTime

@Composable
fun ReminderScreen(
    at: LocalDateTime,
    rows: List<DoseRow>,
    fmt: Formatter,
    onTake: (DoseRow) -> Unit,
    onSkip: (DoseRow) -> Unit,
    onTakeAll: () -> Unit,
    onSkipAll: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
    /** Names of the people other than me, by person id; a dose of theirs shows "For Mom" above the medicine. */
    owners: Map<Long, String> = emptyMap(),
) {
    val c = PillBox.colors
    val block = stringResource(Reminders.blockNameRes(blockOf(at.toLocalTime())))
    val big = LocalDensity.current.fontScale > 1.3f
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPaddingH),
        verticalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Text(fmt.time(at.toLocalTime()), style = MaterialTheme.typography.displayMedium, color = c.text)
        Text(
            stringResourceIso(R.string.notif_title, block),
            style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() },
        )
        rows.forEach { row ->
            val med = row.medicine
            val amount = fmt.amount(med.doseAmount) + " " + pluralStringResource(unitPlural(med.unit), quantityFor(med.doseAmount))
            val line = listOfNotNull(amount, foodText(row.schedule.withFood)).joinToString(stringResource(R.string.dot_sep))
            Column(
                Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S12),
                verticalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                owners[med.personId]?.let { Text(stringResourceIso(R.string.reminder_for, it), style = MaterialTheme.typography.labelLarge, color = c.accent) }
                Text(med.name + (com.pillbox.ui.strengthLabel(med.strength)?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    RowButton(stringResource(R.string.take), stringResourceIso(R.string.take_med, med.name), filled = true) { onTake(row) }
                    RowButton(stringResource(R.string.skip), stringResourceIso(R.string.skip_med, med.name), filled = false) { onSkip(row) }
                }
            }
        }
        if (rows.size > 1) {
            if (big) {
                PrimaryButton(stringResource(R.string.take_all), onTakeAll)
                OutlineButton(stringResource(R.string.skip_all), onSkipAll)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                    PrimaryButton(stringResource(R.string.take_all), onTakeAll, Modifier.weight(1f))
                    OutlineButton(stringResource(R.string.skip_all), onSkipAll, Modifier.weight(1f))
                }
            }
        }
        LinkButton(stringResource(R.string.snooze_15), onSnooze)
        Text(stringResource(R.string.reminder_nag), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}
