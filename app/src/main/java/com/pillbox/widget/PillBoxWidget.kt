package com.pillbox.widget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as ColorProviderType
import com.pillbox.MainActivity
import com.pillbox.PillBoxApp
import com.pillbox.R
import com.pillbox.domain.DoseRow
import com.pillbox.domain.WidgetBuilder
import com.pillbox.domain.WidgetModel
import com.pillbox.domain.WidgetSettings
import com.pillbox.domain.WidgetSize
import com.pillbox.domain.WithFood
import com.pillbox.ui.Formatter
import com.pillbox.ui.quantityFor
import com.pillbox.ui.theme.DarkColors
import com.pillbox.ui.theme.LightColors
import com.pillbox.ui.unitPlural
import java.time.LocalDateTime

/** Loads what the widget shows, from the database right now. */
object WidgetData {
    suspend fun load(app: PillBoxApp, settings: WidgetSettings, now: LocalDateTime): WidgetModel {
        val repo = app.repository
        val items = repo.allActiveWithSchedules().mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
        val day = now.toLocalDate().atStartOfDay()
        val events = repo.allDoseEventsFor(day, day.plusDays(1))
        return WidgetBuilder.build(items, events, now, settings, repo.myPersonId(), app.reminderSettings.current().lateThreshold)
    }
}

/** The home screen widget: the next dose, its medicines, and Take all. */
class PillBoxWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PillBoxApp
        val settings = app.widgetSettings.current()
        val now = LocalDateTime.now()
        val model = runCatching { WidgetData.load(app, settings, now) }.getOrNull()
        provideContent { WidgetContent(settings, model, Formatter.from(context)) }
    }

    companion object {
        /** Redraws every widget. Called after doses change and when the alarm re-arms. */
        suspend fun refresh(context: Context) { runCatching { PillBoxWidget().updateAll(context.applicationContext) } }
    }
}

class PillBoxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillBoxWidget()
}

/** "Take all" from the widget: marks the doses shown as next as taken, shows a short message, and redraws. */
class TakeAllCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as PillBoxApp
        val now = LocalDateTime.now()
        val model = WidgetData.load(app, app.widgetSettings.current(), now)
        val at = model.at
        if (at != null && model.nextRows.isNotEmpty()) {
            app.reminders.take(model.nextRows)
            app.reminders.dismiss(at)
            app.reminders.rearm()
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, R.string.w_taken, Toast.LENGTH_SHORT).show() }
        }
        PillBoxWidget.refresh(context)
    }
}

// ---------------------------------------------------------------- drawing

private data class Palette(
    val card: ColorProviderType, val tile: ColorProviderType, val text: ColorProviderType, val muted: ColorProviderType,
    val primary: ColorProviderType, val onPrimary: ColorProviderType, val late: ColorProviderType,
)

private fun palette() = Palette(
    card = ColorProvider(LightColors.card, DarkColors.card),
    tile = ColorProvider(LightColors.background, DarkColors.background),
    text = ColorProvider(LightColors.text, DarkColors.text),
    muted = ColorProvider(LightColors.textMuted, DarkColors.textMuted),
    primary = ColorProvider(LightColors.primary, DarkColors.primary),
    onPrimary = ColorProvider(LightColors.onPrimary, DarkColors.onPrimary),
    late = ColorProvider(LightColors.danger, DarkColors.danger),
)

@Composable
private fun WidgetContent(settings: WidgetSettings, model: WidgetModel?, fmt: Formatter) {
    val context = LocalContext.current
    val p = palette()
    val size = LocalSize.current
    val open = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(p.card).cornerRadius(24.dp).padding(12.dp).clickable(open),
    ) {
        if (model == null) {
            Text(context.getString(R.string.w_error), style = TextStyle(color = p.text, fontSize = 16.sp))
            return@Column
        }
        Header(p, model, fmt)
        if (model.at == null) {
            Spacer(GlanceModifier.height(6.dp))
            Empty(p, model, fmt)
            return@Column
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(
            fmt.time(model.at.toLocalTime()),
            style = TextStyle(color = p.text, fontSize = if (settings.size == WidgetSize.SMALL) 30.sp else 28.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.semantics { contentDescription = context.getString(R.string.w_next_a11y, fmt.time(model.at.toLocalTime())) },
        )
        val showTake = settings.takeAll && model.nextRows.isNotEmpty()
        val wanted = settings.size.maxRows
        if (wanted > 0) {
            val fit = WidgetBuilder.rowsThatFit(size.height.value, showTake, wanted)
            val shown = model.rows.take(fit)
            Spacer(GlanceModifier.height(6.dp))
            shown.forEach { row -> DoseLine(p, row, settings, fmt, showTime = row.planned != model.at); Spacer(GlanceModifier.height(6.dp)) }
            val more = model.rows.size - shown.size
            if (more > 0) Text(context.resources.getQuantityString(R.plurals.w_more, more, fmt.number(more)), style = TextStyle(color = p.muted, fontSize = 14.sp))
        } else if (settings.showNames) {
            Text(context.resources.getQuantityString(R.plurals.w_n_medicines, model.nextRows.size, fmt.number(model.nextRows.size)), style = TextStyle(color = p.muted, fontSize = 14.sp))
        }
        if (showTake) {
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(48.dp).background(p.primary).cornerRadius(24.dp)
                    .clickable(actionRunCallback<TakeAllCallback>(actionParametersOf())),
                contentAlignment = Alignment.Center,
            ) { Text(context.getString(R.string.w_take_all), style = TextStyle(color = p.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)) }
        }
    }
}

@Composable
private fun Header(p: Palette, model: WidgetModel, fmt: Formatter) {
    val context = LocalContext.current
    val label = when {
        model.at == null -> context.getString(R.string.w_title)
        model.late -> context.getString(R.string.w_late_title)
        else -> context.getString(R.string.w_title)
    }
    val whenText = when {
        model.at == null -> ""
        model.late -> context.getString(R.string.w_late_by, durationText(context, fmt, model.minutes))
        model.minutes <= 0 -> context.getString(R.string.w_now)
        else -> context.getString(R.string.w_in, durationText(context, fmt, model.minutes))
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(color = if (model.late) p.late else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.defaultWeight())
        if (whenText.isNotEmpty()) Text(whenText, style = TextStyle(color = if (model.late) p.late else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun Empty(p: Palette, model: WidgetModel, fmt: Formatter) {
    val context = LocalContext.current
    val title = if (model.allDone) R.string.w_done else R.string.w_none
    Text(context.getString(title), style = TextStyle(color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold))
    model.following?.let { f ->
        val whenText = if (f.toLocalDate() == LocalDateTime.now().toLocalDate().plusDays(1)) context.getString(R.string.w_next_tomorrow, fmt.time(f.toLocalTime()))
        else context.getString(R.string.w_next_at, fmt.time(f.toLocalTime()))
        Text(whenText, style = TextStyle(color = p.muted, fontSize = 14.sp))
    }
}

@Composable
private fun DoseLine(p: Palette, row: DoseRow, settings: WidgetSettings, fmt: Formatter, showTime: Boolean) {
    val context = LocalContext.current
    val med = row.medicine
    val amount = fmt.amount(med.doseAmount) + " " + context.resources.getQuantityString(unitPlural(med.unit), quantityFor(med.doseAmount))
    val food = when (row.schedule.withFood) {
        WithFood.ANY -> null
        WithFood.BEFORE_MEAL -> context.getString(R.string.food_line_before)
        WithFood.WITH_MEAL -> context.getString(R.string.food_line_with)
        WithFood.AFTER_MEAL -> context.getString(R.string.food_line_after)
    }
    val sep = context.getString(R.string.dot_sep)
    val time = if (showTime) fmt.time(row.planned.toLocalTime()) else null
    val title = if (settings.showNames) med.name else amount
    val detail = (if (settings.showNames) listOfNotNull(time, amount, food) else listOfNotNull(time, food)).joinToString(sep)
    Column(
        modifier = GlanceModifier.fillMaxWidth().background(p.tile).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = listOf(title, detail).filter { it.isNotEmpty() }.joinToString(", ") },
    ) {
        Text(title, style = TextStyle(color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (detail.isNotEmpty()) Text(detail, style = TextStyle(color = p.muted, fontSize = 14.sp), maxLines = 1)
    }
}

private fun durationText(context: Context, fmt: Formatter, minutes: Long): String {
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        h == 0 -> context.getString(R.string.w_min, fmt.number(m))
        m == 0 -> context.getString(R.string.w_h, fmt.number(h))
        else -> context.getString(R.string.w_h_min, fmt.number(h), fmt.number(m))
    }
}
