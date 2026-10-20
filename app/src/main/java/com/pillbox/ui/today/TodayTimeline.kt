package com.pillbox.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pillbox.domain.DoseGroup
import com.pillbox.domain.GroupState
import com.pillbox.domain.RowStatus
import com.pillbox.domain.TodayModel
import com.pillbox.ui.Block
import com.pillbox.ui.Formatter
import com.pillbox.ui.blockColors
import com.pillbox.ui.blockOf
import com.pillbox.ui.theme.PillBox
import com.pillbox.ui.theme.Space
import com.pillbox.ui.theme.illustration
import kotlin.math.max
import kotlin.math.min

/** Colors of a dose group: its part of the day, grey when done, red when all skipped. Shared by the group card and the timeline callout. */
@Composable
internal fun groupColors(group: DoseGroup): Pair<Color, Color> {
    val c = PillBox.colors
    val allSkipped = group.rows.all { it.status == RowStatus.SKIPPED }
    return when {
        group.state == GroupState.DONE && allSkipped -> c.dangerContainer to c.danger
        group.state == GroupState.DONE || group.state == GroupState.MISSED_DAY -> c.night to c.nightText
        else -> blockColors(blockOf(group.time.toLocalTime()))
    }
}

private enum class Slot { ABOVE, BELOW, BAR, HOURS, HOUR }

private data class SlotData(val slot: Slot, val minute: Int = 0)

private class SlotModifier(val data: SlotData) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = data
}

private fun Modifier.slot(slot: Slot, minute: Int = 0): Modifier = this.then(SlotModifier(SlotData(slot, minute)))

/** A line at [x], from [y1] to [y2], drawn by the timeline after it knows where everything sits. */
private class Connector(val x: Float, val y1: Float, val y2: Float)

/**
 * The day as a bar: parts of the day in token colors, a dot for every dose, and under or over the bar a label with the medicines and the
 * time. Done doses and night doses sit above the bar, the others below. Decorative: the list says the same in words.
 */
@Composable
internal fun DayTimeline(model: TodayModel, fmt: Formatter) {
    val c = PillBox.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stops = PillBox.illustration.dayBar.map { (p, color) -> (if (rtl) 1f - p else p) to color }.sortedBy { it.first }.toTypedArray()
    val ink = c.text; val done = c.success; val ring = c.primary; val white = c.card
    val large = LocalDensity.current.fontScale > 1.3f
    val hours = if (large) listOf(0, 12, 24) else listOf(0, 6, 12, 18, 24)
    val groups = model.groups
    val dots = groups.map { g -> Triple(g.time.hour * 60 + g.time.minute, g.state, g.rows.all { it.status != RowStatus.PENDING }) }
    val connectors = remember { mutableListOf<Connector>() }
    val gap = with(LocalDensity.current) { 6.dp.toPx() }

    Column(Modifier.fillMaxWidth().background(c.card, MaterialTheme.shapes.large).padding(Space.S16).clearAndSetSemantics { }) {
        Layout(
            content = {
                Canvas(Modifier.slot(Slot.BAR).fillMaxWidth().height(32.dp)) {
                    val barH = 18.dp.toPx()
                    val top = (size.height - barH) / 2
                    fun x(min: Int): Float = (min / 1440f * size.width).let { if (rtl) size.width - it else it }
                    drawRoundRect(Brush.horizontalGradient(colorStops = stops), Offset(0f, top), Size(size.width, barH), CornerRadius(barH / 2))
                    for ((min, state, finished) in dots) {
                        val center = Offset(x(min).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()), size.height / 2)
                        when {
                            finished -> drawCircle(done, 8.dp.toPx(), center)
                            state == GroupState.NEXT || state == GroupState.LATE -> {
                                drawCircle(white, 10.dp.toPx(), center); drawCircle(ring, 10.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                            }
                            else -> { drawCircle(white, 7.dp.toPx(), center); drawCircle(ink, 7.dp.toPx(), center, style = Stroke(2.dp.toPx())) }
                        }
                    }
                    // the check mark inside a finished dot
                    for ((min, _, finished) in dots) if (finished) {
                        val cx = x(min).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx()); val cy = size.height / 2
                        val p = Path().apply { moveTo(cx - 3.5.dp.toPx(), cy); lineTo(cx - 1.dp.toPx(), cy + 2.5.dp.toPx()); lineTo(cx + 3.5.dp.toPx(), cy - 2.5.dp.toPx()) }
                        drawPath(p, white, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                hours.forEach { h ->
                    Text(fmt.hour(h), style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1, softWrap = false, modifier = Modifier.slot(Slot.HOUR, h * 60))
                }
                groups.forEach { g ->
                    val (bg, fg) = groupColors(g)
                    val finished = g.rows.all { it.status != RowStatus.PENDING }
                    val above = finished || blockOf(g.time.toLocalTime()) == Block.NIGHT
                    val names = g.rows.map { it.medicine.name }.distinct()
                    val shown = if (names.size > 3) names.take(2) + "+" + fmt.number(names.size - 2) else names
                    val time = (if (finished && g.rows.any { it.status == RowStatus.TAKEN }) "✓ " else "") + fmt.time(g.time.toLocalTime())
                    Column(
                        Modifier.slot(if (above) Slot.ABOVE else Slot.BELOW, g.time.hour * 60 + g.time.minute).background(bg, MaterialTheme.shapes.small).padding(horizontal = Space.S8, vertical = Space.S4),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        shown.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = fg, textAlign = TextAlign.Center, maxLines = 1) }
                        Text(time, style = MaterialTheme.typography.labelSmall, color = fg, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().drawBehind {
                connectors.forEach { cn -> drawLine(ink, Offset(cn.x, cn.y1), Offset(cn.x, cn.y2), strokeWidth = 2.dp.toPx()) }
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val loose = Constraints(maxWidth = (w * 0.62f).toInt())
            fun xOf(min: Int): Float = (min / 1440f * w).let { if (rtl) w - it else it }
            val bar = measurables.first { (it.parentData as? SlotData)?.slot == Slot.BAR }.measure(Constraints.fixedWidth(w))
            val hourLabels = measurables.filter { (it.parentData as? SlotData)?.slot == Slot.HOUR }.map { (it.parentData as SlotData).minute to it.measure(Constraints()) }
            val above = measurables.filter { (it.parentData as? SlotData)?.slot == Slot.ABOVE }.map { (it.parentData as SlotData).minute to it.measure(loose) }
            val below = measurables.filter { (it.parentData as? SlotData)?.slot == Slot.BELOW }.map { (it.parentData as SlotData).minute to it.measure(loose) }

            // Left edge of each callout: centered on its dot, kept inside the card, pushed apart when two would overlap.
            fun spread(list: List<Pair<Int, androidx.compose.ui.layout.Placeable>>): List<Float> {
                val order = list.indices.sortedBy { xOf(list[it].first) }
                val lefts = FloatArray(list.size)
                var prevRight = -Float.MAX_VALUE
                for (i in order) {
                    val (m, p) = list[i]
                    var l = (xOf(m) - p.width / 2f).coerceIn(0f, (w - p.width).toFloat().coerceAtLeast(0f))
                    l = max(l, prevRight + gap); lefts[i] = l; prevRight = l + p.width
                }
                var nextLeft = Float.MAX_VALUE
                for (i in order.reversed()) {
                    val p = list[i].second
                    if (lefts[i] + p.width > nextLeft - gap) lefts[i] = nextLeft - gap - p.width
                    lefts[i] = max(lefts[i], 0f); nextLeft = lefts[i]
                }
                return lefts.toList()
            }
            val aboveLefts = spread(above); val belowLefts = spread(below)
            val link = 10.dp.roundToPx()
            val aboveH = if (above.isEmpty()) 0 else above.maxOf { it.second.height } + link
            val hoursH = hourLabels.maxOfOrNull { it.second.height } ?: 0
            val barTop = aboveH
            val hoursTop = barTop + bar.height
            val belowTop = hoursTop + hoursH + (if (below.isEmpty()) 0 else link)
            val belowH = if (below.isEmpty()) 0 else below.maxOf { it.second.height }
            val height = belowTop + belowH

            connectors.clear()
            above.forEachIndexed { i, (m, p) -> connectors += Connector(xOf(m), p.height.toFloat(), barTop + bar.height / 2f) }
            below.forEachIndexed { i, (m, p) -> connectors += Connector(xOf(m), barTop + bar.height / 2f, belowTop.toFloat()) }

            layout(w, height) {
                bar.place(0, barTop)
                hourLabels.forEach { (m, p) ->
                    val x = (xOf(m) - p.width / 2f).coerceIn(0f, (w - p.width).toFloat().coerceAtLeast(0f))
                    p.place(x.toInt(), hoursTop)
                }
                above.forEachIndexed { i, (_, p) -> p.place(aboveLefts[i].toInt(), 0) }
                below.forEachIndexed { i, (_, p) -> p.place(belowLefts[i].toInt(), belowTop) }
            }
        }
    }
}


/** The small picture next to a part of the day: sun, sun over the horizon, sunset, moon. Decorative; the name is next to it. */
@Composable
internal fun BlockIcon(block: Block, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp).clearAndSetSemantics { }) {
        val u = size.minDimension / 24f
        val sw = Stroke(1.8f * u, cap = StrokeCap.Round)
        fun ray(cx: Float, cy: Float, ang: Double, r1: Float, r2: Float) {
            val dx = Math.cos(ang).toFloat(); val dy = Math.sin(ang).toFloat()
            drawLine(tint, Offset((cx + dx * r1) * u, (cy + dy * r1) * u), Offset((cx + dx * r2) * u, (cy + dy * r2) * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        }
        when (block) {
            Block.MORNING -> {
                drawCircle(tint, 4.5f * u, Offset(12f * u, 12f * u), style = sw)
                for (k in 0 until 8) ray(12f, 12f, k * Math.PI / 4, 7.5f, 10.5f)
            }
            Block.NOON -> {
                // a sun above the horizon line, rays on top only
                drawArc(tint, 180f, 180f, false, Offset(7.5f * u, 9.5f * u), Size(9f * u, 9f * u), style = sw)
                for (k in 0..4) ray(12f, 14f, Math.PI + k * Math.PI / 4, 7.5f, 10.5f)
                drawLine(tint, Offset(3f * u, 19.5f * u), Offset(21f * u, 19.5f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
            }
            Block.EVENING -> {
                drawArc(tint, 180f, 180f, false, Offset(7f * u, 12f * u), Size(10f * u, 10f * u), style = sw)
                drawLine(tint, Offset(3f * u, 17f * u), Offset(21f * u, 17f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                drawLine(tint, Offset(8f * u, 21f * u), Offset(16f * u, 21f * u), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
                for (k in 1..3) ray(12f, 12f, Math.PI + k * Math.PI / 4, 8f, 10.5f)
            }
            Block.NIGHT -> {
                val moon = Path().apply {
                    moveTo(19.5f * u, 14.5f * u)
                    cubicTo(14f * u, 17.5f * u, 7.5f * u, 13.5f * u, 8.5f * u, 5.5f * u)
                    cubicTo(4.5f * u, 7f * u, 3.5f * u, 13f * u, 7.5f * u, 17.5f * u)
                    cubicTo(11f * u, 21.5f * u, 17.5f * u, 20f * u, 19.5f * u, 14.5f * u)
                    close()
                }
                drawPath(moon, tint, style = sw)
            }
        }
    }
}
