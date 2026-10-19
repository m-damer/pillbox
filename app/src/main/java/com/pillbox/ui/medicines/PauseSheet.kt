package com.pillbox.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pillbox.R
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.Stepper
import com.pillbox.ui.shortDate
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.LocalDate

private enum class PauseChoice { ONE, THREE, OTHER, UNTIL_RESUME }

/** "How long to pause?" Calls [onPause] with the number of days, or null for "until I resume". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    medicineName: String,
    today: LocalDate,
    fmt: Formatter,
    onPause: (days: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PillBox.colors
    var choice by rememberSaveable { mutableStateOf(PauseChoice.THREE) }
    var other by rememberSaveable { mutableIntStateOf(10) }
    val days: Int? = when (choice) {
        PauseChoice.ONE -> 1
        PauseChoice.THREE -> 3
        PauseChoice.OTHER -> other
        PauseChoice.UNTIL_RESUME -> null
    }
    val note = if (days != null) stringResource(R.string.pause_restart_on, shortDate(today.plusDays(days.toLong()), fmt))
    else stringResource(R.string.pause_stays_off)
    val daysLabel = stringResource(R.string.pause_days_label)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Text(
                stringResource(R.string.pause_title, medicineName), style = MaterialTheme.typography.titleMedium, color = c.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.pause_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            ChipGroup {
                PBChip(stringResource(R.string.pause_1_day), choice == PauseChoice.ONE, { choice = PauseChoice.ONE })
                PBChip(pluralStringResource(R.plurals.n_days, 3, fmt.number(3)), choice == PauseChoice.THREE, { choice = PauseChoice.THREE })
                PBChip(stringResource(R.string.pause_other), choice == PauseChoice.OTHER, { choice = PauseChoice.OTHER })
                PBChip(stringResource(R.string.pause_until_resume), choice == PauseChoice.UNTIL_RESUME, { choice = PauseChoice.UNTIL_RESUME })
            }
            if (choice == PauseChoice.OTHER) {
                Stepper(
                    valueText = fmt.number(other),
                    unit = pluralStringResource(R.plurals.pause_days_unit, other),
                    label = daysLabel,
                    onMinus = { other = (other - 1).coerceAtLeast(1) },
                    onPlus = { other = (other + 1).coerceAtMost(365) },
                    minusEnabled = other > 1,
                    plusEnabled = other < 365,
                )
            }
            Text(
                note, style = MaterialTheme.typography.bodyMedium, color = c.accent,
                modifier = Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S16),
            )
            PrimaryButton(stringResource(R.string.pause_confirm), { onPause(days) })
            OutlineButton(stringResource(R.string.pause_cancel), onDismiss)
            Column(Modifier.padding(bottom = Space.S12)) {}
        }
    }
}
