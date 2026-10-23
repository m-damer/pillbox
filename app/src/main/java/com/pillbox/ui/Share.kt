package com.pillbox.ui

import android.content.Context
import android.content.Intent

/** Opens Android's share sheet with plain text. Nothing is sent by the app itself: the user picks where it goes. */
fun Context.shareText(text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
