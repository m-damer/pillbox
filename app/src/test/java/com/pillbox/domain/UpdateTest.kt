package com.pillbox.domain

import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    private fun info(code: Int) = UpdateInfo("x", code, "", "https://a/b.apk", null, 0)

    @Test fun tagToCode() {
        assertEquals(10002, UpdateRules.versionCode("v1.0.2"))
        assertEquals(10100, UpdateRules.versionCode("1.1"))
        assertEquals(20305, UpdateRules.versionCode("v2.3.5"))
    }
    @Test fun badTags() {
        assertNull(UpdateRules.versionCode("latest"))
        assertNull(UpdateRules.versionCode("v1.x.2"))
        assertNull(UpdateRules.versionCode("v1.100.0"))
        assertNull(UpdateRules.versionCode("v1"))
        assertNull(UpdateRules.versionCode("v1.2.3.4"))
    }
    @Test fun newer() {
        assertTrue(UpdateRules.isNewer(10003, 10002))
        assertFalse(UpdateRules.isNewer(10002, 10002))
        assertFalse(UpdateRules.isNewer(10001, 10002))
    }
    @Test fun notifyOncePerVersion() {
        assertTrue(UpdateRules.shouldNotify(true, info(10003), 10002, 0))
        assertFalse(UpdateRules.shouldNotify(true, info(10003), 10002, 10003))
        assertTrue(UpdateRules.shouldNotify(true, info(10004), 10002, 10003))
        assertFalse(UpdateRules.shouldNotify(false, info(10003), 10002, 0))
        assertFalse(UpdateRules.shouldNotify(true, info(10002), 10002, 0))
    }
    @Test fun sha() {
        val h = "a".repeat(64)
        assertEquals(h, UpdateRules.parseSha256("${h.uppercase()}  PillBox-1.0.2.apk\n"))
        assertEquals(h, UpdateRules.parseSha256(h))
        assertNull(UpdateRules.parseSha256("not a hash"))
        assertNull(UpdateRules.parseSha256(""))
    }
    @Test fun notes() {
        assertEquals("What's new\nFaster\nFixes", UpdateRules.plainNotes("## What's new\n\n- Faster\n* Fixes\n"))
        assertEquals(2, UpdateRules.plainNotes("a\nb\nc", 2).lines().size)
    }
}
