package com.pillbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** A settings row with a title, an optional line under it, and a switch with a check mark when on. The whole row is the touch target. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val c = PillBox.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SettingsRowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Space.S16, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            thumbContent = if (checked) { { Icon(com.pillbox.ui.components.DesignIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.card, checkedTrackColor = c.primary, checkedIconColor = c.primary,
                uncheckedThumbColor = c.card, uncheckedTrackColor = c.placeholder, uncheckedBorderColor = c.placeholder,
            ),
        )
    }
}
