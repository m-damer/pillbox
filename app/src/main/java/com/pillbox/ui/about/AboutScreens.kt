package com.pillbox.ui.about

import com.pillbox.ui.components.stringResourceIso
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pillbox.R
import kotlinx.coroutines.launch
import com.pillbox.domain.Contact
import com.pillbox.domain.EmailCheck
import com.pillbox.domain.FeedbackKind
import com.pillbox.domain.FeedbackMail
import com.pillbox.domain.Mail
import com.pillbox.ui.addmed.fieldColors
import com.pillbox.ui.components.DialogKind
import com.pillbox.ui.components.MessageDialog
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PBChip
import com.pillbox.ui.components.ChipGroup
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.LinkButton
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.settings.Caption
import com.pillbox.ui.settings.Card
import com.pillbox.ui.settings.Note
import com.pillbox.ui.settings.SubHeader
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

// ---------------------------------------------------------------- helpers

/** A star drawn as an outline. An empty star must differ from a filled one by shape, not only by color. */
private val StarOutline: ImageVector by lazy {
    ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 17.27f); lineTo(18.18f, 21f); lineToRelative(-1.64f, -7.03f); lineTo(22f, 9.24f)
            lineToRelative(-7.19f, -0.61f); lineTo(12f, 2f); lineTo(9.19f, 8.63f); lineTo(2f, 9.24f)
            lineToRelative(5.46f, 4.73f); lineTo(5.82f, 21f); close()
        }
    }.build()
}

/** Opens the person's email app with the message ready. Returns false when there is no email app. Pill Box never sends anything itself. */
fun openEmail(context: Context, mail: Mail): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(mail.to))
        putExtra(Intent.EXTRA_SUBJECT, mail.subject)
        putExtra(Intent.EXTRA_TEXT, mail.body)
    }
    return try { context.startActivity(intent); true } catch (e: ActivityNotFoundException) { false }
}

fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

fun appInfo(context: Context): String = FeedbackMail.appInfo(appVersion(context), "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE)

private fun shareApp(context: Context, text: String, chooser: String) {
    val body = if (Contact.STORE_URL.isNotEmpty()) "$text\n${Contact.STORE_URL}" else text
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }
    runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
}

// ---------------------------------------------------------------- Privacy and about

@Composable
fun AboutScreen(thanks: Int, onBack: () -> Unit, onPrivacy: () -> Unit, onTerms: () -> Unit, onFeedback: () -> Unit, onRate: () -> Unit) {
    val host = remember { SnackbarHostState() }
    val title = stringResource(R.string.ab_thanks_title)
    val body = stringResource(R.string.ab_thanks_body)
    LaunchedEffect(thanks) { if (thanks > 0) host.showToast(title, body) }
    AboutContent(rememberFormatter().digits(appVersion(LocalContext.current)), host, onBack, onPrivacy, onTerms, onFeedback, onRate)
}

@Composable
fun AboutContent(version: String, host: SnackbarHostState, onBack: () -> Unit, onPrivacy: () -> Unit, onTerms: () -> Unit, onFeedback: () -> Unit, onRate: () -> Unit) {
    val c = PillBox.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            SubHeader(stringResource(R.string.ab_title), onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    Box(Modifier.size(Dimens.LogoSmall).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primary)) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = Space.S8))
                    Text(stringResourceIso(R.string.ab_version, version), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                Row(
                    Modifier.fillMaxWidth().background(c.primaryContainer, MaterialTheme.shapes.large).padding(Space.S16),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.ab_privacy_short), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                }
                Card {
                    AboutRow(stringResource(R.string.ab_privacy), Icons.Filled.Lock, onPrivacy)
                    Divider()
                    AboutRow(stringResource(R.string.ab_terms), Icons.Filled.Info, onTerms)
                    Divider()
                    AboutRow(stringResource(R.string.ab_feedback), Icons.Filled.Email, onFeedback)
                    Divider()
                    AboutRow(stringResource(R.string.ab_rate), StarOutline, onRate)
                }
                Text(
                    stringResource(R.string.ab_disclaimer), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.S16),
                )
            }
        }
        ToastHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = Space.S16).heightIn(min = 1.dp).background(PillBox.colors.divider))
}

@Composable
private fun AboutRow(title: String, icon: ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    val c = PillBox.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.SettingsRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(horizontal = Space.S12, vertical = Space.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
    ) {
        Box(Modifier.size(Dimens.TouchTargetMin).background(if (danger) c.dangerContainer else c.primaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (danger) c.danger else c.accent)
        }
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (danger) c.danger else c.text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

// ---------------------------------------------------------------- Privacy policy and Terms

private class Block(val icon: ImageVector, val title: Int, val body: Int, val container: @Composable () -> androidx.compose.ui.graphics.Color, val tint: @Composable () -> androidx.compose.ui.graphics.Color)

@Composable
private fun LegalPage(title: String, updated: String, blocks: List<Block>, onBack: () -> Unit, bottom: @Composable () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(title, onBack)
        Text(updated, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.padding(horizontal = Dimens.ScreenPaddingH + Space.S8))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH).padding(top = Space.S8)) {
            Card {
                blocks.forEachIndexed { i, b ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(Space.S12).semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.size(Dimens.TouchTargetMin).background(b.container(), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                            Icon(b.icon, contentDescription = null, tint = b.tint())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                            Text(stringResource(b.title), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.semantics { heading() })
                            Text(stringResource(b.body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S8)) { bottom() }
    }
}

@Composable
fun PrivacyPolicyContent(onBack: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit, showDelete: Boolean = true) {
    val c = PillBox.colors
    val blocks = listOf(
        Block(Icons.Filled.Lock, R.string.pp_1_title, R.string.pp_1_body, { c.primaryContainer }, { c.accent }),
        Block(Icons.Filled.Share, R.string.pp_2_title, R.string.pp_2_body, { c.successContainer }, { c.success }),
        Block(Icons.Filled.Person, R.string.pp_3_title, R.string.pp_3_body, { c.primaryContainer }, { c.accent }),
        Block(Icons.Filled.Check, R.string.pp_4_title, R.string.pp_4_body, { c.successContainer }, { c.success }),
    )
    LegalPage(stringResource(R.string.pp_title), stringResource(R.string.legal_updated), blocks, onBack) {
        if (showDelete) Card { AboutRow(stringResource(R.string.bk_delete), Icons.Filled.Delete, onDelete, danger = true) }
        OutlineButton(stringResource(R.string.pp_close), onClose)
    }
}

@Composable
fun TermsContent(onBack: () -> Unit, onDone: () -> Unit) {
    val c = PillBox.colors
    val blocks = listOf(
        Block(Icons.Filled.Info, R.string.tm_1_title, R.string.tm_1_body, { c.primaryContainer }, { c.accent }),
        Block(Icons.Filled.Warning, R.string.tm_2_title, R.string.tm_2_body, { c.warningContainer }, { c.warning }),
        Block(Icons.Filled.Check, R.string.tm_3_title, R.string.tm_3_body, { c.successContainer }, { c.success }),
        Block(Icons.Filled.Person, R.string.tm_4_title, R.string.tm_4_body, { c.primaryContainer }, { c.accent }),
    )
    LegalPage(stringResource(R.string.tm_title), stringResource(R.string.legal_updated), blocks, onBack) {
        PrimaryButton(stringResource(R.string.tm_ok), onDone)
    }
}

// ---------------------------------------------------------------- Send feedback

private fun kindLabel(k: FeedbackKind) = when (k) {
    FeedbackKind.PROBLEM -> "A problem"
    FeedbackKind.IDEA -> "An idea"
    FeedbackKind.QUESTION -> "A question"
    FeedbackKind.LIKE -> "Something I like"
}

@Composable
fun FeedbackScreen(onBack: () -> Unit, onOpened: () -> Unit) {
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var includeInfo by rememberSaveable { mutableStateOf(true) }
    var check by rememberSaveable { mutableStateOf(EmailCheck.OK) }
    val noMailTitle = stringResource(R.string.fb_no_mail_title)
    val noMailBody = stringResourceIso(R.string.fb_no_mail_body, Contact.DEVELOPER_EMAIL)
    Box(Modifier.fillMaxSize()) {
        FeedbackContent(
            kind, { kind = it }, message, { message = it }, email, { email = it; check = EmailCheck.OK }, phone, { phone = it },
            includeInfo, { includeInfo = it }, check, onBack,
            onSend = {
                val result = FeedbackMail.checkEmail(email)
                check = result
                if (result == EmailCheck.OK) {
                    val mail = FeedbackMail.feedback(kindLabel(FeedbackKind.entries[kind]), message, email, phone, if (includeInfo) appInfo(context) else null)
                    if (openEmail(context, mail)) onOpened() else scope.launchToast(host, noMailTitle, noMailBody)
                }
            },
        )
        ToastHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchToast(host: SnackbarHostState, title: String, body: String) {
    launch { host.showToast(title, body) }
}

@Composable
fun FeedbackContent(
    kind: Int, onKind: (Int) -> Unit, message: String, onMessage: (String) -> Unit, email: String, onEmail: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit, includeInfo: Boolean, onInfo: (Boolean) -> Unit, emailCheck: EmailCheck,
    onBack: () -> Unit, onSend: () -> Unit,
) {
    val c = PillBox.colors
    val labels = listOf(R.string.fb_problem, R.string.fb_idea, R.string.fb_question, R.string.fb_like)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.fb_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            Caption(stringResource(R.string.fb_about))
            ChipGroup { labels.forEachIndexed { i, l -> PBChip(stringResource(l), kind == i, { onKind(i) }) } }
            Caption(stringResource(R.string.fb_message))
            OutlinedTextField(
                value = message, onValueChange = onMessage, textStyle = MaterialTheme.typography.bodyLarge, shape = MaterialTheme.shapes.large, colors = fieldColors(),
                placeholder = { Text(stringResource(R.string.fb_message_hint), style = MaterialTheme.typography.bodyLarge) },
                minLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton),
            )
            Caption(stringResource(R.string.fb_reach))
            OutlinedTextField(
                value = email, onValueChange = onEmail, textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, shape = MaterialTheme.shapes.large, colors = fieldColors(),
                isError = emailCheck != EmailCheck.OK,
                placeholder = { Text(stringResource(R.string.fb_email), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton),
            )
            OutlinedTextField(
                value = phone, onValueChange = onPhone, textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, shape = MaterialTheme.shapes.large, colors = fieldColors(),
                placeholder = { Text(stringResource(R.string.fb_phone), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.StepperButton),
            )
            if (emailCheck != EmailCheck.OK) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S8), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(if (emailCheck == EmailCheck.EMPTY) R.string.fb_email_missing else R.string.fb_email_invalid),
                        style = MaterialTheme.typography.bodyMedium, color = c.danger,
                    )
                }
            }
            Text(stringResource(R.string.fb_only_reply), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Card { SwitchRow(stringResource(R.string.fb_info), includeInfo, onInfo, subtitle = stringResource(R.string.fb_info_sub)) }
            Note(stringResource(R.string.fb_note))
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
            PrimaryButton(stringResource(R.string.fb_open_mail), onSend)
            LinkButton(stringResource(R.string.bk_cancel), onBack)
        }
    }
}

// ---------------------------------------------------------------- Rate the app

@Composable
fun RateScreen(onBack: () -> Unit, onOpened: () -> Unit, onWriteFeedback: () -> Unit) {
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var stars by rememberSaveable { mutableIntStateOf(0) }
    val noMailTitle = stringResource(R.string.fb_no_mail_title)
    val noMailBody = stringResourceIso(R.string.fb_no_mail_body, Contact.DEVELOPER_EMAIL)
    val shareText = stringResource(R.string.rt_share_text)
    val shareChooser = stringResource(R.string.rt_share)
    Box(Modifier.fillMaxSize()) {
        RateContent(
            stars, { stars = it }, onBack, onWriteFeedback,
            onSend = { if (openEmail(context, FeedbackMail.rating(stars, appInfo(context)))) onOpened() else scope.launchToast(host, noMailTitle, noMailBody) },
            onShare = { shareApp(context, shareText, shareChooser) },
        )
        ToastHost(host, Modifier.align(Alignment.BottomCenter))
    }
}

private val starLabels = listOf(R.string.rt_1, R.string.rt_2, R.string.rt_3, R.string.rt_4, R.string.rt_5)

@Composable
fun RateContent(stars: Int, onStars: (Int) -> Unit, onBack: () -> Unit, onWriteFeedback: () -> Unit, onSend: () -> Unit, onShare: () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SubHeader(stringResource(R.string.rt_title), onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(Dimens.LogoSmall).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primary)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.rt_question), style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S16).semantics { heading() })
            Text(stringResource(R.string.rt_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.S8))
            Row(Modifier.padding(top = Space.S16).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
                for (i in 1..5) {
                    val label = stringResourceIso(R.string.rt_star_a11y, i, stringResource(starLabels[i - 1]))
                    Box(
                        Modifier.size(Dimens.TouchTargetMin + Space.S4).selectable(selected = stars == i, role = Role.RadioButton, onClick = { onStars(i) }).semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (i <= stars) Icons.Filled.Star else StarOutline, contentDescription = null, tint = if (i <= stars) c.star else c.textMuted, modifier = Modifier.size(Dimens.StarSize))
                    }
                }
            }
            Text(
                if (stars > 0) stringResource(starLabels[stars - 1]) else stringResource(R.string.rt_tap),
                style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.padding(top = Space.S8),
            )
            LinkButton(stringResource(R.string.rt_more), onWriteFeedback, modifier = Modifier.padding(top = Space.S12))
        }
        Column(Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Space.S12), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.rt_open_mail), onSend, enabled = stars > 0)
            OutlineButton(stringResource(R.string.rt_share), onShare)
            LinkButton(stringResource(R.string.rt_not_now), onBack)
        }
    }
}

// ---------------------------------------------------------------- Crash report prompt

@Composable
fun CrashReportDialog(onSend: () -> Unit, onSkip: () -> Unit) {
    MessageDialog(DialogKind.Warning, stringResource(R.string.cr_title), stringResource(R.string.cr_body), onDismiss = onSkip, icon = Icons.Filled.Warning) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
            PrimaryButton(stringResource(R.string.cr_open_mail), onSend)
            OutlineButton(stringResource(R.string.cr_skip), onSkip)
        }
    }
}
