package com.pillbox.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PackMathTest {
    private val a = Pack(1, 30.0, YearMonth.of(2027, 1))
    private val b = Pack(2, 30.0, YearMonth.of(2027, 6))

    @Test fun firstPackIsInUseByDefault() {
        val s = PackMath.states(listOf(a, b), 60.0)
        assertEquals(listOf(30.0, 30.0), s.map { it.remaining })
        assertEquals(listOf(true, false), s.map { it.inUse })
    }

    @Test fun takenDosesComeOutOfTheFrontPack() {
        val s = PackMath.states(listOf(a, b), 45.0)
        assertEquals(listOf(15.0, 30.0), s.map { it.remaining })
    }

    @Test fun nextPackStartsWhenFirstIsEmpty() {
        val s = PackMath.states(listOf(a, b), 25.0)
        assertEquals(listOf(0.0, 25.0), s.map { it.remaining })
        assertEquals(2, PackMath.current(listOf(a, b), 25.0)?.pack?.id)
        assertEquals(YearMonth.of(2027, 6), PackMath.currentExpiry(listOf(a, b), 25.0))
    }

    @Test fun nothingInUseWhenAllEmpty() {
        assertNull(PackMath.current(listOf(a, b), 0.0))
    }

    @Test fun undoBringsBackTheFinishedPack() {
        assertEquals(2, PackMath.current(listOf(a, b), 30.0)?.pack?.id)
        assertEquals(1, PackMath.current(listOf(a, b), 31.0)?.pack?.id)
    }

    @Test fun userCanChooseThePackInUse() {
        val p = PackMath.useNow(listOf(a, b), 50.0, 2)
        assertEquals(listOf(2, 1), p.map { it.id })
        assertEquals(listOf(30.0, 20.0), p.map { it.left })
        assertEquals(2, PackMath.current(p, 50.0)?.pack?.id)
        // doses now come out of pack 2
        assertEquals(listOf(20.0, 20.0), PackMath.states(p, 40.0).map { it.remaining })
    }

    @Test fun useNowDropsFinishedPacks() {
        val p = PackMath.useNow(listOf(a, b), 25.0, 2)
        assertEquals(listOf(2), p.map { it.id })
    }

    @Test fun useNowWithUnknownIdChangesNothing() {
        assertEquals(listOf(a, b), PackMath.useNow(listOf(a, b), 60.0, 9))
    }

    @Test fun noDateAndNoPacksMeansNoTracking() {
        assertTrue(PackMath.afterAdd(emptyList(), 10.0, null, 30.0, null).isEmpty())
    }

    @Test fun firstDateCreatesPackForWhatWasThere() {
        val p = PackMath.afterAdd(emptyList(), 10.0, YearMonth.of(2026, 12), 30.0, YearMonth.of(2027, 5))
        assertEquals(2, p.size)
        assertEquals(10.0, p[0].left, 0.0); assertEquals(YearMonth.of(2026, 12), p[0].expiry)
        assertEquals(30.0, p[1].left, 0.0); assertEquals(YearMonth.of(2027, 5), p[1].expiry)
    }

    @Test fun firstDateWithEmptyStockMakesOnePack() {
        val p = PackMath.afterAdd(emptyList(), 0.0, null, 30.0, YearMonth.of(2027, 5))
        assertEquals(1, p.size)
    }

    @Test fun addingWithoutDateKeepsTrackingWithOpenDate() {
        val p = PackMath.afterAdd(listOf(a), 20.0, null, 30.0, null)
        assertEquals(listOf(20.0, 30.0), p.map { it.left })
        assertNull(p[1].expiry)
        assertEquals(2, p[1].id)
    }

    @Test fun addingDropsFinishedPackAndKeepsIdsUnique() {
        val p = PackMath.afterAdd(listOf(a, b), 20.0, null, 10.0, null)
        assertEquals(listOf(2, 3), p.map { it.id })
    }

    @Test fun manualHigherStockGoesToLastPack() {
        val p = PackMath.fit(listOf(a, b), 70.0)
        assertEquals(40.0, p.last().left, 0.0)
        assertEquals(listOf(a, b), PackMath.fit(listOf(a, b), 40.0))
    }

    @Test fun editFormDateChangesPackInUse() {
        val p = PackMath.setCurrentExpiry(listOf(a, b), 25.0, YearMonth.of(2028, 1))
        assertEquals(a.expiry, p[0].expiry)
        assertEquals(YearMonth.of(2028, 1), p[1].expiry)
    }

    @Test fun warnsWhenPackInUseOutlastsNextDate() {
        val x = Pack(1, 100.0, YearMonth.of(2028, 1))
        val y = Pack(2, 30.0, YearMonth.of(2026, 12))
        assertTrue(PackMath.currentOutlastsNext(listOf(x, y), 130.0, 1.0, LocalDate.of(2026, 10, 23)))
        assertFalse(PackMath.currentOutlastsNext(listOf(x, y), 130.0, null, LocalDate.of(2026, 10, 23)))
        assertFalse(PackMath.currentOutlastsNext(listOf(x, y), 130.0, 10.0, LocalDate.of(2026, 10, 23)))
    }

    @Test fun storageRoundTrip() {
        val p = listOf(Pack(1, 12.5, YearMonth.of(2027, 3)), Pack(2, 30.0, null))
        assertEquals(p, PackMath.decode(PackMath.encode(p)))
        assertNull(PackMath.encode(emptyList()))
        assertTrue(PackMath.decode("garbage").isEmpty())
    }
}
