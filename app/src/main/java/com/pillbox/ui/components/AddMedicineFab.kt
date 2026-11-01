package com.pillbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** The floating "+ Add medicine" button. [onHeight] reports its height so a list can leave that much room at its end. */
@Composable
fun AddMedicineFab(onClick: () -> Unit, modifier: Modifier = Modifier, onHeight: (Dp) -> Unit = {}) {
    val c = PillBox.colors
    val density = LocalDensity.current
    Row(
        modifier
            .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
            .heightIn(min = 56.dp)
            .background(c.primary, RoundedCornerShape(28.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.S20, vertical = Space.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        Icon(DesignIcons.Plus, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.add_medicine), style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
    }
}
