package com.pillbox.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Wraps text in a first-strong isolate so a Latin name inside an Arabic sentence (or the reverse) cannot reorder the words around it. */
fun isolate(text: String): String = if (text.isEmpty()) text else "⁨$text⁩"

private fun isolateArgs(args: Array<out Any?>): Array<Any> = Array(args.size) { i -> (args[i] as? String)?.let(::isolate) ?: (args[i] ?: "") }

/** Same as stringResource, but every text argument (a medicine or person name, say) is isolated. Use it whenever a name goes into a sentence. */
@Composable
fun stringResourceIso(id: Int, vararg args: Any?): String = stringResource(id, *isolateArgs(args))

fun Context.getStringIso(id: Int, vararg args: Any?): String = getString(id, *isolateArgs(args))
