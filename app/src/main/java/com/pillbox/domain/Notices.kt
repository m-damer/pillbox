package com.pillbox.domain

/** The tabs of the notifications screen. */
enum class NoticeKind { DOSE, STOCK, EXPIRY, APP }

/**
 * One line in the notifications screen: something the app told the user. [key] says "this is the same notice" so a repeated
 * reminder is saved once. [target] says where a tap goes: "today", "medicine:<id>", "updates" or "backup".
 */
data class Notice(
    val key: String,
    val kind: NoticeKind,
    val title: String,
    val text: String,
    val atMillis: Long,
    val read: Boolean = false,
    val target: String? = null,
)

/** Pure rules for the saved notices. Newest first; nothing is kept longer than 30 days or beyond 200 items. */
object NoticeRules {
    const val MAX_AGE_DAYS = 30
    const val MAX_ITEMS = 200
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Adds a notice unless one with the same key is already saved. */
    fun add(list: List<Notice>, n: Notice, nowMillis: Long): List<Notice> {
        if (list.any { it.key == n.key }) return prune(list, nowMillis)
        return prune(list + n, nowMillis)
    }

    /** Drops notices older than 30 days, sorts newest first and keeps at most 200. */
    fun prune(list: List<Notice>, nowMillis: Long): List<Notice> =
        list.filter { nowMillis - it.atMillis <= MAX_AGE_DAYS * DAY_MS }.sortedByDescending { it.atMillis }.take(MAX_ITEMS)

    /** The notices of one tab; null means the All tab. */
    fun ofKind(list: List<Notice>, kind: NoticeKind?): List<Notice> = if (kind == null) list else list.filter { it.kind == kind }

    fun unread(list: List<Notice>, kind: NoticeKind? = null): Int = ofKind(list, kind).count { !it.read }

    fun markRead(list: List<Notice>, key: String): List<Notice> = list.map { if (it.key == key) it.copy(read = true) else it }

    fun markAllRead(list: List<Notice>): List<Notice> = list.map { it.copy(read = true) }

    /** Removes the notices of one tab; null removes everything. */
    fun clear(list: List<Notice>, kind: NoticeKind?): List<Notice> = if (kind == null) emptyList() else list.filter { it.kind != kind }
}
