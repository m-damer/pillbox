package com.pillbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

enum class Tab { TODAY, MEDS, PEOPLE, SETTINGS }

/** Today, Meds, [+], People, Settings. Labels wrap to two lines at large text instead of clipping. */
@Composable
fun PillBoxBottomBar(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier, onlyTodayAndSettings: Boolean = false) {
    val c = PillBox.colors
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.card, MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.ZeroCornerSize, bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize))
                .navigationBarsPadding()
                .heightIn(min = Dimens.BottomNavHeight)
                .padding(horizontal = Space.S8)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Tab.TODAY, Icons.Filled.DateRange, R.string.nav_today, selected, onSelect, Modifier.weight(1f))
            if (!onlyTodayAndSettings) NavItem(Tab.MEDS, Icons.Filled.List, R.string.nav_meds, selected, onSelect, Modifier.weight(1f))
            Box(Modifier.weight(0.9f))
            if (!onlyTodayAndSettings) NavItem(Tab.PEOPLE, Icons.Filled.Person, R.string.nav_people, selected, onSelect, Modifier.weight(1f))
            NavItem(Tab.SETTINGS, Icons.Filled.Settings, R.string.nav_settings, selected, onSelect, Modifier.weight(1f))
        }
        val add = stringResource(R.string.add_medicine)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(Dimens.Fab)
                .background(c.primary, CircleShape)
                .border(4.dp, c.card, CircleShape)
                .semantics { contentDescription = add; role = Role.Button }
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun NavItem(tab: Tab, icon: ImageVector, label: Int, selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val c = PillBox.colors
    val on = tab == selected
    Column(
        modifier
            .heightIn(min = Dimens.TouchTargetMin)
            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
            .padding(vertical = Space.S8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.S2),
    ) {
        Icon(icon, contentDescription = null, tint = if (on) c.accent else c.textMuted, modifier = Modifier.size(24.dp))
        Text(
            stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (on) c.accent else c.textMuted,
            textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}
