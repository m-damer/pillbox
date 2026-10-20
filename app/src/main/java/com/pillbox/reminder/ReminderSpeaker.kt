package com.pillbox.reminder

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pillbox.ui.AppLocale
import java.util.Locale

/** Says a short sentence out loud (Read reminders aloud). The phone's own voice, nothing leaves the phone. */
object ReminderSpeaker {
    private var tts: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        val app = context.applicationContext
        // A new reminder replaces one still being spoken.
        tts?.runCatching { stop(); shutdown() }
        var engine: TextToSpeech? = null
        engine = TextToSpeech(app) { status ->
            val e = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { e.shutdown(); return@TextToSpeech }
            e.language = Locale(AppLocale.active(app))
            e.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { e.shutdown(); if (tts === e) tts = null }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { e.shutdown(); if (tts === e) tts = null }
            })
            e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dose")
        }
        tts = engine
    }
}
