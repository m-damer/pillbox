package com.pillbox.domain

/** Where feedback, ratings and crash reports are addressed. One place, so it is easy to change. */
object Contact {
    const val DEVELOPER_EMAIL = "damermahmoud@gmail.com"
    /** Link to the app in the store, added to "Share the app" once the app is published. Empty until then. */
    const val STORE_URL = ""
}

enum class FeedbackKind { PROBLEM, IDEA, QUESTION, LIKE }

enum class EmailCheck { OK, EMPTY, INVALID }

/** A message ready to hand to the person's email app. Nothing is sent by Pill Box itself. */
data class Mail(val to: String, val subject: String, val body: String)

object FeedbackMail {
    /** "name@example.com": one @, something before it, a dot in the part after it, no spaces. Deliberately simple. */
    fun checkEmail(raw: String): EmailCheck {
        val s = raw.trim()
        if (s.isEmpty()) return EmailCheck.EMPTY
        if (s.any { it.isWhitespace() }) return EmailCheck.INVALID
        val at = s.indexOf('@')
        if (at < 1 || at != s.lastIndexOf('@')) return EmailCheck.INVALID
        val domain = s.substring(at + 1)
        val dot = domain.lastIndexOf('.')
        if (dot < 1 || dot == domain.length - 1) return EmailCheck.INVALID
        return EmailCheck.OK
    }

    /** App info is only the version and the phone model; never medicines or people. */
    fun appInfo(version: String, model: String, androidVersion: String): String =
        "Pill Box $version\nPhone: $model\nAndroid: $androidVersion"

    fun feedback(kindLabel: String, message: String, email: String, phone: String, info: String?): Mail {
        val body = buildString {
            append(message.trim()).append("\n\n")
            append("---\nEmail: ").append(email.trim())
            if (phone.isNotBlank()) append("\nPhone: ").append(phone.trim())
            if (info != null) append("\n").append(info)
        }
        return Mail(Contact.DEVELOPER_EMAIL, "Pill Box feedback: $kindLabel", body)
    }

    fun rating(stars: Int, info: String): Mail {
        val s = stars.coerceIn(1, 5)
        return Mail(Contact.DEVELOPER_EMAIL, "Pill Box rating: $s/5", "Rating: $s out of 5\n\n---\n$info")
    }

    fun crash(report: String, info: String): Mail =
        Mail(Contact.DEVELOPER_EMAIL, "Pill Box crash report", "$report\n\n---\n$info")

    /**
     * Only what helps to fix a crash: the error type and the code lines. The error message is left out because it can
     * hold names typed by the person. Long traces are cut.
     */
    fun crashText(thread: String, trace: List<Pair<String, List<String>>>, maxChars: Int = 6000): String {
        val sb = StringBuilder("Thread: ").append(thread).append('\n')
        trace.forEachIndexed { i, (type, frames) ->
            sb.append(if (i == 0) "Error: " else "Caused by: ").append(type).append('\n')
            frames.take(30).forEach { sb.append("  at ").append(it).append('\n') }
        }
        return if (sb.length <= maxChars) sb.toString() else sb.substring(0, maxChars) + "\n..."
    }
}
