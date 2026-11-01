package com.pillbox.domain

import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppClockTest {
    @After fun reset() { AppClock.home = null }

    @Test fun followsThePhoneByDefault() {
        AppClock.home = null
        assertFalse(AppClock.keepsHome)
        assertEquals(ZoneId.systemDefault(), AppClock.zone())
    }

    @Test fun keepingHomeUsesTheHomeZoneEvenWhenThePhoneIsElsewhere() {
        AppClock.home = ZoneId.of("Asia/Damascus")
        assertTrue(AppClock.keepsHome)
        assertEquals(ZoneId.of("Asia/Damascus"), AppClock.zone())
        // Two zones 9+ hours apart cannot show the same clock hour all day: the clock really follows the home zone.
        val damascus = AppClock.now()
        AppClock.home = ZoneId.of("Pacific/Auckland")
        val auckland = AppClock.now()
        assertTrue(java.time.Duration.between(damascus, auckland).abs().toHours() >= 8)
    }

    @Test fun homeToKeepIsTheCurrentHomeOrThePhoneZone() {
        assertEquals("Europe/Istanbul", AppClock.homeToKeep("Europe/Istanbul"))
        assertEquals(ZoneId.systemDefault().id, AppClock.homeToKeep(null))
    }

    @Test fun aBadSavedZoneMeansFollowThePhone() {
        assertNull(AppClock.parse(null))
        assertNull(AppClock.parse("Not/AZone"))
        assertEquals(ZoneId.of("Asia/Damascus"), AppClock.parse("Asia/Damascus"))
    }
}
