package com.pillbox.ui.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.pillbox.R
import com.pillbox.domain.DoseOutcome
import com.pillbox.domain.DoseRecord
import com.pillbox.domain.ReportDay
import com.pillbox.domain.ReportModel
import com.pillbox.ui.Formatter
import com.pillbox.ui.localizedStrength
import com.pillbox.ui.medicines.scheduleSummaryText
import com.pillbox.ui.quantityFor
import com.pillbox.ui.today.SkipReason
import com.pillbox.ui.unitPlural
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Draws the doctor report as a PDF: black ink on white so it prints well, A4, as many pages as it needs.
 * Layout is written for left-to-right and mirrored for Arabic, so the first column is always on the reading side.
 * Everything it says comes from [ReportModel] and string resources.
 */
class ReportPdf(context: Context, private val fmt: Formatter) {
    private val res = context.resources
    private val rtl = TextUtils.getLayoutDirectionFromLocale(res.configuration.locales[0]) == View.LAYOUT_DIRECTION_RTL

    private var doc: PdfDocument? = null
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas = Canvas()
    private var y = 0f
    private var pageNo = 0

    /** Writes the report to [out] and returns the number of pages. */
    fun write(model: ReportModel, out: OutputStream): Int {
        val d = PdfDocument()
        doc = d
        pageNo = 0
        try {
            startPage()
            draw(model)
            finishPage()
            d.writeTo(out)
        } finally {
            d.close()
            doc = null
        }
        return pageNo
    }

    // ---------------------------------------------------------------- pages

    private fun startPage() {
        pageNo++
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create()
        val p = doc!!.startPage(info)
        page = p
        canvas = p.canvas
        y = MARGIN
    }

    private fun finishPage() {
        // Footer: what this file is, and the page number.
        val small = tp(7.5f, color = GRAY)
        val note = layout(res.getString(R.string.report_footer), small, CW * 0.75f)
        drawLayout(note, MARGIN, PAGE_H - 34f)
        val num = layout(res.getString(R.string.report_page, fmt.number(pageNo)), small, CW * 0.2f, end = true)
        drawLayout(num, MARGIN + CW * 0.8f, PAGE_H - 34f)
        doc!!.finishPage(page!!)
    }

    private fun newPage() {
        finishPage()
        startPage()
    }

    private fun need(h: Float) { if (y + h > BOTTOM) newPage() }

    // ---------------------------------------------------------------- content

    private fun draw(m: ReportModel) {
        header()
        meta(m)
        stats(m)
        if (m.options.medicines) medicinesTable(m)
        if (m.options.dayByDay) dayByDay(m)
        if (m.options.missedLate) lateAndMissed(m)
    }

    private fun header() {
        // A simple pill mark and the app name on the reading side, the report title on the other.
        val logo = RectF(mx(MARGIN, 34f), y, mx(MARGIN, 34f) + 34f, y + 34f)
        canvas.drawRoundRect(logo, 8f, 8f, fill(INDIGO))
        canvas.drawCircle(logo.centerX(), logo.centerY(), 8f, fill(WHITE))
        val name = layout(res.getString(R.string.app_name), tp(18f, bold = true), 200f)
        drawLayout(name, MARGIN + 44f, y + 5f)
        val title = layout(res.getString(R.string.report_title), tp(12f, color = GRAY), CW * 0.45f, end = true)
        drawLayout(title, MARGIN + CW * 0.55f, y + 10f)
        y += 46f
        canvas.drawRect(MARGIN, y, MARGIN + CW, y + 1.5f, fill(INK))
        y += 14f
    }

    private fun meta(m: ReportModel) {
        val days = m.days.size
        val period = res.getString(
            R.string.report_period_value, dateLong(m.start), dateLong(m.end),
            res.getQuantityString(R.plurals.n_days, days, fmt.number(days)),
        )
        val cols = listOf(
            res.getString(R.string.report_patient) to m.patient,
            res.getString(R.string.report_period) to period,
            res.getString(R.string.report_created) to dateLong(m.created),
        )
        val weights = listOf(0.28f, 0.44f, 0.28f)
        var x = MARGIN
        var tallest = 0f
        cols.forEachIndexed { i, (label, value) ->
            val w = CW * weights[i] - 8f
            val l = layout(label, tp(9f, color = GRAY), w)
            val v = layout(value, tp(11f, bold = true), w)
            drawLayout(l, x, y)
            drawLayout(v, x, y + l.height + 2f)
            tallest = maxOf(tallest, l.height + 2f + v.height)
            x += CW * weights[i]
        }
        y += tallest + 14f
    }

    private fun stats(m: ReportModel) {
        val h = m.history
        val pct = h.percent?.let { fmt.number(it) + if (rtl) "٪" else "%" } ?: "–"
        val boxes = listOf(
            pct to res.getString(R.string.report_stat_pct),
            fmt.number(h.onTime) to res.getString(R.string.report_stat_on_time),
            fmt.number(h.late) to res.getString(R.string.report_stat_late),
            fmt.number(h.missed) to res.getString(R.string.report_stat_missed),
        )
        val gap = 8f
        val w = (CW - gap * 3) / 4
        var x = MARGIN
        val heightOf = 52f
        boxes.forEach { (big, label) ->
            canvas.drawRect(mx(x, w), y, mx(x, w) + w, y + heightOf, stroke(INK, 1f))
            drawLayout(layout(big, tp(20f, bold = true), w - 12f), x + 6f, y + 5f)
            drawLayout(layout(label, tp(8.5f), w - 12f), x + 6f, y + 31f)
            x += w + gap
        }
        y += heightOf + 18f
    }

    private fun heading(text: String) {
        need(40f)
        drawLayout(layout(text, tp(13f, bold = true), CW), MARGIN, y)
        y += 22f
    }

    private fun medicinesTable(m: ReportModel) {
        heading(res.getString(R.string.report_medicines))
        val headers = listOf(
            R.string.report_col_medicine, R.string.report_col_dose, R.string.report_col_when,
            R.string.report_col_on_time, R.string.report_col_late, R.string.report_col_missed,
        ).map { res.getString(it) }
        val rows = m.medicines.map { r ->
            val med = r.medicine
            val strength = localizedStrength(med.strength, res)
            val dose = (if (strength != null) "$strength, " else "") + fmt.amount(med.doseAmount) + " " +
                res.getQuantityString(unitPlural(med.unit), quantityFor(med.doseAmount))
            listOf(med.name, dose, whenCell(r), fmt.number(r.onTime), fmt.number(r.late), fmt.number(r.missed))
        }
        table(headers, listOf(2.6f, 2.4f, 3f, 1.2f, 1f, 1.2f), setOf(3, 4, 5), rows)
        y += 16f
    }

    /** As-needed medicines say how often they were taken, since they have no planned doses to count. */
    private fun whenCell(r: com.pillbox.domain.ReportMedicine): String {
        val base = scheduleSummaryText(res, r.schedule, fmt)
        if (r.schedule.frequency !is com.pillbox.domain.Frequency.AsNeeded) return base
        return res.getQuantityString(R.plurals.report_as_needed_taken, r.asNeeded, fmt.number(r.asNeeded))
    }

    private fun dayByDay(m: ReportModel) {
        heading(res.getString(R.string.report_day_by_day))
        val cw = CW / 7f
        val ch = 26f
        val dayFmt = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        val firstFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        m.days.chunked(7).forEach { row ->
            need(ch + 4f)
            row.forEachIndexed { i, date ->
                val x = MARGIN + i * cw
                val mark = m.dayMark(date)
                val border = when (mark) {
                    ReportDay.MISSED -> stroke(INK, 2.2f)
                    ReportDay.LATE -> stroke(INK, 1f).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f) }
                    ReportDay.PENDING, ReportDay.PAUSED -> stroke(LIGHT, 1f)
                    else -> stroke(INK, 1f)
                }
                if (mark == ReportDay.MISSED) canvas.drawRect(mx(x, cw), y, mx(x, cw) + cw, y + ch, fill(FILL))
                canvas.drawRect(mx(x, cw) + 0.5f, y + 0.5f, mx(x, cw) + cw - 0.5f, y + ch - 0.5f, border)
                val label = fmt.digits((if (i == 0 || date.dayOfMonth == 1) firstFmt else dayFmt).format(date))
                drawLayout(layout(label, tp(8f, bold = mark == ReportDay.MISSED), cw - 20f), x + 4f, y + 8f)
                drawMark(mark, x + cw - 10f, y + ch / 2)
            }
            y += ch
        }
        y += 8f
        legend()
        y += 20f
    }

    private fun legend() {
        need(20f)
        val items = listOf(
            ReportDay.OK to R.string.report_legend_ok, ReportDay.LATE to R.string.report_legend_late,
            ReportDay.MISSED to R.string.report_legend_missed, ReportDay.PAUSED to R.string.report_legend_paused,
        )
        var x = MARGIN
        val paint = tp(8.5f)
        items.forEach { (mark, label) ->
            val text = res.getString(label)
            val w = paint.measureText(text) + 2f
            if (x + 16f + w > MARGIN + CW) { x = MARGIN; y += 16f; need(16f) }
            drawMark(mark, x + 6f, y + 6f)
            drawLayout(layout(text, paint, w + 4f), x + 16f, y)
            x += 16f + w + 16f
        }
        y += 14f
    }

    private fun lateAndMissed(m: ReportModel) {
        heading(res.getString(R.string.report_late_missed))
        val list = m.history.attention.sortedBy { it.planned }
        if (list.isEmpty()) {
            val text = if (m.history.total == 0) R.string.report_no_doses else R.string.report_none
            drawLayout(layout(res.getString(text), tp(10f), CW), MARGIN, y)
            y += 20f
            return
        }
        val headers = listOf(R.string.report_col_date_time, R.string.report_col_medicine, R.string.report_col_what, R.string.report_col_reason).map { res.getString(it) }
        val rows = list.map { r ->
            listOf(
                shortDateLong(r), r.medicine.name, whatText(r),
                SkipReason.fromCode(r.reason)?.let { res.getString(reasonRes(it)) } ?: res.getString(R.string.report_no_reason),
            )
        }
        table(headers, listOf(3f, 2.4f, 3f, 3f), emptySet(), rows)
    }

    private fun shortDateLong(r: DoseRecord): String =
        res.getString(R.string.history_when, com.pillbox.ui.shortDate(r.planned.toLocalDate(), fmt), fmt.time(r.planned.toLocalTime()))

    private fun whatText(r: DoseRecord): String = when (r.outcome) {
        DoseOutcome.SKIPPED -> res.getString(R.string.report_what_skipped)
        DoseOutcome.MISSED -> res.getString(R.string.report_what_missed)
        DoseOutcome.LATE -> res.getString(
            R.string.report_what_late,
            if (r.lateMinutes >= 60) res.getQuantityString(R.plurals.n_hours, r.lateMinutes / 60, fmt.number(r.lateMinutes / 60))
            else res.getQuantityString(R.plurals.n_minutes, r.lateMinutes, fmt.number(r.lateMinutes)),
        )
        DoseOutcome.ON_TIME -> ""
    }

    private fun reasonRes(r: SkipReason) = when (r) {
        SkipReason.NOT_FEELING_WELL -> R.string.reason_not_well
        SkipReason.SIDE_EFFECTS -> R.string.reason_side_effects
        SkipReason.RAN_OUT -> R.string.reason_ran_out
        SkipReason.DOCTOR_SAID -> R.string.reason_doctor
        SkipReason.OTHER -> R.string.reason_other
    }

    // ---------------------------------------------------------------- table

    /** A bordered table that continues on the next page with its header repeated. */
    private fun table(headers: List<String>, weights: List<Float>, centered: Set<Int>, rows: List<List<String>>) {
        val total = weights.sum()
        val widths = weights.map { it / total * CW }

        fun drawRow(cells: List<String>, header: Boolean) {
            val paint = tp(9f, bold = header)
            val layouts = cells.mapIndexed { i, s -> layout(s, paint, widths[i] - 2 * PAD, center = i in centered) }
            val h = layouts.maxOf { it.height } + 2 * PAD
            if (y + h > BOTTOM) {
                newPage()
                if (!header) drawRow(headers, true)
            }
            var x = MARGIN
            cells.indices.forEach { i ->
                val left = mx(x, widths[i])
                if (header) canvas.drawRect(left, y, left + widths[i], y + h, fill(FILL))
                canvas.drawRect(left, y, left + widths[i], y + h, stroke(INK, 0.8f))
                drawLayout(layouts[i], x + PAD, y + PAD)
                x += widths[i]
            }
            y += h
        }

        drawRow(headers, true)
        rows.forEach { drawRow(it, false) }
    }

    // ---------------------------------------------------------------- marks and helpers

    /** Check, "!", cross, pause bars, "…". The symbol, not the colour, carries the meaning. */
    private fun drawMark(mark: ReportDay, cx: Float, cy: Float) {
        val x = mx(cx - 6f, 12f) + 6f
        val ink = stroke(INK, 1.7f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        when (mark) {
            ReportDay.OK -> canvas.drawPath(Path().apply { moveTo(x - 4.5f, cy); lineTo(x - 1.2f, cy + 3.5f); lineTo(x + 4.5f, cy - 4f) }, ink)
            ReportDay.MISSED -> {
                canvas.drawLine(x - 4f, cy - 4f, x + 4f, cy + 4f, ink)
                canvas.drawLine(x - 4f, cy + 4f, x + 4f, cy - 4f, ink)
            }
            ReportDay.LATE -> {
                canvas.drawLine(x, cy - 5f, x, cy + 1.5f, ink)
                canvas.drawPoint(x, cy + 5f, ink)
            }
            ReportDay.PENDING -> for (dx in listOf(-4f, 0f, 4f)) canvas.drawPoint(x + dx, cy + 3f, ink)
            ReportDay.PAUSED -> {
                canvas.drawLine(x - 2f, cy - 4f, x - 2f, cy + 4f, ink)
                canvas.drawLine(x + 2f, cy - 4f, x + 2f, cy + 4f, ink)
            }
            ReportDay.NONE -> Unit
        }
    }

    private fun dateLong(d: LocalDate): String =
        fmt.digits(d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())))

    /** Left edge for something drawn at logical [x] with width [w]: mirrored for right-to-left. */
    private fun mx(x: Float, w: Float): Float = if (rtl) MARGIN + CW - (x - MARGIN) - w else x

    private fun tp(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.color = color
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = width }

    private fun layout(text: String, paint: TextPaint, width: Float, center: Boolean = false, end: Boolean = false): StaticLayout {
        val align = when {
            center -> Layout.Alignment.ALIGN_CENTER
            end -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .build()
    }

    private fun drawLayout(l: StaticLayout, x: Float, top: Float) {
        canvas.save()
        canvas.translate(mx(x, l.width.toFloat()), top)
        l.draw(canvas)
        canvas.restore()
    }

    private companion object {
        const val PAGE_W = 595f
        const val PAGE_H = 842f
        const val MARGIN = 40f
        const val CW = PAGE_W - 2 * MARGIN
        const val BOTTOM = PAGE_H - 52f
        const val PAD = 5f
        val INK = 0xFF111111.toInt()
        val GRAY = 0xFF555555.toInt()
        val LIGHT = 0xFFAAAAAA.toInt()
        val FILL = 0xFFE8E8E8.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val INDIGO = 0xFF4338CA.toInt()
    }
}
