package com.pillbox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private fun n(key: String, kind: NoticeKind = NoticeKind.DOSE, ago: Long = 0, read: Boolean = false) =
        Notice(key, kind, "t", "x", now - ago, read)

    @Test fun same_key_is_saved_once() {
        val a = NoticeRules.add(emptyList(), n("a"), now)
        assertEquals(1, NoticeRules.add(a, n("a"), now).size)
    }

    @Test fun newest_first() {
        val l = NoticeRules.add(NoticeRules.add(emptyList(), n("old", ago = day), now), n("new"), now)
        assertEquals(listOf("new", "old"), l.map { it.key })
    }

    @Test fun older_than_30_days_is_removed_on_add() {
        val l = NoticeRules.add(listOf(n("ancient", ago = 31 * day), n("edge", ago = 30 * day)), n("new"), now)
        assertEquals(listOf("new", "edge"), l.map { it.key })
    }

    @Test fun keeps_at_most_200() {
        val many = (1..250).map { n("k$it", ago = it.toLong()) }
        assertEquals(200, NoticeRules.prune(many, now).size)
        assertEquals("k1", NoticeRules.prune(many, now).first().key)
    }

    @Test fun tabs_and_unread_counts() {
        val l = listOf(n("a", NoticeKind.DOSE), n("b", NoticeKind.STOCK, read = true), n("c", NoticeKind.STOCK))
        assertEquals(3, NoticeRules.ofKind(l, null).size)
        assertEquals(2, NoticeRules.ofKind(l, NoticeKind.STOCK).size)
        assertEquals(2, NoticeRules.unread(l))
        assertEquals(1, NoticeRules.unread(l, NoticeKind.STOCK))
        assertEquals(0, NoticeRules.unread(l, NoticeKind.EXPIRY))
    }

    @Test fun mark_read() {
        val l = listOf(n("a"), n("b"))
        assertEquals(1, NoticeRules.unread(NoticeRules.markRead(l, "a")))
        assertEquals(0, NoticeRules.unread(NoticeRules.markAllRead(l)))
    }

    @Test fun clear_one_tab_or_all() {
        val l = listOf(n("a", NoticeKind.DOSE), n("b", NoticeKind.STOCK), n("c", NoticeKind.APP))
        assertEquals(listOf("a", "c"), NoticeRules.clear(l, NoticeKind.STOCK).map { it.key })
        assertEquals(0, NoticeRules.clear(l, null).size)
    }
}
