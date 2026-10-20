package com.pillbox.ui.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** One choice in a list of choices: title, optional line under it, and a round mark with a check when chosen. Use inside a selectableGroup. */
@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = PillBox.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SettingsRowMinHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Box(
            Modifier.size(28.dp).then(if (selected) Modifier.background(c.primary, CircleShape) else Modifier.border(2.dp, c.border, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
    }
}
