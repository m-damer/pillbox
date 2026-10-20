package com.pillbox.domain

import com.pillbox.reminder.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateTest {
    @Test fun everythingFineMeansNothingToFix() = assertEquals(0, PermissionState(true, true, true).issues)

    @Test fun eachProblemCountsOnce() {
        assertEquals(1, PermissionState(false, true, true).issues)
        assertEquals(2, PermissionState(true, false, false).issues)
        assertEquals(4, PermissionState(false, false, false, doNotDisturb = true).issues)
    }

    @Test fun doNotDisturbAloneIsOneThingToCheck() = assertEquals(1, PermissionState(true, true, true, doNotDisturb = true).issues)
}
