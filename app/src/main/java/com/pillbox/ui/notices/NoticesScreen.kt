package com.pillbox.ui.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.Notice
import com.pillbox.domain.NoticeKind
import com.pillbox.domain.NoticeRules
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.DesignIcons
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.DangerButton
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.settings.SubHeader
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private fun kindName(k: NoticeKind?) = when (k) {
    null -> R.string.notices_tab_all
    NoticeKind.DOSE -> R.string.notices_tab_doses
    NoticeKind.STOCK -> R.string.notices_tab_stock
    NoticeKind.EXPIRY -> R.string.notices_tab_expiry
    NoticeKind.APP -> R.string.notices_tab_app
}

private fun kindIcon(k: NoticeKind) = when (k) {
    NoticeKind.DOSE -> DesignIcons.Pill
    NoticeKind.STOCK -> DesignIcons.Warning
    NoticeKind.EXPIRY -> DesignIcons.Calendar
    NoticeKind.APP -> DesignIcons.Info
}

/**
 * Everything the app told the user, in tabs by type. New ones are bold with a "New" label (never color alone).
 * Tapping one opens what it is about. "Clear" always asks first.
 */
@Composable
fun NoticesScreen(
    notices: List<Notice>,
    onBack: () -> Unit,
    onOpen: (Notice) -> Unit,
    onClear: (NoticeKind?) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: NoticeKind? = null,
    now: LocalDateTime = LocalDateTime.now(),
) {
    val c = PillBox.colors
    val fmt = rememberFormatter()
    var tab by rememberSaveable { mutableStateOf(initialTab?.name) }
    val kind = tab?.let { NoticeKind.valueOf(it) }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = NoticeRules.ofKind(notices, kind)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.notices_title), onBack)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = Dimens.ScreenPaddingH),
                horizontalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                (listOf<NoticeKind?>(null) + NoticeKind.entries).forEach { k ->
                    val n = NoticeRules.unread(notices, k)
                    val name = stringResource(kindName(k))
                    PBChip(if (n > 0) "$name ${fmt.number(n)}" else name, selected = k == kind, onClick = { tab = k?.name })
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.notices_empty), style = MaterialTheme.typography.bodyLarge, color = c.textMuted, modifier = Modifier.padding(top = Space.S20))
                } else {
                    shown.forEach { NoticeRow(it, fmt, now, onClick = { onOpen(it) }) }
                    Text(stringResource(R.string.notices_kept), style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(top = Space.S4))
                }
            }
            if (shown.isNotEmpty()) {
                Box(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S8)) {
                    OutlineButton(stringResource(if (kind == null) R.string.notices_clear_all else R.string.notices_clear_tab), { confirmClear = true })
                }
            }
        }
    }
    if (confirmClear) {
        MessageDialog(
            DialogKind.Danger,
            stringResource(if (kind == null) R.string.notices_clear_all_title else R.string.notices_clear_tab_title),
            stringResource(R.string.notices_clear_body),
            onDismiss = { confirmClear = false },
        ) {
            DangerButton(stringResource(R.string.notices_clear_yes), { confirmClear = false; onClear(kind) })
            LinkButton(stringResource(R.string.cancel), { confirmClear = false })
        }
    }
}

@Composable
private fun NoticeRow(n: Notice, fmt: Formatter, now: LocalDateTime, onClick: () -> Unit) {
    val c = PillBox.colors
    val whenText = whenText(n.atMillis, fmt, now.toLocalDate())
    val newLabel = stringResource(R.string.notices_new)
    val desc = (if (n.read) "" else "$newLabel. ") + n.title + ". " + n.text + ". " + whenText
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ListRowMinHeight)
            .background(if (n.read) c.card else c.primaryContainer, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(c.card, CircleShape), contentAlignment = Alignment.Center) {
            Icon(kindIcon(n.kind), contentDescription = null, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S2)) {
            Text(n.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold, color = c.text)
            Text(n.text, style = MaterialTheme.typography.bodyMedium, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.CenterVertically) {
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                if (!n.read) Text(newLabel, style = MaterialTheme.typography.labelMedium, color = c.accent)
            }
        }
        Icon(DesignIcons.Chevron, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

/** "Today 8:00 AM", "Yesterday 8:00 AM" or "Mon 19 Oct 8:00 AM". */
@Composable
private fun whenText(atMillis: Long, fmt: Formatter, today: LocalDate): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val day = when (at.toLocalDate()) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.notices_yesterday)
        else -> com.pillbox.ui.shortDate(at.toLocalDate(), fmt)
    }
    return day + " " + fmt.time(at.toLocalTime())
}

/** The bell on Today: opens the notifications screen. A number says how many are new; the words say it too. */
@Composable
fun NoticeBell(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = PillBox.colors
    val fmt = rememberFormatter()
    val desc = if (unread > 0) pluralStringResource(R.plurals.notices_bell_new, unread, fmt.number(unread)) else stringResource(R.string.notices_bell)
    Box(modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Icon(DesignIcons.Bell, contentDescription = null, tint = c.text, modifier = Modifier.size(28.dp))
        if (unread > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).heightIn(min = 20.dp).background(c.danger, CircleShape).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (unread > 99) fmt.number(99) + "+" else fmt.number(unread), style = MaterialTheme.typography.labelSmall, color = c.onPrimary, maxLines = 1) }
        }
    }
}
