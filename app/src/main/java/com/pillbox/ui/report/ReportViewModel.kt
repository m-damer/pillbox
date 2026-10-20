package com.pillbox.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillbox.data.PillBoxRepository
import com.pillbox.domain.Medicine
import com.pillbox.domain.Pause
import com.pillbox.domain.ReportBuilder
import com.pillbox.domain.ReportOptions
import com.pillbox.domain.Schedule
import com.pillbox.ui.Formatter
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A message to show once: the toast after Save, or the error. */
data class ReportMessage(val id: Int, val title: String, val body: String?, val error: Boolean = false)

data class ReportUiState(
    val loaded: Boolean = false,
    val patient: String = "",
    val options: ReportOptions = ReportOptions(),
    /** Pages in the current PDF, or null while it is being made. */
    val pages: Int? = null,
    val message: ReportMessage? = null,
    /** Page images for the preview screen, or null when it is closed. */
    val preview: List<Bitmap>? = null,
)

/** The Doctor report screen: choices on the left, a PDF made from them, then Share or Save. */
class ReportViewModel(
    private val context: Context,
    private val repo: PillBoxRepository,
    private val meName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private class Data(val patient: String, val items: List<Triple<Medicine, Schedule, List<Pause>>>, val events: List<com.pillbox.domain.DoseEvent>)

    private var data: Data? = null
    private var file: File? = null
    private var job: Job? = null
    private var counter = 0
    private val dir get() = File(context.cacheDir, "reports").also { it.mkdirs() }

    init {
        viewModelScope.launch {
            val me = repo.activePerson(meName)
            val patient = me.birthYear?.let { "${me.name} ($it)" } ?: me.name
            val today = LocalDateTime.now().toLocalDate()
            val from = today.minusDays((ReportOptions.PERIODS.max() - 1).toLong()).atStartOfDay()
            val events = repo.observeHistoryEvents(me.id, from, today.plusDays(1).atStartOfDay()).first()
            val meds = repo.observeMedicines(me.id).first() + repo.observeFinished(me.id).first()
            val items = meds.mapNotNull { m -> m.schedule?.let { Triple(m.medicine, it, m.pauses) } }
            data = Data(patient, items, events)
            _state.update { it.copy(loaded = true, patient = patient) }
            regenerate()
        }
    }

    fun setPeriod(days: Int) = change { it.copy(days = days) }
    fun setMedicines(on: Boolean) = change { it.copy(medicines = on) }
    fun setDayByDay(on: Boolean) = change { it.copy(dayByDay = on) }
    fun setMissedLate(on: Boolean) = change { it.copy(missedLate = on) }

    private fun change(f: (ReportOptions) -> ReportOptions) {
        _state.update { it.copy(options = f(it.options), pages = null) }
        regenerate()
    }

    /** Makes the PDF for the current choices in the background. Cancels an older one still running. */
    private fun regenerate() {
        val d = data ?: return
        val options = _state.value.options
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val model = ReportBuilder.build(d.patient, d.items, d.events, LocalDateTime.now(), options)
                    dir.listFiles()?.forEach { it.delete() }
                    val out = File(dir, "PillBox-report-${model.created}-${options.days}d.pdf")
                    val pages = out.outputStream().use { ReportPdf(context, Formatter.from(context)).write(model, it) }
                    out to pages
                }
                file = result.first
                _state.update { it.copy(pages = result.second) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pages = null, message = failed()) }
            }
        }
    }

    /** Waits for the PDF, then hands its content URI (for Share) to [onReady]. */
    fun share(onReady: (Uri, String) -> Unit) {
        viewModelScope.launch {
            job?.join()
            val f = file ?: run { _state.update { it.copy(message = failed()) }; return@launch }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            onReady(uri, f.name)
        }
    }

    /** The default file name offered by "Save to phone". */
    fun fileName(): String = file?.name ?: "PillBox-report.pdf"

    /** Copies the PDF to the place the person picked. */
    fun saveTo(uri: Uri, savedTitle: String) {
        viewModelScope.launch {
            job?.join()
            val f = file ?: run { _state.update { it.copy(message = failed()) }; return@launch }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            _state.update { it.copy(message = if (ok) ReportMessage(++counter, savedTitle, f.name) else failed()) }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Draws every page of the PDF to images for the preview screen. */
    fun openPreview() {
        viewModelScope.launch {
            job?.join()
            val f = file ?: return@launch
            val bitmaps = withContext(Dispatchers.IO) {
                runCatching {
                    val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    try {
                        (0 until renderer.pageCount).map { i ->
                            val p = renderer.openPage(i)
                            try {
                                val width = 1000
                                val bmp = Bitmap.createBitmap(width, (width.toFloat() * p.height / p.width).toInt(), Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            } finally {
                                p.close()
                            }
                        }
                    } finally {
                        renderer.close()
                        fd.close()
                    }
                }.getOrNull()
            }
            if (bitmaps == null) _state.update { it.copy(message = failed()) } else _state.update { it.copy(preview = bitmaps) }
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    private fun failed() = ReportMessage(
        ++counter, context.getString(com.pillbox.R.string.report_failed_title), context.getString(com.pillbox.R.string.report_failed_body), error = true,
    )
}
