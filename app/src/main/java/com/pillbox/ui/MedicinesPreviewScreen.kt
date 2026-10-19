package com.pillbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pillbox.R
import com.pillbox.data.MedicineWithSchedule
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** Temporary home for M2 so the new medicines can be seen. Today (M3) and Medicines (M5) replace it. */
@Composable
fun MedicinesPreviewScreen(medicines: List<MedicineWithSchedule>, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PillBox.colors
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Text(
            stringResource(R.string.medicines_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S16).semantics { heading() },
        )
        if (medicines.isEmpty()) {
            Text(
                stringResource(R.string.medicines_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
                modifier = Modifier.weight(1f).padding(horizontal = Dimens.ScreenPaddingH),
            )
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.ScreenPaddingH),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                items(medicines, key = { it.medicine.id }) { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.ListRowMinHeight)
                            .background(colors.card, MaterialTheme.shapes.large)
                            .border(Dimens.ChipBorder, colors.border, MaterialTheme.shapes.large)
                            .padding(Space.S16),
                    ) {
                        Text(m.medicine.name, style = MaterialTheme.typography.titleMedium, color = colors.text)
                        m.medicine.strength?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted) }
                    }
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.add_medicine), onAdd,
            Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12),
        )
    }
}
