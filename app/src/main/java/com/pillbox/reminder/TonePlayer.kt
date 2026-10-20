package com.pillbox.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.pillbox.domain.Tone

/** Plays a tone once so the user can hear it before choosing. Uses the notification volume, like the real reminder. */
class TonePlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(tone: Tone) {
        stop()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        player = MediaPlayer.create(context, Reminders.toneResource(tone), attrs, 0)?.apply {
            setOnCompletionListener { it.release(); if (player === it) player = null }
            start()
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }
}
