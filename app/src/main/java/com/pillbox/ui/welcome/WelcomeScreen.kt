package com.pillbox.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pillbox.R
import com.pillbox.domain.DoseUnit
import com.pillbox.domain.TextSize
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.RadioRow
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.today.PillIcon

/**
 * First launch: language and text size, then Continue. Terms and Privacy are one tap away; continuing means agreeing to them.
 * Both choices apply at once and can be changed later in Settings.
 */
@Composable
fun WelcomeContent(
    language: String,
    textSize: TextSize,
    onLanguage: (String) -> Unit,
    onTextSize: (TextSize) -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PillBox.colors
    val sizes = listOf(TextSize.STANDARD to R.string.acc_size_standard, TextSize.LARGE to R.string.acc_size_large, TextSize.LARGEST to R.string.acc_size_largest)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingTop),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Box(Modifier.size(Dimens.LogoSmall).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primary)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.wl_title), style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.wl_sub), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
            Section(stringResource(R.string.lang_language))
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                RadioRow(stringResource(R.string.lang_en), language == "en", { onLanguage("en") })
                RadioRow(stringResource(R.string.lang_ar), language == "ar", { onLanguage("ar") })
            }
            Section(stringResource(R.string.acc_text_size))
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
                Segmented(sizes.map { stringResource(it.second) }, sizes.indexOfFirst { it.first == textSize }, { onTextSize(sizes[it].first) }, stackWhenLarge = true)
                Row(
                    Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.medium).padding(Space.S12),
                    horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillIcon(DoseUnit.CAPSULE, size = 32.dp)
                    Text(stringResource(R.string.acc_preview_name), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            Text(stringResource(R.string.wl_agree), style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S16)) {
                LegalLink(stringResource(R.string.ab_terms), onTerms)
                LegalLink(stringResource(R.string.ab_privacy), onPrivacy)
            }
            PrimaryButton(stringResource(R.string.wl_continue), onContinue)
        }
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Space.S8),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = PillBox.colors.accent, textDecoration = TextDecoration.Underline) }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = PillBox.colors.textMuted,
        modifier = Modifier.fillMaxWidth().padding(start = Space.S8, top = Space.S4).semantics { heading() },
    )
}
