package com.pillbox.ui.report

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillbox.R
import com.pillbox.domain.ReportOptions
import com.pillbox.ui.Formatter
import com.pillbox.ui.components.OutlineButton
import com.pillbox.ui.components.PrimaryButton
import com.pillbox.ui.components.Segmented
import com.pillbox.ui.components.SwitchRow
import com.pillbox.ui.components.ToastHost
import com.pillbox.ui.components.showToast
import com.pillbox.ui.rememberFormatter
import com.pillbox.ui.theme.Dimens
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space

/** Doctor report: choose the period and what to include, preview the PDF, then share it or save it. */
@Composable
fun ReportScreen(vm: ReportViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val savedTitle = stringResource(R.string.report_saved_title)
    val chooser = stringResource(R.string.report_share_chooser)

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.saveTo(uri, savedTitle)
    }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showToast(m.title, m.body)
        vm.messageShown()
    }
    BackHandler(enabled = state.preview != null) { vm.closePreview() }

    val preview = state.preview
    if (preview != null) {
        ReportPreview(preview, onBack = vm::closePreview)
        return
    }
    ReportContent(
        state, fmt, snackbar, onBack = onBack,
        onPeriod = vm::setPeriod, onMedicines = vm::setMedicines, onDayByDay = vm::setDayByDay, onMissedLate = vm::setMissedLate,
        onPreview = vm::openPreview,
        onShare = {
            vm.share { uri, name ->
                val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri(name, uri)
                context.startActivity(Intent.createChooser(send, chooser))
            }
        },
        onSave = { saveLauncher.launch(vm.fileName()) },
    )
}

@Composable
fun ReportContent(
    state: ReportUiState,
    fmt: Formatter,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPeriod: (Int) -> Unit,
    onMedicines: (Boolean) -> Unit,
    onDayByDay: (Boolean) -> Unit,
    onMissedLate: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val c = PillBox.colors
    val o = state.options
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text)
            }
            Column(Modifier.weight(1f).padding(vertical = Space.S8)) {
                Text(stringResource(R.string.report_screen_title), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.semantics { heading() })
                if (state.patient.isNotEmpty()) Text(stringResource(R.string.report_for, state.patient), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            Label(stringResource(R.string.report_period_label))
            Segmented(
                options = ReportOptions.PERIODS.map { pluralStringResource(R.plurals.n_days, it, fmt.number(it)) },
                selectedIndex = ReportOptions.PERIODS.indexOf(o.days).coerceAtLeast(0),
                onSelect = { onPeriod(ReportOptions.PERIODS[it]) },
            )
            Label(stringResource(R.string.report_include))
            Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large)) {
                SwitchRow(stringResource(R.string.report_opt_meds), o.medicines, onMedicines)
                Divider()
                SwitchRow(stringResource(R.string.report_opt_days), o.dayByDay, onDayByDay)
                Divider()
                SwitchRow(stringResource(R.string.report_opt_missed), o.missedLate, onMissedLate, subtitle = stringResource(R.string.report_opt_missed_sub))
            }
            PreviewRow(state.pages, pluralStringResource(R.plurals.n_days, o.days, fmt.number(o.days)), fmt, onPreview)
            Row(
                Modifier.fillMaxWidth().background(c.warningContainer, MaterialTheme.shapes.medium).padding(Space.S16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = c.warning, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.report_warning), style = MaterialTheme.typography.bodyMedium, color = c.warning, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.size(Space.S8))
        }
        Column(
            Modifier.padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingBottom),
            verticalArrangement = Arrangement.spacedBy(Space.S10),
        ) {
            PrimaryButton(stringResource(R.string.report_share), onShare)
            OutlineButton(stringResource(R.string.report_save), onSave)
        }
    }
    // The toast sits just above the two buttons, as in the design.
    ToastHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 124.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = PillBox.colors.textMuted,
        modifier = Modifier.padding(start = Space.S8, top = Space.S4).semantics { heading() },
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PillBox.colors.divider))
}

@Composable
private fun PreviewRow(pages: Int?, period: String, fmt: Formatter, onClick: () -> Unit) {
    val c = PillBox.colors
    val title = stringResource(R.string.report_preview)
    val sub = if (pages == null) stringResource(R.string.report_preview_making) else pluralStringResource(R.plurals.report_preview_sub, pages, fmt.number(pages), period)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, MaterialTheme.shapes.large)
            .clickable(enabled = pages != null, role = Role.Button, onClick = onClick)
            .padding(Space.S16),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S16),
    ) {
        PageThumbnail()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.textMuted)
    }
}

/** A tiny drawn page: a title bar and a few lines. */
@Composable
private fun PageThumbnail() {
    val c = PillBox.colors
    val line = c.border
    val accent = c.primary
    Canvas(
        Modifier.size(width = 52.dp, height = 68.dp).background(c.card, RoundedCornerShape(6.dp)).border(2.dp, c.border, RoundedCornerShape(6.dp)),
    ) {
        val pad = 10.dp.toPx()
        drawRoundRect(accent, Offset(pad, pad), Size(size.width * 0.35f, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        for (i in 0 until 6) {
            val w = if (i == 2 || i == 4) size.width * 0.4f else size.width - pad * 2
            drawRoundRect(line, Offset(pad, pad + 12.dp.toPx() + i * 7.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        }
    }
}

/** The finished PDF, page by page, as pictures. */
@Composable
private fun ReportPreview(pages: List<android.graphics.Bitmap>, onBack: () -> Unit) {
    val c = PillBox.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.S8), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Dimens.TouchTargetMin).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = c.text)
            }
            Text(
                stringResource(R.string.report_preview_title), style = MaterialTheme.typography.titleLarge, color = c.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        val fmt = rememberFormatter()
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.ScreenPaddingH), verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            itemsIndexed(pages) { i, bmp ->
                val label = stringResource(R.string.report_preview_page, fmt.number(i + 1), fmt.number(pages.size))
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = label, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().border(1.dp, c.border),
                )
            }
        }
    }
}
