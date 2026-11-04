package com.pillbox.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Wraps text in a first-strong isolate so a Latin name inside an Arabic sentence (or the reverse) cannot reorder the words around it. */
fun isolate(text: String): String {
    if (text.isEmpty()) return text
    // Say the direction outright when the text is all Arabic or all Latin. A name such as "Metformin" stays left to right
    // and an amount such as "٨ أقراص" stays right to left; mixed text or text with no letters falls back to first-strong.
    val arabic = text.any { it in '\u0600'..'\u06FF' }
    val latin = text.any { it in 'A'..'Z' || it in 'a'..'z' }
    val open = when {
        arabic && !latin -> '\u2067'
        latin && !arabic -> '\u2066'
        else -> '\u2068'
    }
    return "$open$text\u2069"
}

private fun isolateArgs(args: Array<out Any?>): Array<Any> = Array(args.size) { i -> (args[i] as? String)?.let(::isolate) ?: (args[i] ?: "") }

/** Same as stringResource, but every text argument (a medicine or person name, say) is isolated. Use it whenever a name goes into a sentence. */
@Composable
fun stringResourceIso(id: Int, vararg args: Any?): String = stringResource(id, *isolateArgs(args))

fun Context.getStringIso(id: Int, vararg args: Any?): String = getString(id, *isolateArgs(args))
