package com.pillbox

import android.content.Context
import com.pillbox.domain.FeedbackMail
import java.io.File

/**
 * Remembers a crash on the phone, so the next launch can ask "send a report?". Nothing leaves the phone by itself:
 * the report goes only if the person opens their email app and presses Send.
 */
object CrashReporter {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(describe(thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Error types and code lines only; messages can hold what the person typed, so they are left out. */
    internal fun describe(thread: String, error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(5).map { e ->
            e.javaClass.name to e.stackTrace.map { it.toString() }
        }.toList()
        return FeedbackMail.crashText(thread, chain)
    }

    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.ifBlank { null } }.getOrNull()

    fun clear(context: Context) { runCatching { File(context.filesDir, FILE).delete() } }
}
