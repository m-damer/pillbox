package com.pillbox.ui.people

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.PeopleRules
import com.pillbox.domain.Person
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration

/** A person with the words the screens show for them: "Me" is always the localized word, others use their name. */
data class PersonChip(val person: Person, val label: String, val initials: String)

@Composable
fun rememberChips(people: List<Person>): List<PersonChip> {
    val me = stringResource(R.string.person_me)
    val labels = people.map { if (it.isMe) me else it.name }
    val initials = PeopleRules.initials(labels)
    return people.mapIndexed { i, p -> PersonChip(p, labels[i], initials[i]) }
}

/** A round avatar. The owner of the phone is solid; everyone else gets the color they picked. The letters are decoration: the name is read next to it. */
@Composable
fun Avatar(initials: String, colorIndex: Int, solid: Boolean, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = PillBox.colors
    val bg = if (solid) c.primary else PillBox.illustration.avatars[PeopleRules.colorFor(colorIndex)]
    val fg = if (solid) c.onPrimary else c.accent
    Box(modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        if (initials.isEmpty()) Icon(Icons.Filled.Person, contentDescription = null, tint = fg, modifier = Modifier.size(size / 2))
        else Text(initials, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/**
 * Row of avatars at the top of Today and Medicines to switch whose medicines are shown. Only drawn when there is
 * more than one person. Each avatar is a 48dp button that TalkBack reads as "Mom, selected".
 */
@Composable
fun PeopleSwitcher(chips: List<PersonChip>, activeId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (chips.size < 2) return
    val c = PillBox.colors
    val selectedText = stringResource(R.string.switcher_selected)
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.S4), verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val on = chip.person.id == activeId
            val desc = if (on) chip.label + ", " + selectedText else chip.label
            Box(
                Modifier
                    .size(Dimens.TouchTargetMin)
                    .clip(CircleShape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(chip.person.id) })
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    chip.initials, chip.person.colorIndex, chip.person.isMe, size = 40.dp,
                    modifier = if (on) Modifier.border(BorderStroke(3.dp, c.primary), CircleShape) else Modifier,
                )
            }
        }
    }
}

/** The people shown in the switcher and who is selected. Null on screens that have no switcher (and in screenshots of one person). */
data class PeopleBar(val people: List<Person>, val activeId: Long?, val onSelect: (Long) -> Unit) {
    val active: Person? get() = people.firstOrNull { it.id == activeId } ?: people.firstOrNull { it.isMe }
}

/** The switcher placed at the end of a header row. Draws nothing when there is only one person. */
@Composable
fun PeopleBarSwitcher(bar: PeopleBar?, modifier: Modifier = Modifier) {
    if (bar == null || bar.people.size < 2) return
    PeopleSwitcher(rememberChips(bar.people), bar.active?.id, bar.onSelect, modifier)
}

/** "You are viewing Mom's medicines": shown on Today when the person on screen is not me. */
@Composable
fun ViewingBanner(bar: PeopleBar?, modifier: Modifier = Modifier) {
    val p = bar?.active ?: return
    if (p.isMe) return
    val c = PillBox.colors
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(
            stringResource(if (p.ring) R.string.viewing_banner else R.string.viewing_banner_off, p.name),
            style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f),
        )
    }
}
