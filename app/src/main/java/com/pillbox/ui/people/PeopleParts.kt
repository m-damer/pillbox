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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import com.pillbox.ui.rememberFormatter
import androidx.compose.runtime.setValue
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
        if (initials.isEmpty()) Icon(com.pillbox.ui.components.DesignIcons.Person, contentDescription = null, tint = fg, modifier = Modifier.size(size / 2))
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
data class PeopleBar(val people: List<Person>, val activeId: Long?, val onSelect: (Long) -> Unit, val unread: Map<Long, Int> = emptyMap()) {
    val active: Person? get() = people.firstOrNull { it.id == activeId } ?: people.firstOrNull { it.isMe }
}

/**
 * The switcher placed at the end of a header row: one pill with the name of the person on screen ("Me" for the owner)
 * and a small arrow. It opens a list of everyone, so it takes the same room with 2 people or 20. Draws nothing for one person.
 */
@Composable
fun PeopleBarSwitcher(bar: PeopleBar?, modifier: Modifier = Modifier) {
    if (bar == null || bar.people.size < 2) return
    val chips = rememberChips(bar.people)
    val active = chips.firstOrNull { it.person.id == bar.active?.id } ?: chips.first()
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // The red number on the pill counts new notifications of the people NOT on screen; the bell counts everyone.
    val others = bar.unread.filterKeys { it != active.person.id }.values.sum()
    PersonPill(active.label, active.person.isMe, active.person.colorIndex, others, onClick = { open = true }, modifier = modifier)
    if (open) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }) {
            PersonPickerCard(chips, active.person.id, bar.unread, onPick = { open = false; bar.onSelect(it) }, onCancel = { open = false })
        }
    }
}

/** The pill: the name of the person on screen and a down arrow. At large text the name wraps instead of being cut. */
@Composable
internal fun PersonPill(label: String, isMe: Boolean, colorIndex: Int, unreadElsewhere: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val bg = if (isMe) c.primary else PillBox.illustration.avatars[PeopleRules.colorFor(colorIndex)]
    val fg = if (isMe) c.onPrimary else c.accent
    val base = stringResource(R.string.switcher_pill_a11y, label)
    val desc = if (unreadElsewhere > 0) base + " " + pluralStringResource(R.plurals.switcher_unread_elsewhere, unreadElsewhere, rememberFormatter().number(unreadElsewhere)) else base
    Box(modifier.widthIn(max = 176.dp)) {
    Row(
        Modifier
            .padding(top = 6.dp, end = 6.dp)
            .heightIn(min = Dimens.TouchTargetMin)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(bg)
            .border(BorderStroke(2.dp, c.primary), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(start = Space.S16, end = Space.S8, top = Space.S6, bottom = Space.S6),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S4),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(com.pillbox.ui.components.DesignIcons.ChevronDown, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
    if (unreadElsewhere > 0) CountBadge(unreadElsewhere, Modifier.align(Alignment.TopEnd))
    }
}

/** The red circle with a number. The number is always written (never color alone); 99+ when large. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val fmt = rememberFormatter()
    Box(modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).background(c.danger, CircleShape).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) fmt.number(99) + "+" else fmt.number(count), style = MaterialTheme.typography.labelSmall, color = c.onPrimary, maxLines = 1)
    }
}

/** The popup list of everyone. One tap switches and closes. The person on screen has a check mark and is read as "showing now". */
@Composable
internal fun PersonPickerCard(chips: List<PersonChip>, activeId: Long, unread: Map<Long, Int>, onPick: (Long) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.8f).dp
    val selectedText = stringResource(R.string.switcher_selected)
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().heightIn(max = maxHeight).background(c.card, MaterialTheme.shapes.extraLarge).padding(vertical = Space.S16),
    ) {
        Text(
            stringResource(R.string.switcher_title), style = MaterialTheme.typography.titleMedium, color = c.text,
            modifier = Modifier.padding(horizontal = Space.S16).padding(bottom = Space.S8).semantics { heading() },
        )
        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup().padding(horizontal = Space.S8),
        ) {
            chips.forEach { chip ->
                val on = chip.person.id == activeId
                val n = unread[chip.person.id] ?: 0
                val newText = if (n > 0) pluralStringResource(R.plurals.switcher_unread, n, rememberFormatter().number(n)) else null
                val desc = listOfNotNull(chip.label, if (on) selectedText else null, newText).joinToString(", ")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ListRowMinHeight)
                        .background(if (on) c.primaryContainer else c.card, MaterialTheme.shapes.medium)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(chip.person.id) })
                        .semantics { contentDescription = desc }
                        .padding(horizontal = Space.S8, vertical = Space.S6),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Avatar(chip.initials, chip.person.colorIndex, chip.person.isMe, size = 40.dp)
                    Text(chip.label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                    if (on) Icon(com.pillbox.ui.components.DesignIcons.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
                    if (n > 0) CountBadge(n)
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.S16).padding(top = Space.S8)) {
            com.pillbox.ui.components.LinkButton(stringResource(R.string.cancel), onCancel)
        }
    }
}

/** "You're viewing Mom's medicines": shown on Today when the person on screen is not me. Short on purpose; only the name is bold. */
@Composable
fun ViewingBanner(bar: PeopleBar?, modifier: Modifier = Modifier) {
    val p = bar?.active ?: return
    if (p.isMe) return
    val c = PillBox.colors
    val marker = "\u0001"
    val full = stringResource(if (p.ring) R.string.viewing_banner else R.string.viewing_banner_off, marker)
    val at = full.indexOf(marker)
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(full.substring(0, at))
        // The name is its own left-to-right or right-to-left unit, so an Arabic name in an English sentence (or the reverse) keeps its order.
        append("\u2068")
        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        append(p.name)
        pop()
        append("\u2069")
        append(full.substring(at + marker.length))
    }
    Row(
        modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(horizontal = Space.S12, vertical = Space.S8),
        horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(com.pillbox.ui.components.DesignIcons.Person, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
    }
}
